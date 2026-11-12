from __future__ import annotations

import numpy as np

from ..algorithms.contracts import PredictionResult
from ..engine import AlgorithmTrainRequest
from ..evaluation import calculate_metrics, conformal_radius
from ..profiles import AlgorithmProfileRegistry
from ..selection import CandidateScore
from ..tuning import expand_parameter_grid
from .contracts import (
    BacktestResult,
    ForecastRequest,
    ForecastResult,
    PipelineStage,
    PipelineStageResult,
    PipelineStageStatus,
    ProfileModelBundle,
    ProfilePredictionResult,
)
from .profile_executor import AlgorithmProfileExecutor
from .scenario import (
    ForecastScenario,
    ForecastScenarioRegistry,
)


class ForecastPipeline:
    """模型引擎面向上层业务的统一预测入口。

    根据 Profile 分派确定性策略，组织特征、回测、候选训练、合法组合、
    统一排名和最终预测；具体算法通过内部 ``AlgorithmEngine`` 执行。
    """

    def __init__(
        self,
        # 执行候选算法的训练与预测。
        profile_executor: AlgorithmProfileExecutor,
        # 根据 profile_code 查找预测方案配置。
        profile_registry: AlgorithmProfileRegistry,
        # 根据 scenario_code 查找场景的数据准备和参数钩子。
        scenario_registry: ForecastScenarioRegistry,
    ) -> None:
        self.profile_executor = profile_executor
        self.profile_registry = profile_registry
        self.scenario_registry = scenario_registry

    def run(self, request: ForecastRequest) -> ForecastResult:
        """执行唯一的生产预测流程。

        ``profile_code`` 决定候选算法、特征和回测参数；``scenario_code`` 只查找
        当前场景的数据清洗与参数钩子，不会切换到另一套预测流程。
        """
        profile = self.profile_registry.get(request.profile_code)
        scenario = self.scenario_registry.get(profile.scenario_code)
        return self._run_forecast(request, profile, scenario)

    def _run_forecast(self, request, profile, scenario: ForecastScenario):
        """执行可直接用于生产的完整预测流程。

        该流程负责数据清洗与校验、特征解析、Walk-forward 回测、参数 Tune、
        独立 Eval、候选组合与排名、全量历史重新训练、未来预测和预测区间校准。
        ``scenario`` 只提供当前业务场景的差异配置和扩展钩子，流程顺序统一由
        ForecastPipeline 控制。
        """
        executor = self.profile_executor

        # 1. 清洗和校验场景数据，解析本次任务使用的特征。
        request = scenario.prepare_request(request, profile)
        if request.train_data.target is None:
            raise ValueError("配置驱动预测需要历史目标")
        feature_specs, feature_plan = scenario._resolve_features(request, profile)
        feature_warmup = scenario._feature_warmup(feature_specs)
        algorithm_minimum = max(
            executor.algorithm_engine.registry.get(candidate.algorithm_code)
            .get_capabilities()
            .minimum_training_rows
            for candidate in profile.candidates
        )
        # 2. 根据预测跨度、特征预热长度和算法要求生成 Walk-forward 计划。
        plan_params = scenario.prepare_plan_params(request, profile)
        configured_floor = int(plan_params.get("minimum_train_floor", 1))
        # 算法要求的是特征生成后的有效样本数，原始数据还需要加上特征预热区间。
        plan_params["minimum_train_size"] = max(
            int(plan_params.get("minimum_train_size", 1)),
            configured_floor,
            algorithm_minimum + feature_warmup,
        )
        planner = scenario.backtest_registry.get(profile.backtest.code)
        plan = planner.plan(
            len(request.train_data.features),
            len(request.future_data.features),
            plan_params,
        )
        parameter_variants = {
            candidate.id: [
                scenario._with_oof_plan(params, plan)
                for params in expand_parameter_grid(candidate.params, candidate.search_space)
            ]
            for candidate in profile.candidates
        }
        oof_variants = {
            candidate.id: [
                np.full(len(request.train_data.features), np.nan)
                for _ in parameter_variants[candidate.id]
            ]
            for candidate in profile.candidates
        }
        variant_failures: dict[tuple[str, int], str] = {}
        failures: dict[str, str] = {}
        engine = executor.algorithm_engine

        # 3. 在每个回测窗口训练所有参数方案，生成样本外 OOF 预测。
        for fold in plan.folds:
            raw_train = scenario._slice(request.train_data, fold.train_start, fold.train_end)
            raw_validation = scenario._slice(
                request.train_data, fold.validation_start, fold.validation_end
            )
            fold_train, fold_validation = scenario.feature_processor.prepare(
                raw_train, raw_validation, feature_specs
            )
            for candidate in profile.candidates:
                for variant_index, variant_params in enumerate(parameter_variants[candidate.id]):
                    variant_key = (candidate.id, variant_index)
                    if variant_key in variant_failures:
                        continue
                    try:
                        model = engine.train(
                            AlgorithmTrainRequest(
                                candidate.algorithm_code, fold_train, variant_params
                            )
                        )
                        prediction = scenario._predict_range(
                            engine,
                            model,
                            raw_train,
                            raw_validation,
                            fold_validation,
                            feature_specs,
                        ).values
                        oof_variants[candidate.id][variant_index][
                            fold.validation_start : fold.validation_end
                        ] = prediction
                    except Exception as exc:
                        variant_failures[variant_key] = str(exc)

        # 4. 将前段回测折用于 Tune，末段回测折用于独立 Eval。
        actual = request.train_data.target.to_numpy(dtype=float)
        metrics: dict[str, dict[str, float]] = {}
        scores: list[CandidateScore] = []
        successful_oof: dict[str, np.ndarray] = {}
        interval_radii: dict[str, float] = {}
        selected_params: dict[str, dict] = {}
        tuning_metadata: dict[str, dict] = {}
        member_specs: dict[str, dict] = {}
        tune_mask = np.zeros(len(actual), dtype=bool)
        evaluation_mask = np.zeros(len(actual), dtype=bool)
        if "eval_folds" in plan_params:
            # 兼容仍显式指定折数的自定义Profile。
            requested_eval_folds = int(plan_params["eval_folds"])
            eval_fold_count = min(max(requested_eval_folds, 1), len(plan.folds) - 1)
        else:
            minimum_tune_folds = int(plan_params.get("minimum_tune_folds", 1))
            minimum_eval_folds = int(plan_params.get("minimum_eval_folds", 1))
            eval_ratio = float(plan_params.get("eval_ratio", 0.4))
            if not 0 < eval_ratio < 1:
                raise ValueError("eval_ratio必须在0和1之间")
            if len(plan.folds) < minimum_tune_folds + minimum_eval_folds:
                raise ValueError(
                    "回测折数不足以划分Tune和Eval: "
                    f"folds={len(plan.folds)}, tune_min={minimum_tune_folds}, "
                    f"eval_min={minimum_eval_folds}"
                )
            eval_fold_count = max(
                minimum_eval_folds,
                int(round(len(plan.folds) * eval_ratio)),
            )
            eval_fold_count = min(
                eval_fold_count,
                len(plan.folds) - minimum_tune_folds,
            )
        tune_folds = plan.folds[:-eval_fold_count]
        evaluation_folds = plan.folds[-eval_fold_count:]
        for fold in tune_folds:
            tune_mask[fold.validation_start : fold.validation_end] = True
        for fold in evaluation_folds:
            evaluation_mask[fold.validation_start : fold.validation_end] = True
        for candidate in profile.candidates:
            variant_scores = []
            for variant_index, values in enumerate(oof_variants[candidate.id]):
                mask = np.isfinite(values)
                variant_tune_mask = mask & tune_mask
                variant_eval_mask = mask & evaluation_mask
                if not variant_tune_mask.any() or not variant_eval_mask.any():
                    continue
                tune_metrics = calculate_metrics(
                    actual[variant_tune_mask], values[variant_tune_mask]
                )
                variant_scores.append((variant_index, tune_metrics, values, variant_eval_mask))
            if not variant_scores:
                messages = [
                    message
                    for (candidate_id, _), message in variant_failures.items()
                    if candidate_id == candidate.id
                ]
                failures[candidate.id] = "; ".join(messages) or "没有参数方案完成Tune和Eval"
                if candidate.required:
                    raise RuntimeError(
                        f"必需候选算法回测失败: {candidate.id}: {failures[candidate.id]}"
                    )
                continue
            metric_name = str(profile.selection.params.get("metric", "mape")).lower()
            best_index, best_tune_metrics, values, candidate_evaluation_mask = min(
                variant_scores,
                key=lambda item: (float(item[1][metric_name]), item[0]),
            )
            candidate_tune_mask = np.isfinite(values) & tune_mask
            candidate_metrics = calculate_metrics(
                actual[candidate_evaluation_mask], values[candidate_evaluation_mask]
            )
            metrics[candidate.id] = candidate_metrics
            scores.append(CandidateScore(candidate.id, "ALGORITHM", candidate_metrics))
            successful_oof[candidate.id] = values
            interval_radii[candidate.id] = conformal_radius(
                actual[candidate_evaluation_mask],
                values[candidate_evaluation_mask],
                float(plan_params.get("prediction_interval_coverage", 0.8)),
            )
            selected_params[candidate.id] = parameter_variants[candidate.id][best_index]
            tuning_metadata[candidate.id] = {
                "variant_count": len(parameter_variants[candidate.id]),
                "successful_variant_count": len(variant_scores),
                "selected_variant": best_index,
                "selected_params": selected_params[candidate.id],
                "tune_metrics": best_tune_metrics,
                "eval_metrics": candidate_metrics,
            }
            member_specs[candidate.id] = {
                "id": candidate.id,
                "code": candidate.algorithm_code,
                "params": dict(selected_params[candidate.id]),
            }
        if not scores:
            details = "; ".join(f"{key}: {value}" for key, value in failures.items())
            raise RuntimeError(f"没有候选算法完成回测: {details}")

        # 5. 统一评价原子候选，并按 Profile 配置生成和评价结果组合。
        selector = scenario.selector_registry.get(profile.selection.code)
        tune_scores = [
            CandidateScore(
                item.id,
                "ALGORITHM",
                calculate_metrics(actual[tune_mask], successful_oof[item.id][tune_mask]),
            )
            for item in scores
        ]
        base_ranking = selector.rank(tune_scores, dict(profile.selection.params))
        ranked_ids = [item.id for item in base_ranking]
        combination_candidates = []
        if profile.combinations:
            common_mask = np.logical_and.reduce(
                [np.isfinite(successful_oof[item]) for item in ranked_ids]
            )
            common_tune = common_mask & tune_mask
            common_evaluation = common_mask & evaluation_mask
            if common_tune.any() and common_evaluation.any():
                tune_oof = {key: values[common_tune] for key, values in successful_oof.items()}
                evaluation_oof = {
                    key: values[common_evaluation] for key, values in successful_oof.items()
                }
                for spec in profile.combinations:
                    combination_candidates.extend(
                        scenario.combination_registry.get(spec.code).build(
                            ranked_ids,
                            tune_oof,
                            actual[common_tune],
                            evaluation_oof,
                            actual[common_evaluation],
                            member_specs,
                            dict(spec.params),
                        )
                    )
        for combination in combination_candidates:
            metrics[combination.id] = combination.metrics
            scores.append(
                CandidateScore(
                    combination.id,
                    "COMBINATION",
                    combination.metrics,
                    combination.member_ids,
                )
            )
            interval_radii[combination.id] = conformal_radius(
                actual[common_evaluation],
                combination.predictions,
                float(plan_params.get("prediction_interval_coverage", 0.8)),
            )
        ranking = selector.rank(scores, dict(profile.selection.params))

        # 6. 使用全量历史重新训练回测成功的候选，并预测真正的未来区间。
        prepared_train, prepared_future = scenario.feature_processor.prepare(
            request.train_data, request.future_data, feature_specs
        )
        models = {}
        predictions: dict[str, PredictionResult] = {}
        for candidate in profile.candidates:
            if candidate.id not in successful_oof:
                continue
            try:
                model = engine.train(
                    AlgorithmTrainRequest(
                        candidate.algorithm_code, prepared_train, selected_params[candidate.id]
                    )
                )
                models[candidate.id] = model
                raw_prediction = scenario._predict_range(
                    engine,
                    model,
                    request.train_data,
                    request.future_data,
                    prepared_future,
                    feature_specs,
                )
                predictions[candidate.id] = scenario._with_calibrated_interval(
                    raw_prediction,
                    interval_radii[candidate.id],
                    float(plan_params.get("prediction_interval_coverage", 0.8)),
                )
            except Exception as exc:
                failures[candidate.id] = str(exc)

        for combination in combination_candidates:
            try:
                model = engine.train(
                    AlgorithmTrainRequest(
                        combination.algorithm_code,
                        prepared_train,
                        combination.algorithm_params,
                    )
                )
                models[combination.id] = model
                raw_prediction = scenario._predict_range(
                    engine,
                    model,
                    request.train_data,
                    request.future_data,
                    prepared_future,
                    feature_specs,
                )
                predictions[combination.id] = scenario._with_calibrated_interval(
                    raw_prediction,
                    interval_radii[combination.id],
                    float(plan_params.get("prediction_interval_coverage", 0.8)),
                )
            except Exception as exc:
                failures[combination.id] = str(exc)

        # 7. 从成功产生未来预测的候选中确定最终结果并返回完整过程信息。
        winner = next((item for item in ranking if item.id in predictions), None)
        if winner is None:
            raise RuntimeError("完成回测的候选均未能在全量数据上训练并预测")
        bundle = ProfileModelBundle(profile.code, profile.version, models, failures)
        prediction = ProfilePredictionResult(
            profile_code=profile.code,
            predictions=predictions,
            final_prediction=predictions[winner.id],
            failed_candidates=failures,
        )
        backtest = BacktestResult(
            candidate_metrics=metrics,
            member_oof_predictions=successful_oof,
            metadata={
                "method_code": plan.method_code,
                "fold_count": len(plan.folds),
                "available_fold_count": plan.available_fold_count,
                "minimum_train_size": plan.minimum_train_size,
                "initial_train_size": plan.initial_train_size,
                "validation_size": plan.validation_size,
                "step_size": plan.step_size,
                "feature_warmup_size": feature_warmup,
                "algorithm_minimum_training_rows": algorithm_minimum,
                "feature_plan": feature_plan,
                "ranking": [item.id for item in ranking],
                "selected": winner.id,
                "combination_tune_fold_count": len(tune_folds),
                "tune_folds": [fold.number for fold in tune_folds],
                "selection_eval_folds": [fold.number for fold in evaluation_folds],
                "eval_ratio": len(evaluation_folds) / len(plan.folds),
                "tuning": tuning_metadata,
                "prediction_interval_coverage": float(
                    plan_params.get("prediction_interval_coverage", 0.8)
                ),
                "prediction_interval_radii": interval_radii,
            },
        )
        return ForecastResult(
            profile_code=profile.code,
            model_bundle=bundle,
            prediction=prediction,
            selected_candidate_ids=(winner.id,),
            stages=[
                self._stage(PipelineStage.FEATURE_PREPARATION, "已按编码动态加载特征方法"),
                self._stage(
                    PipelineStage.BACKTEST,
                    f"已完成{len(plan.folds)}折回测，窗口{plan.validation_size}，步长{plan.step_size}",
                ),
                self._stage(
                    PipelineStage.COMBINATION, f"已生成{len(combination_candidates)}个合法组合"
                ),
                self._stage(PipelineStage.SELECTION, f"已选择最优候选 {winner.id}"),
                self._stage(PipelineStage.TRAINING, "已使用全量历史数据训练成功候选"),
                self._stage(PipelineStage.PREDICTION, "已返回各候选和最优候选预测"),
            ],
            backtest=backtest,
        )

    @staticmethod
    def _stage(stage, message, skipped=False):
        status = PipelineStageStatus.SKIPPED if skipped else PipelineStageStatus.COMPLETED
        return PipelineStageResult(stage, status, message)
