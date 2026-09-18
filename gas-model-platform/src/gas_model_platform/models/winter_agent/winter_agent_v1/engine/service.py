from __future__ import annotations

import json
import logging
import warnings
from typing import Any, Dict

import numpy as np
import pandas as pd

from .artifacts import ModelArtifact
from .backtest import evaluate_candidate, make_folds
from .config import DEFAULT_CONFIG
from .data_agent import clean_data, prepare_future_data
from .ensemble_agent import build_ensembles
from .feature_agent import rank_features
from .forecasting import fit_candidate, predict_candidate
from .issues import localize_issue_message
from .model_zoo import all_specs
from .optimizer import select_feature_set, tune_with_optuna
from .schemas import PredictionResult, TrainResult
from .version import MODEL_VERSION

logger = logging.getLogger(__name__)


def _records(frame: pd.DataFrame):
    if frame is None or frame.empty:
        return []
    return json.loads(frame.to_json(orient="records", date_format="iso"))


def _effective_estimator_params(model) -> Dict[str, Any]:
    """返回最终拟合器中可 JSON 序列化的有效参数。"""
    estimator = getattr(model, "estimator", None)
    if estimator is None or not hasattr(estimator, "get_params"):
        return {}
    params = {}
    for key, value in estimator.get_params(deep=True).items():
        if isinstance(value, np.generic):
            value = value.item()
        if value is None or isinstance(value, (str, int, float, bool)):
            params[key] = value
        elif isinstance(value, (list, tuple)) and all(
            item is None or isinstance(item, (str, int, float, bool))
            for item in value
        ):
            params[key] = list(value)
    return params


