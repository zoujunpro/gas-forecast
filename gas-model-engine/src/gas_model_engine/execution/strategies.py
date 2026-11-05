from __future__ import annotations

from typing import Protocol

import numpy as np
import pandas as pd

from ..algorithms.contracts import AlgorithmDataset, AlgorithmModel, PredictionResult
from ..backtesting import create_default_backtest_registry
from ..combinations import create_default_combination_registry
from ..engine import AlgorithmPredictRequest, AlgorithmTrainRequest
from ..evaluation import calculate_metrics, conformal_radius
from ..features import ConfiguredFeatureProcessor, FeaturePolicyResolver
from ..profiles import AlgorithmProfile
from ..selection import CandidateScore, create_default_selector_registry
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
    ProfilePredictRequest,
    ProfileTrainRequest,
)
from .profile_executor import AlgorithmProfileExecutor


class ForecastStrategy(Protocol):
    """一个业务预测场景完整训练、回测、组合和预测流程的协议。"""

    code: str

    def execute(
        self,
        request: ForecastRequest,
        profile: AlgorithmProfile,
        executor: AlgorithmProfileExecutor,
    ) -> ForecastResult: ...


class DirectProfileStrategy:
    """显式简单策略：训练全部候选，并按 Profile 固定规则汇总。"""

    code = "DIRECT_PROFILE_V1"

    def execute(self, request, profile, executor):
        candidate_ids = tuple(candidate.id for candidate in profile.candidates)
        bundle = executor.train(
            ProfileTrainRequest(profile.code, request.train_data, candidate_ids)
        )
        prediction = executor.predict(ProfilePredictRequest(bundle, request.future_data))
        return ForecastResult(
            profile_code=profile.code,
            model_bundle=bundle,
            prediction=prediction,
            selected_candidate_ids=candidate_ids,
            stages=[
                self._stage(PipelineStage.FEATURE_PREPARATION, "使用调用方提供的标准特征数据"),
                self._stage(PipelineStage.BACKTEST, "该简单策略不执行回测", skipped=True),
                self._stage(PipelineStage.SELECTION, "该简单策略不执行模型选优", skipped=True),
                self._stage(PipelineStage.TRAINING, "全部候选已完成训练"),
                self._stage(PipelineStage.PREDICTION, "全部候选已完成预测"),
                self._stage(
                    PipelineStage.COMBINATION,
                    f"按显式配置使用 {profile.aggregation.value.lower()} 汇总",
                ),
            ],
        )

    @staticmethod
    def _stage(stage, message, skipped=False):
        status = PipelineStageStatus.SKIPPED if skipped else PipelineStageStatus.COMPLETED
        return PipelineStageResult(stage, status, message)