def train(
    train_data: pd.DataFrame,
    province: str | None = None,
    config: Dict[str, Any] | None = None,
) -> TrainResult:
    """训练、滚动回测并返回模型产物和可落库的训练结果。"""
    cfg = dict(DEFAULT_CONFIG)
    cfg.update(config or {})
    seed = int(cfg["random_seed"])

    data, clean_log, summary = clean_data(
        train_data, bool(cfg["drop_suspicious_tail"])
    )
    folds = make_folds(
        data,
        int(cfg["backtest_seasons"]),
        int(cfg["minimum_train_rows"]),
    )
    if len(folds) < 3:
        scope = f"{province} " if province else ""
        raise ValueError(f"{scope}可用冬供回测季不足3个，目前={len(folds)}")

    tuning_count = min(int(cfg["tuning_seasons"]), len(folds) - 2)
    tuning_folds = folds[:tuning_count]
    evaluation_folds = folds[tuning_count:]

    base_train = data[data.date < tuning_folds[0]["dates"][0]].copy()
    feature_ranking = rank_features(base_train, seed)
    features, _ = select_feature_set(
        data,
        tuning_folds,
        feature_ranking,
        cfg["feature_top_n_candidates"],
        seed,
    )

    model_specs = all_specs(cfg["profile"])
    requested_models = cfg.get("model_names")
    if requested_models:
        requested = set(requested_models)
        model_specs = [spec for spec in model_specs if spec.name in requested]
    if not model_specs:
        raise ValueError("没有可训练的候选模型")

    tuned_params = {}
    all_issues = []
    if cfg["optuna_enabled"]:
        for spec in model_specs:
            if spec.tunable and spec.name in cfg["tune_models"]:
                params, _, _, tuning_issues = tune_with_optuna(
                    spec,
                    data,
                    tuning_folds,
                    features,
                    cfg["optuna_trials"],
                    seed,
                )
                tuned_params[spec.name] = params
                if not tuning_issues.empty:
                    all_issues.append(tuning_issues)

    tuning_predictions = {}
    evaluation_predictions = {}
    tuning_metrics = {}
    evaluation_metrics = {}
    all_fold_metrics = []
    for spec in model_specs:
        params = tuned_params.get(spec.name, {})
        tune_pred, tune_fold, tune_metric, tune_issues = evaluate_candidate(
            spec, data, tuning_folds, features, params, seed
        )
        eval_pred, eval_fold, eval_metric, eval_issues = evaluate_candidate(
            spec, data, evaluation_folds, features, params, seed
        )
        if not tune_pred.empty:
            tuning_predictions[spec.name] = tune_pred
            tuning_metrics[spec.name] = tune_metric
        if not eval_pred.empty:
            evaluation_predictions[spec.name] = eval_pred
            evaluation_metrics[spec.name] = eval_metric
        if not tune_fold.empty:
            all_fold_metrics.append(tune_fold.assign(stage="tuning"))
        if not eval_fold.empty:
            all_fold_metrics.append(eval_fold.assign(stage="evaluation"))
        if not tune_issues.empty:
            all_issues.append(tune_issues.assign(stage="tuning_backtest"))
        if not eval_issues.empty:
            all_issues.append(eval_issues.assign(stage="evaluation_backtest"))

    individual_rows = [
        {
            "model": spec.name,
            "type": spec.kind,
            **evaluation_metrics[spec.name],
            "constituents": "",
        }
        for spec in model_specs
        if spec.name in evaluation_metrics
    ]
    ranking = pd.DataFrame(individual_rows)
    ensemble_predictions, ensemble_ranking, ensemble_weights = build_ensembles(
        tuning_predictions,
        evaluation_predictions,
        tuning_metrics,
        5,
    )
    if not ensemble_ranking.empty:
        ranking = pd.concat([ranking, ensemble_ranking], ignore_index=True)
    if ranking.empty:
        raise RuntimeError("所有候选模型训练或回测失败")
    ranking["_complexity_priority"] = (ranking["type"] == "ensemble").astype(int)
    ranking = ranking.sort_values(
        ["MAPE", "WMAPE", "_complexity_priority"]
    ).drop(columns=["_complexity_priority"]).reset_index(drop=True)
    ranking.insert(0, "rank", range(1, len(ranking) + 1))

    best_model = str(ranking.iloc[0].model)
    if best_model in evaluation_predictions:
        best_backtest = evaluation_predictions[best_model].copy()
        constituents = [best_model]
        weights = {best_model: 1.0}
    else:
        best_backtest = ensemble_predictions[best_model].copy()
        weights = ensemble_weights[best_model]
        constituents = list(weights)

    best_mape = float(ranking.iloc[0]["MAPE"])
    best_wmape = float(ranking.iloc[0]["WMAPE"])
    if len(ranking) > 1:
        second_mape = float(ranking.iloc[1]["MAPE"])
        second_wmape = float(ranking.iloc[1]["WMAPE"])
        if (
            abs(second_mape - best_mape) <= 1e-9
            and abs(second_wmape - best_wmape) <= 1e-9
        ):
            selection_reason = (
                "按独立评估回测集的 MAPE 升序、WMAPE 升序排序；"
                f"{best_model} 与第2名指标并列，"
                "同等精度下优先选择结构更简单的单模型。"
            )
        else:
            selection_reason = (
                "按独立评估回测集的 MAPE 升序、WMAPE 升序排序，"
                f"{best_model} 排名第1；MAPE={best_mape:.6f}%，"
                f"比第2名低 {second_mape - best_mape:.6f} 个百分点。"
            )
    else:
        selection_reason = (
            "当前只有一个成功候选模型，"
            f"{best_model} 的回测 MAPE={best_mape:.6f}%，"
            f"WMAPE={best_wmape:.6f}%。"
        )

    spec_by_name = {spec.name: spec for spec in model_specs}
    fitted_models = {}
    for name in constituents:
        with warnings.catch_warnings(record=True) as caught:
            warnings.simplefilter("always")
            fitted_models[name] = fit_candidate(
                spec_by_name[name],
                data,
                features,
                params=tuned_params.get(name, {}),
                seed=seed,
            )
        if caught:
            for item in caught:
                localized = localize_issue_message(
                    item.category.__name__, str(item.message)
                )
                logger.warning(
                    "model training warning stage=final_fit model=%s category=%s message=%s",
                    name,
                    item.category.__name__,
                    localized,
                )
            all_issues.append(pd.DataFrame([
                {
                    "severity": "warning",
                    "stage": "final_fit",
                    "model_name": name,
                    "season": None,
                    "category": item.category.__name__,
                    "message": localize_issue_message(
                        item.category.__name__, str(item.message)
                    ),
                }
                for item in caught
            ]))
    selected_model_params = {
        "members": {
            name: {
                "parameters": _effective_estimator_params(fitted_models[name]),
                "tuned_parameters": tuned_params.get(name, {}),
                "parameter_source": "optuna" if name in tuned_params else "default",
                "weight": weights.get(name),
            }
            for name in constituents
        }
    }

    residuals = (best_backtest.actual - best_backtest.prediction).values
    alpha = 1 - float(cfg["prediction_interval"])
    residual_lower = float(np.quantile(residuals, alpha / 2))
    residual_upper = float(np.quantile(residuals, 1 - alpha / 2))
    artifact = ModelArtifact(
        province=province,
        best_model=best_model,
        models=fitted_models,
        features=features,
        weights=weights,
        residual_lower=residual_lower,
        residual_upper=residual_upper,
        train_start=str(data.date.min().date()),
        train_end=str(data.date.max().date()),
        metadata={
            "model_version": MODEL_VERSION,
            "profile": cfg["profile"],
            "tuning_seasons": [fold["season"] for fold in tuning_folds],
            "evaluation_seasons": [fold["season"] for fold in evaluation_folds],
            "selection_reason": selection_reason,
            "selected_model_params": selected_model_params,
        },
    )

    metric_names = ["MAPE", "WMAPE", "sMAPE", "RMSE", "MAE", "R2"]
    metrics = {
        name: float(ranking.iloc[0][name])
        for name in metric_names
        if name in ranking.columns
    }
    fold_metrics = (
        pd.concat(all_fold_metrics, ignore_index=True)
        if all_fold_metrics
        else pd.DataFrame()
    )
    issues = (
        pd.concat(all_issues, ignore_index=True).drop_duplicates()
        if all_issues
        else pd.DataFrame()
    )
    issue_records = _records(issues)
    errors = [item for item in issue_records if item.get("severity") == "error"]
    return TrainResult(
        artifact=artifact,
        best_model=best_model,
        metrics=metrics,
        selection_reason=selection_reason,
        requested_candidate_count=len(model_specs),
        successful_candidate_count=len(evaluation_metrics),
        ranked_candidate_count=len(ranking),
        selected_model_params=selected_model_params,
        backtest_results=_records(best_backtest),
        model_ranking=_records(ranking),
        fold_metrics=_records(fold_metrics),
        features=features,
        summary=summary,
        clean_log=_records(clean_log),
        issues=issue_records,
        errors=errors,
    )


def predict(
    artifact: ModelArtifact,
    future_data: pd.DataFrame,
) -> PredictionResult:
    """加载训练产物后的纯预测入口，不执行调参、回测或重新训练。"""
    future = prepare_future_data(future_data)
    member_predictions = {
        name: predict_candidate(model, future)
        for name, model in artifact.models.items()
    }
    if artifact.best_model == "Ensemble_MedianTop5":
        prediction = np.median(
            np.column_stack(list(member_predictions.values())), axis=1
        )
    else:
        prediction = sum(
            member_predictions[name] * float(weight)
            for name, weight in artifact.weights.items()
        )

    rows = []
    for date, value in zip(future.date, prediction):
        rows.append(
            {
                "date": str(pd.Timestamp(date).date()),
                "forecast_value": float(value),
                "lower_value": max(0.0, float(value + artifact.residual_lower)),
                "upper_value": max(0.0, float(value + artifact.residual_upper)),
            }
        )
    return PredictionResult(model_name=artifact.best_model, results=rows)