class ConfigDrivenForecastStrategy:
    """配置驱动的公共流程模板。

    请求和结果契约由 ForecastPipeline 统一维护；具体预测场景可以继承本类，
    通过独立策略文件覆盖执行计划或评价钩子，而不改变业务接口。
    """

    code = "CONFIG_DRIVEN_FORECAST_V1"

    def __init__(
        self,
        feature_processor=None,
        backtest_registry=None,
        combination_registry=None,
        selector_registry=None,
        feature_policy_resolver=None,
    ) -> None:
        self.feature_processor = feature_processor or ConfiguredFeatureProcessor()
        self.backtest_registry = backtest_registry or create_default_backtest_registry()
        self.combination_registry = combination_registry or create_default_combination_registry()
        self.selector_registry = selector_registry or create_default_selector_registry()
        self.feature_policy_resolver = feature_policy_resolver or FeaturePolicyResolver()

    @staticmethod
    def _slice(data: AlgorithmDataset, start: int, end: int) -> AlgorithmDataset:
        return AlgorithmDataset(
            features=data.features.iloc[start:end].reset_index(drop=True),
            dates=data.dates[start:end],
            target=None
            if data.target is None
            else data.target.iloc[start:end].reset_index(drop=True),
            sample_weight=(
                None
                if data.sample_weight is None
                else data.sample_weight.iloc[start:end].reset_index(drop=True)
            ),
            metadata=dict(data.metadata),
        )

    def execute(self, request, profile, executor):
        request = self.prepare_request(request, profile)
        if request.train_data.target is None:
            raise ValueError("配置驱动预测需要历史目标")
        feature_specs, feature_plan = self._resolve_features(request, profile)
        feature_warmup = self._feature_warmup(feature_specs)
        algorithm_minimum = max(
            executor.algorithm_engine.registry.get(candidate.algorithm_code)
            .get_capabilities()
            .minimum_training_rows
            for candidate in profile.candidates
        )
        plan_params = self.prepare_plan_params(request, profile)
        configured_floor = int(plan_params.get("minimum_train_floor", 1))
        # 算法要求的是特征生成后的有效样本数，原始数据还需要加上特征预热区间。
        plan_params["minimum_train_size"] = max(
            int(plan_params.get("minimum_train_size", 1)),
            configured_floor,
            algorithm_minimum + feature_warmup,
        )
        planner = self.backtest_registry.get(profile.backtest.code)
        plan = planner.plan(
            len(request.train_data.features),
            len(request.future_data.features),
            plan_params,
        )
        parameter_variants = {
            candidate.id: [
                self._with_oof_plan(params, plan)
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

        for fold in plan.folds:
            raw_train = self._slice(request.train_data, fold.train_start, fold.train_end)
            raw_validation = self._slice(
                request.train_data, fold.validation_start, fold.validation_end
            )
            fold_train, fold_validation = self.feature_processor.prepare(
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
                        prediction = self._predict_range(
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

        selector = self.selector_registry.get(profile.selection.code)
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
                        self.combination_registry.get(spec.code).build(
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

        prepared_train, prepared_future = self.feature_processor.prepare(
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
                raw_prediction = self._predict_range(
                    engine,
                    model,
                    request.train_data,
                    request.future_data,
                    prepared_future,
                    feature_specs,
                )
                predictions[candidate.id] = self._with_calibrated_interval(
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
                raw_prediction = self._predict_range(
                    engine,
                    model,
                    request.train_data,
                    request.future_data,
                    prepared_future,
                    feature_specs,
                )
                predictions[combination.id] = self._with_calibrated_interval(
                    raw_prediction,
                    interval_radii[combination.id],
                    float(plan_params.get("prediction_interval_coverage", 0.8)),
                )
            except Exception as exc:
                failures[combination.id] = str(exc)

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

    def prepare_plan_params(self, request, profile) -> dict:
        """返回场景回测参数；子策略可覆盖，但不得修改统一请求契约。"""
        return dict(profile.backtest.params)

    @staticmethod
    def _with_oof_plan(params, plan):
        """把外层任务窗口注入残差组合，确保内部OOF模拟相同预测跨度。"""
        normalized = dict(params)
        if "oof_params" not in normalized:
            return normalized
        oof_params = dict(normalized["oof_params"])
        oof_params["validation_size"] = plan.validation_size
        oof_params["step_size"] = plan.step_size
        normalized["oof_params"] = oof_params
        return normalized

    @staticmethod
    def _with_calibrated_interval(prediction, radius, coverage):
        """使用Eval样本外误差为任意候选附加统一口径的预测区间。"""
        metadata = dict(prediction.metadata)
        metadata.update(
            {
                "interval_method": "OOF_ABSOLUTE_ERROR_CONFORMAL",
                "interval_coverage": coverage,
                "interval_radius": radius,
            }
        )
        return PredictionResult(
            prediction.values,
            prediction.values - radius,
            prediction.values + radius,
            metadata,
        )

    def prepare_request(self, request, profile):
        """场景数据清洗扩展点；公共模板默认不修改请求。"""
        return request

    def _resolve_features(self, request, profile):
        """解析场景 Policy；未配置 Policy 的自定义 Profile 继续使用内嵌特征。"""
        if not profile.feature_policy_code:
            return profile.features, {
                "policy_code": None,
                "selected_features": [spec.code for spec in profile.features],
                "skipped_features": [],
            }
        metadata = {**request.future_data.metadata, **request.train_data.metadata}
        plan = self.feature_policy_resolver.resolve(
            profile.feature_policy_code,
            metadata,
            request.train_data.features.columns,
            request.future_data.features.columns,
        )
        return plan.specs, {
            "policy_code": plan.policy_code,
            "policy_name": plan.policy_name,
            "target_type": plan.target_type,
            "selected_features": list(plan.selected_features),
            "skipped_features": list(plan.skipped_features),
        }

    @staticmethod
    def _stage(stage, message):
        return PipelineStageResult(stage, PipelineStageStatus.COMPLETED, message)

    @staticmethod
    def _feature_warmup(specs) -> int:
        """返回目标滞后类特征所需的最大历史长度。"""
        warmup = 0
        for spec in specs:
            params = dict(spec.params)
            if spec.code == "LAG_V1":
                warmup = max(warmup, *(int(value) for value in params.get("lags", [1, 7, 14])))
            elif spec.code == "ROLLING_V1":
                warmup = max(
                    warmup,
                    *(int(value) for value in params.get("windows", [3, 7, 14])),
                )
        return warmup

    def _predict_range(
        self,
        engine,
        model: AlgorithmModel,
        raw_history: AlgorithmDataset,
        raw_future: AlgorithmDataset,
        prepared_future: AlgorithmDataset,
        feature_specs,
    ) -> PredictionResult:
        """预测一个完整区间；目标衍生特征存在时按天递归更新。"""
        if not self._uses_target_features(model):
            return engine.predict(AlgorithmPredictRequest(model, prepared_future))

        history = raw_history
        values: list[float] = []
        lowers: list[float] = []
        uppers: list[float] = []
        has_interval = True
        for index in range(len(raw_future.features)):
            raw_one = self._slice(raw_future, index, index + 1)
            _, prepared_one = self.feature_processor.prepare(history, raw_one, feature_specs)
            result = engine.predict(AlgorithmPredictRequest(model, prepared_one))
            value = float(result.values[0])
            values.append(value)
            if result.lower_bounds is None or result.upper_bounds is None:
                has_interval = False
            else:
                lowers.append(float(result.lower_bounds[0]))
                uppers.append(float(result.upper_bounds[0]))
            history = self._append_prediction(history, raw_one, value)
        return PredictionResult(
            np.asarray(values),
            np.asarray(lowers) if has_interval else None,
            np.asarray(uppers) if has_interval else None,
            {"prediction_mode": "recursive"},
        )

    @classmethod
    def _uses_target_features(cls, model: AlgorithmModel) -> bool:
        if any(name.startswith("target_") for name in model.feature_names):
            return True
        member_models = getattr(model.payload, "member_models", None)
        return bool(member_models) and any(
            cls._uses_target_features(member) for member in member_models.values()
        )

    @staticmethod
    def _append_prediction(
        history: AlgorithmDataset, future_row: AlgorithmDataset, prediction: float
    ) -> AlgorithmDataset:
        features = history.features.copy()
        appended = future_row.features.reindex(columns=features.columns)
        features = pd.concat([features, appended], ignore_index=True)
        target = pd.concat([history.target, pd.Series([prediction])], ignore_index=True)
        return AlgorithmDataset(
            features=features,
            dates=history.dates.append(future_row.dates),
            target=target,
            metadata=dict(history.metadata),
        )


class PendingLegacyStrategy:
    """旧流程尚未等价迁移时使用，阻止通用流程替代原业务逻辑。"""

    def __init__(self, code: str, description: str) -> None:
        self.code = code
        self.description = description

    def execute(self, request, profile, executor):
        raise NotImplementedError(
            f"预测策略 {self.code} 尚未完成等价迁移：{self.description}；"
            "为避免改变原模型逻辑，当前不会回退到通用组合流程"
        )


class ForecastStrategyRegistry:
    """预测流程策略注册中心。"""

    def __init__(self, strategies=()) -> None:
        self._strategies = {}
        for strategy in strategies:
            self.register(strategy)

    def register(self, strategy: ForecastStrategy) -> None:
        if strategy.code in self._strategies:
            raise ValueError(f"预测策略已注册: {strategy.code}")
        self._strategies[strategy.code] = strategy

    def get(self, code: str) -> ForecastStrategy:
        try:
            return self._strategies[code]
        except KeyError as exc:
            raise KeyError(f"预测策略未注册: {code}") from exc


def create_default_strategy_registry() -> ForecastStrategyRegistry:
    """注册已实现策略和需要后续等价迁移的旧模型策略。"""
    from .scenario_strategies import (
        LegacyShortTermResidualStrategy,
        MonthlyIndustryStrategy,
        ShortTermCustomerStrategy,
        WinterSupplyStrategy,
    )

    return ForecastStrategyRegistry(
        [
            DirectProfileStrategy(),
            ConfigDrivenForecastStrategy(),
            ShortTermCustomerStrategy(),
            WinterSupplyStrategy(),
            LegacyShortTermResidualStrategy(),
            MonthlyIndustryStrategy(),
        ]
    )
