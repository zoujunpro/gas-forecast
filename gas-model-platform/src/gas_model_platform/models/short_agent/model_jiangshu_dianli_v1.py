"""短期智能体下的江苏电力日级预测模型 V1。

迁移自 ``demo_jiangsu_power_agent.py``，保留 Prophet 基线、LightGBM
残差学习和 Walk-Forward 验证，并在同一文件中提供统一模型 Handler。
"""

from __future__ import annotations

from dataclasses import dataclass, field
from hashlib import sha256
import logging
from pathlib import Path
import re
from typing import Any

import joblib
import numpy as np
import pandas as pd
from lightgbm import LGBMRegressor

from gas_model_platform.core.config import settings
from gas_model_platform.schemas.modeling import (
    BacktestPoint,
    BacktestResult,
    CandidateEvaluation,
    ForecastPoint,
    MetricSet,
    ModelContext,
    ModelInfo,
    PredictResult,
    RollingBacktestFoldMetric,
    RollingBacktestPoint,
    TrainingDataRange,
    TrainingDataValidationIssue,
    TrainingDataValidationResult,
    TrainResult,
)


logger = logging.getLogger(__name__)


ALIASES = {
    "ds": ["ds", "date", "日期", "时间", "stat_date", "statDate"],
    "y": [
        "y",
        "gas_sales",
        "power",
        "power_generation",
        "generation",
        "发电",
        "发电量",
    ],
}

DEFAULT_CANDIDATES = [
    {"cps": 0.05, "max_depth": 3, "num_leaves": 16, "reg_alpha": 0.5, "reg_lambda": 3.0, "lr": 0.03},
    {"cps": 0.10, "max_depth": 4, "num_leaves": 31, "reg_alpha": 1.0, "reg_lambda": 5.0, "lr": 0.02},
    {"cps": 0.03, "max_depth": 2, "num_leaves": 8, "reg_alpha": 0.0, "reg_lambda": 1.0, "lr": 0.05},
    {"cps": 0.15, "max_depth": 3, "num_leaves": 16, "reg_alpha": 1.5, "reg_lambda": 4.0, "lr": 0.04},
]

LAGS = {"lag1": 1, "lag2": 2, "lag3": 3, "lag7": 7, "lag14": 14, "lag30": 30}
ROLLS = {
    "roll_mean_7": 7,
    "roll_mean_14": 14,
    "roll_mean_30": 30,
    "roll_std_7": 7,
    "roll_std_14": 14,
}


@dataclass
class JiangshuDianliV1Artifact:
    prophet_model: Any
    residual_model: Any
    feature_names: list[str]
    selected_params: dict[str, Any]
    history_dates: list[str]
    history_values: list[float]
    climatology_mean: dict[int, float]
    climatology_std: dict[int, float]
    climatology_fallback_mean: float
    climatology_fallback_std: float
    start_date: str
    train_start: str
    train_end: str
    residual_lower: float
    residual_upper: float
    external_feature_names: list[str] = field(default_factory=list)
    metadata: dict[str, Any] = field(default_factory=dict)
    model_version: str = "1.0.0"
    artifact_version: str = "1.0"


@dataclass
class JiangshuDianliV1TrainingOutput:
    artifact: JiangshuDianliV1Artifact
    metrics: dict[str, float]
    backtest_rows: list[dict[str, Any]]
    fold_metrics: list[dict[str, Any]]
    candidate_evaluations: list[dict[str, Any]]
    selected_params: dict[str, Any]
    baseline_metrics: dict[str, float]
    evolution_history: list[dict[str, Any]]
    security_checks: dict[str, bool]
    n_folds: int
    walk_forward_rmse: float


def _prophet_class():
    try:
        from prophet import Prophet
    except ImportError as exc:
        raise RuntimeError(
            "MODEL_JIANGSHU_DIANLI_V1.0 需要安装 prophet 依赖，请重新安装项目依赖"
        ) from exc
    return Prophet


def _rename_columns(frame: pd.DataFrame) -> pd.DataFrame:
    normalized = {str(column).strip().lower(): column for column in frame.columns}
    rename: dict[Any, str] = {}
    for standard, aliases in ALIASES.items():
        for alias in aliases:
            original = normalized.get(str(alias).strip().lower())
            if original is not None:
                rename[original] = standard
                break
    return frame.rename(columns=rename)


def prepare_training_data(data: pd.DataFrame) -> pd.DataFrame:
    frame = _rename_columns(data.copy())
    missing = [column for column in ("ds", "y") if column not in frame.columns]
    if missing:
        raise ValueError(f"短期模型训练数据缺少必要字段: {missing}")
    frame["ds"] = pd.to_datetime(frame["ds"], errors="coerce")
    frame["y"] = pd.to_numeric(frame["y"], errors="coerce")
    # 与原脚本 load_jiangsu_power_data 保持一致：保留 Excel 中除日期和
    # 目标值之外的全部业务特征，仅清除日期或目标值无效的行。
    frame = frame.dropna(subset=["ds", "y"])
    return frame.reset_index(drop=True)


def prepare_future_data(
    data: pd.DataFrame,
    required_external_features: list[str] | None = None,
) -> pd.DataFrame:
    frame = _rename_columns(data.copy())
    if "ds" not in frame.columns:
        raise ValueError("短期模型预测数据缺少必要字段 date")
    frame["ds"] = pd.to_datetime(frame["ds"], errors="coerce")
    if frame["ds"].isna().any():
        raise ValueError("短期模型预测数据中存在无效日期")
    if frame["ds"].duplicated().any():
        raise ValueError("短期模型预测日期不能重复")
    required = list(required_external_features or [])
    missing = [column for column in required if column not in frame.columns]
    if missing:
        raise ValueError(f"短期模型预测数据缺少未来外部特征: {missing}")
    for column in required:
        frame[column] = pd.to_numeric(frame[column], errors="coerce")
    invalid = [column for column in required if frame[column].isna().any()]
    if invalid:
        raise ValueError(f"短期模型预测数据的未来外部特征存在空值或非数值: {invalid}")
    columns = ["ds", *required]
    return frame[columns].sort_values("ds").reset_index(drop=True)


def _calendar_features(frame: pd.DataFrame, start_date: pd.Timestamp) -> pd.DataFrame:
    result = frame.copy()
    result["day_of_week"] = result["ds"].dt.dayofweek.astype(int)
    result["month"] = result["ds"].dt.month.astype(int)
    result["day_of_year"] = result["ds"].dt.dayofyear.astype(int)
    result["is_weekend"] = (result["day_of_week"] >= 5).astype(int)
    result["quarter"] = result["ds"].dt.quarter.astype(int)
    result["t"] = (result["ds"] - start_date).dt.days.astype(int)
    for harmonic in range(1, 5):
        angle = 2 * np.pi * harmonic * result["t"].astype(float) / 7.0
        result[f"weekly_sin_{harmonic}"] = np.sin(angle)
        result[f"weekly_cos_{harmonic}"] = np.cos(angle)
    for harmonic in range(1, 6):
        angle = 2 * np.pi * harmonic * result["t"].astype(float) / 365.25
        result[f"yearly_sin_{harmonic}"] = np.sin(angle)
        result[f"yearly_cos_{harmonic}"] = np.cos(angle)
    return result


def _climatology(data: pd.DataFrame) -> tuple[dict[int, float], dict[int, float], float, float]:
    working = data.assign(doy=data["ds"].dt.dayofyear.astype(int))
    means = working.groupby("doy")["y"].mean().to_dict()
    stds = working.groupby("doy")["y"].std().to_dict()
    fallback_mean = float(working["y"].mean())
    fallback_std = float(working["y"].std())
    if not np.isfinite(fallback_std) or fallback_std <= 0:
        fallback_std = 1.0
    return means, stds, fallback_mean, fallback_std


def _add_climatology(
    frame: pd.DataFrame,
    means: dict[int, float],
    stds: dict[int, float],
    fallback_mean: float,
    fallback_std: float,
) -> pd.DataFrame:
    result = frame.copy()
    result["doy"] = result["ds"].dt.dayofyear.astype(int)
    # 原脚本按当前数据行顺序执行 ffill/bfill；末尾 fallback 只覆盖映射
    # 全为空的极端输入，不改变正常训练数据上的原始结果。
    result["clim_mean"] = (
        result["doy"].map(means).ffill().bfill().fillna(fallback_mean)
    )
    result["clim_std"] = (
        result["doy"].map(stds).ffill().bfill().fillna(fallback_std).clip(lower=1e-6)
    )
    return result


def _add_history_features(frame: pd.DataFrame) -> pd.DataFrame:
    result = frame.copy()
    for name, lag in LAGS.items():
        result[name] = result["y"].shift(lag)
    for name, window in ROLLS.items():
        shifted = result["y"].shift(1).rolling(window=window, min_periods=1)
        result[name] = shifted.std() if "std" in name else shifted.mean()
    result["diff1"] = (result["lag1"] - result["lag2"]).fillna(0.0)
    result["diff7"] = (result["lag1"] - result["lag7"]).fillna(0.0)
    return result


def _fit_prophet(data: pd.DataFrame, cps: float):
    model = _prophet_class()(
        yearly_seasonality="auto",
        weekly_seasonality="auto",
        daily_seasonality=False,
        seasonality_mode="additive",
        changepoint_prior_scale=cps,
    )
    model.fit(data[["ds", "y"]])
    return model


def _prophet_components(model: Any, dates: pd.Series) -> pd.DataFrame:
    forecast = model.predict(pd.DataFrame({"ds": pd.to_datetime(dates)}))
    for column in ("trend", "weekly", "yearly", "additive_terms"):
        if column not in forecast.columns:
            forecast[column] = 0.0
    return forecast[["ds", "yhat", "trend", "weekly", "yearly", "additive_terms"]].copy()


def _merge_prophet_features(
    features: pd.DataFrame,
    components: pd.DataFrame,
) -> pd.DataFrame:
    result = features.merge(components, on="ds", how="left")
    return result.rename(
        columns={
            "yhat": "prophet_yhat",
            "trend": "prophet_trend",
            "weekly": "prophet_weekly",
            "yearly": "prophet_yearly",
            "additive_terms": "prophet_add",
        }
    )


def _new_residual_model(params: dict[str, Any]) -> LGBMRegressor:
    return LGBMRegressor(
        n_estimators=2000,
        learning_rate=float(params["lr"]),
        max_depth=int(params["max_depth"]),
        num_leaves=int(params["num_leaves"]),
        reg_alpha=float(params["reg_alpha"]),
        reg_lambda=float(params["reg_lambda"]),
        min_child_samples=30,
        subsample=0.85,
        colsample_bytree=0.8,
        random_state=42,
        n_jobs=-1,
        verbose=-1,
    )


def _history_values(values: list[float], lag: int) -> float:
    return float(values[-lag])


def predict_with_artifact(
    artifact: JiangshuDianliV1Artifact,
    future_data: pd.DataFrame,
) -> list[dict[str, Any]]:
    external_feature_names = list(
        getattr(artifact, "external_feature_names", []) or []
    )
    future = prepare_future_data(future_data, external_feature_names)
    train_end = pd.Timestamp(artifact.train_end)
    if (future["ds"] <= train_end).any():
        raise ValueError("短期模型预测日期必须晚于模型训练结束日期")
    expected = pd.date_range(train_end + pd.Timedelta(days=1), periods=len(future), freq="D")
    actual_dates = future["ds"].dt.date.tolist()
    expected_dates = list(expected.date)
    if actual_dates != expected_dates:
        raise ValueError("短期模型预测日期必须从训练结束日期次日起按天连续")

    history = list(artifact.history_values)
    results: list[dict[str, Any]] = []
    components = _prophet_components(artifact.prophet_model, future["ds"])
    for index, date_value in enumerate(future["ds"]):
        row = _calendar_features(
            future.iloc[[index]].reset_index(drop=True),
            pd.Timestamp(artifact.start_date),
        )
        row = _add_climatology(
            row,
            artifact.climatology_mean,
            artifact.climatology_std,
            artifact.climatology_fallback_mean,
            artifact.climatology_fallback_std,
        )
        for name, lag in LAGS.items():
            row[name] = _history_values(history, lag)
        for name, window in ROLLS.items():
            values = np.asarray(history[-window:], dtype=float)
            row[name] = float(np.std(values, ddof=1)) if "std" in name else float(np.mean(values))
        row["diff1"] = row["lag1"] - row["lag2"]
        row["diff7"] = row["lag1"] - row["lag7"]
        component = components.iloc[index]
        row["prophet_yhat"] = float(component["yhat"])
        row["prophet_trend"] = float(component["trend"])
        row["prophet_weekly"] = float(component["weekly"])
        row["prophet_yearly"] = float(component["yearly"])
        row["prophet_add"] = float(component["additive_terms"])
        residual = float(artifact.residual_model.predict(row[artifact.feature_names])[0])
        prediction = float(component["yhat"] + residual)
        history.append(prediction)
        results.append(
            {
                "date": str(pd.Timestamp(date_value).date()),
                "prediction": prediction,
                "lower": prediction + artifact.residual_lower,
                "upper": prediction + artifact.residual_upper,
            }
        )
    return results


def calculate_metrics(actual: np.ndarray, predicted: np.ndarray) -> dict[str, float]:
    actual = np.asarray(actual, dtype=float)
    predicted = np.asarray(predicted, dtype=float)
    error = predicted - actual
    denominator = np.maximum(np.abs(actual), 1e-8)
    mae = float(np.mean(np.abs(error)))
    rmse = float(np.sqrt(np.mean(error ** 2)))
    mape = float(np.mean(np.abs(error) / denominator) * 100)
    wmape = float(np.sum(np.abs(error)) / max(np.sum(np.abs(actual)), 1e-8) * 100)
    smape = float(np.mean(2 * np.abs(error) / np.maximum(np.abs(actual) + np.abs(predicted), 1e-8)) * 100)
    total = float(np.sum((actual - actual.mean()) ** 2))
    r2 = float(1 - np.sum(error ** 2) / total) if total > 0 else float("nan")
    return {"mape": mape, "wmape": wmape, "smape": smape, "rmse": rmse, "mae": mae, "r2": r2}


def _original_walk_forward_splits(
    features: pd.DataFrame,
) -> list[tuple[slice, slice]]:
    """保持原脚本固定的 730/120/90 Walk-Forward 划分。"""
    splits: list[tuple[slice, slice]] = []
    start = 365 * 2
    while start + 120 <= len(features):
        splits.append((slice(0, start), slice(start, start + 120)))
        start += 90
    if not splits:
        raise ValueError(
            "MODEL_JIANGSHU_DIANLI_V1.0 至少需要 880 条连续日级数据，"
            "才能执行原始 730/120/90 Walk-Forward 逻辑"
        )
    return splits


def _features_with_climatology(
    features: pd.DataFrame,
    climatology_source: pd.DataFrame,
) -> tuple[pd.DataFrame, dict[int, float], dict[int, float], float, float]:
    means, stds, fallback_mean, fallback_std = _climatology(climatology_source)
    return (
        _add_climatology(
            features,
            means,
            stds,
            fallback_mean,
            fallback_std,
        ),
        means,
        stds,
        fallback_mean,
        fallback_std,
    )


def _fit_and_predict_original_split(
    train: pd.DataFrame,
    validation: pd.DataFrame,
    params: dict[str, Any],
) -> tuple[np.ndarray, dict[str, float]]:
    train_with_climate, means, stds, fallback_mean, fallback_std = (
        _features_with_climatology(train, train)
    )
    validation_with_climate = _add_climatology(
        validation,
        means,
        stds,
        fallback_mean,
        fallback_std,
    )
    prophet_model = _fit_prophet(train_with_climate[["ds", "y"]], params["cps"])
    prophet_train = _prophet_components(prophet_model, train_with_climate["ds"])
    prophet_validation = _prophet_components(
        prophet_model, validation_with_climate["ds"]
    )
    train_features = _merge_prophet_features(train_with_climate, prophet_train)
    validation_features = _merge_prophet_features(
        validation_with_climate, prophet_validation
    )
    feature_names = [
        column
        for column in train_features.columns
        if column not in {"ds", "y", "doy"}
    ]
    residual_target = (
        train_with_climate["y"].astype(float).reset_index(drop=True)
        - prophet_train["yhat"].astype(float).reset_index(drop=True)
    )
    residual_model = _new_residual_model(params)
    residual_model.fit(train_features[feature_names], residual_target, eval_metric="rmse")
    predicted = (
        prophet_validation["yhat"].astype(float).to_numpy()
        + residual_model.predict(validation_features[feature_names])
    )
    return predicted, calculate_metrics(
        validation_with_climate["y"].to_numpy(dtype=float), predicted
    )


def _run_original_baseline(clean: pd.DataFrame) -> dict[str, float]:
    """原脚本的单次划分基础模型，仅用于对比。"""
    cutoff = clean["ds"].max() - pd.Timedelta(days=119)
    train = clean[clean["ds"] < cutoff].copy()
    start_date = clean["ds"].min()
    means, stds, fallback_mean, fallback_std = _climatology(train)
    features = _calendar_features(clean, start_date)
    features = _add_climatology(
        features, means, stds, fallback_mean, fallback_std
    )
    features = _add_history_features(features).dropna().reset_index(drop=True)
    train_features = features[features["ds"] < cutoff].copy().reset_index(drop=True)
    test_features = features[features["ds"] >= cutoff].copy().reset_index(drop=True)
    params = dict(DEFAULT_CANDIDATES[0])
    prophet_model = _fit_prophet(train_features[["ds", "y"]], params["cps"])
    prophet_train = _prophet_components(prophet_model, train_features["ds"])
    prophet_test = _prophet_components(prophet_model, test_features["ds"])
    train_matrix = _merge_prophet_features(train_features, prophet_train)
    test_matrix = _merge_prophet_features(test_features, prophet_test)
    feature_names = [
        column for column in train_matrix.columns if column not in {"ds", "y", "doy"}
    ]
    residual_target = (
        train_features["y"].astype(float).reset_index(drop=True)
        - prophet_train["yhat"].astype(float).reset_index(drop=True)
    )
    residual_model = _new_residual_model(params)
    residual_model.fit(train_matrix[feature_names], residual_target)
    predicted = (
        prophet_test["yhat"].astype(float).to_numpy()
        + residual_model.predict(test_matrix[feature_names])
    )
    return calculate_metrics(test_features["y"].to_numpy(dtype=float), predicted)


def train_model(data: pd.DataFrame) -> JiangshuDianliV1TrainingOutput:
    """原样执行演示脚本的训练、进化搜索和最终测试逻辑。"""
    clean = prepare_training_data(data)
    external_feature_names = [
        column for column in clean.columns if column not in {"ds", "y"}
    ]
    baseline_metrics = _run_original_baseline(clean)
    start_date = clean["ds"].min()
    full_features = _calendar_features(clean, start_date)
    full_features = _add_history_features(full_features).dropna().reset_index(drop=True)
    splits = _original_walk_forward_splits(full_features)

    population = [dict(candidate) for candidate in DEFAULT_CANDIDATES]
    evolution_history: list[dict[str, Any]] = []
    candidate_evaluations: list[dict[str, Any]] = []
    best_params: dict[str, Any] | None = None
    best_score = float("inf")
    best_fold_metrics: list[dict[str, Any]] = []
    total_candidates = 4 * len(population)
    completed_candidates = 0

    logger.info(
        "江苏电力模型开始训练 rows=%d folds=%d generations=4 candidates_per_generation=%d",
        len(clean),
        len(splits),
        len(population),
    )

    for generation in range(4):
        logger.info("江苏电力模型参数搜索 generation=%d/4 开始", generation + 1)
        generation_scores: list[float] = []
        for candidate_index, params in enumerate(population):
            fold_rmses: list[float] = []
            current_fold_metrics: list[dict[str, Any]] = []
            for fold_index, (train_slice, validation_slice) in enumerate(
                splits, start=1
            ):
                train = full_features.iloc[train_slice].copy().reset_index(drop=True)
                validation = (
                    full_features.iloc[validation_slice].copy().reset_index(drop=True)
                )
                try:
                    _, metrics = _fit_and_predict_original_split(
                        train,
                        validation,
                        params,
                    )
                except Exception:
                    logger.exception(
                        "江苏电力模型回测失败 generation=%d candidate=%d fold=%d",
                        generation + 1,
                        candidate_index + 1,
                        fold_index,
                    )
                    continue
                fold_rmses.append(metrics["rmse"])
                current_fold_metrics.append(
                    {
                        "fold": fold_index,
                        "label": (
                            f"{validation['ds'].min().date()}~"
                            f"{validation['ds'].max().date()}"
                        ),
                        "metrics": metrics,
                    }
                )
                if (
                    fold_index == 1
                    or fold_index % 5 == 0
                    or fold_index == len(splits)
                ):
                    logger.info(
                        "江苏电力模型回测进度 generation=%d/4 candidate=%d/%d "
                        "fold=%d/%d",
                        generation + 1,
                        candidate_index + 1,
                        len(population),
                        fold_index,
                        len(splits),
                    )
            average_rmse = (
                float(np.mean(fold_rmses)) if fold_rmses else float("inf")
            )
            generation_scores.append(average_rmse)
            candidate_evaluations.append(
                {
                    "generation": generation + 1,
                    "candidate_index": candidate_index,
                    "params": dict(params),
                    "metrics": {"rmse": average_rmse},
                }
            )
            completed_candidates += 1
            logger.info(
                "江苏电力模型训练进度 generation=%d/4 candidate=%d/%d "
                "completed=%d/%d folds=%d rmse=%.6f",
                generation + 1,
                candidate_index + 1,
                len(population),
                completed_candidates,
                total_candidates,
                len(fold_rmses),
                average_rmse,
            )
            if average_rmse < best_score:
                best_score = average_rmse
                best_params = dict(params)
                best_fold_metrics = current_fold_metrics

        best_index = int(np.argmin(generation_scores))
        evolution_history.append(
            {
                "generation": generation + 1,
                "scores": generation_scores,
                "best_rmse": float(generation_scores[best_index]),
                "best_params": dict(population[best_index]),
            }
        )
        if generation < 3:
            elite = dict(population[best_index])
            new_population = [dict(elite)]
            for _ in range(3):
                mutant = dict(elite)
                key = str(np.random.choice(list(mutant.keys())))
                if key == "cps":
                    mutant[key] = float(
                        np.clip(
                            mutant[key] * np.random.choice([0.5, 1.5, 2.0]),
                            0.01,
                            0.5,
                        )
                    )
                elif key == "max_depth":
                    mutant[key] = int(
                        np.clip(mutant[key] + np.random.choice([-1, 1]), 2, 6)
                    )
                elif key == "num_leaves":
                    mutant[key] = int(
                        np.clip(
                            mutant[key] + np.random.choice([-8, 8, 15]), 4, 63
                        )
                    )
                elif key in {"reg_alpha", "reg_lambda"}:
                    mutant[key] = float(
                        np.clip(
                            mutant[key] * np.random.choice([0.5, 2.0]), 0.0, 10.0
                        )
                    )
                elif key == "lr":
                    mutant[key] = float(
                        np.clip(
                            mutant[key] * np.random.choice([0.7, 1.3]),
                            0.005,
                            0.1,
                        )
                    )
                new_population.append(mutant)
            population = new_population

    if best_params is None or not np.isfinite(best_score):
        raise RuntimeError("四代进化搜索没有得到可用模型")

    relative_error = best_score / clean["y"].mean() * 100
    time_leakage = any(
        full_features.iloc[validation_slice]["ds"].min()
        <= full_features.iloc[train_slice]["ds"].max()
        for train_slice, validation_slice in splits
    )
    security_checks = {
        "mape_safe": bool(relative_error > 1.0),
        "time_leakage_free": not time_leakage,
        "all_passed": bool(relative_error > 1.0 and not time_leakage),
    }

    cutoff = full_features["ds"].max() - pd.Timedelta(days=119)
    train_final = (
        full_features[full_features["ds"] < cutoff].copy().reset_index(drop=True)
    )
    test_final = (
        full_features[full_features["ds"] >= cutoff].copy().reset_index(drop=True)
    )
    (
        train_final,
        evaluation_means,
        evaluation_stds,
        evaluation_fallback_mean,
        evaluation_fallback_std,
    ) = _features_with_climatology(train_final, train_final)
    test_final = _add_climatology(
        test_final,
        evaluation_means,
        evaluation_stds,
        evaluation_fallback_mean,
        evaluation_fallback_std,
    )
    evaluation_prophet_model = _fit_prophet(
        train_final[["ds", "y"]], best_params["cps"]
    )
    prophet_train = _prophet_components(evaluation_prophet_model, train_final["ds"])
    prophet_test = _prophet_components(evaluation_prophet_model, test_final["ds"])
    train_matrix = _merge_prophet_features(train_final, prophet_train)
    test_matrix = _merge_prophet_features(test_final, prophet_test)
    feature_names = [
        column
        for column in train_matrix.columns
        if column not in {"ds", "y", "doy"}
    ]
    residual_target = (
        train_final["y"].astype(float).reset_index(drop=True)
        - prophet_train["yhat"].astype(float).reset_index(drop=True)
    )
    evaluation_residual_model = _new_residual_model(best_params)
    evaluation_residual_model.fit(train_matrix[feature_names], residual_target)
    predicted = (
        prophet_test["yhat"].astype(float).to_numpy()
        + evaluation_residual_model.predict(test_matrix[feature_names])
    )
    actual = test_final["y"].to_numpy(dtype=float)
    final_metrics = calculate_metrics(actual, predicted)
    logger.info(
        "江苏电力模型训练完成 candidates=%d folds=%d rmse=%.6f",
        completed_candidates,
        len(splits),
        final_metrics["rmse"],
    )
    residuals = actual - predicted

    # 评估指标仍严格来自原脚本最后120天留出集。选参和评估完成后，再使用
    # 全部可用于建模的历史数据拟合生产模型，避免线上预测丢失最近120天信息。
    production_features, means, stds, fallback_mean, fallback_std = (
        _features_with_climatology(full_features, full_features)
    )
    prophet_model = _fit_prophet(
        production_features[["ds", "y"]], best_params["cps"]
    )
    production_prophet = _prophet_components(
        prophet_model, production_features["ds"]
    )
    production_matrix = _merge_prophet_features(
        production_features, production_prophet
    )
    feature_names = [
        column
        for column in production_matrix.columns
        if column not in {"ds", "y", "doy"}
    ]
    production_residual_target = (
        production_features["y"].astype(float).reset_index(drop=True)
        - production_prophet["yhat"].astype(float).reset_index(drop=True)
    )
    residual_model = _new_residual_model(best_params)
    residual_model.fit(
        production_matrix[feature_names], production_residual_target
    )

    artifact = JiangshuDianliV1Artifact(
        prophet_model=prophet_model,
        residual_model=residual_model,
        feature_names=feature_names,
        selected_params=best_params,
        history_dates=[str(value.date()) for value in clean["ds"]],
        history_values=clean["y"].astype(float).tolist(),
        climatology_mean=means,
        climatology_std=stds,
        climatology_fallback_mean=fallback_mean,
        climatology_fallback_std=fallback_std,
        start_date=str(start_date.date()),
        train_start=str(production_features["ds"].min().date()),
        train_end=str(clean["ds"].max().date()),
        residual_lower=float(np.quantile(residuals, 0.05)),
        residual_upper=float(np.quantile(residuals, 0.95)),
        external_feature_names=external_feature_names,
        metadata={
            "selection_method": "four_generation_walk_forward_rmse",
            "security_checks": security_checks,
            "walk_forward_rmse": best_score,
            "external_feature_names": external_feature_names,
            "production_refit_full_history": True,
            "production_training_rows": len(production_features),
            "evaluation_train_end": str(train_final["ds"].max().date()),
            "evaluation_test_start": str(test_final["ds"].min().date()),
            "evaluation_test_end": str(test_final["ds"].max().date()),
        },
    )
    fold_label = f"{test_final['ds'].min().date()}~{test_final['ds'].max().date()}"
    backtest_rows = [
        {
            "fold": fold_label,
            "date": str(date_value.date()),
            "actual": float(actual_value),
            "prediction": float(predicted_value),
        }
        for date_value, actual_value, predicted_value in zip(
            test_final["ds"], actual, predicted
        )
    ]
    return JiangshuDianliV1TrainingOutput(
        artifact=artifact,
        metrics=final_metrics,
        backtest_rows=backtest_rows,
        fold_metrics=best_fold_metrics,
        candidate_evaluations=candidate_evaluations,
        selected_params=best_params,
        baseline_metrics=baseline_metrics,
        evolution_history=evolution_history,
        security_checks=security_checks,
        n_folds=len(splits),
        walk_forward_rmse=best_score,
    )


class ModelJiangshuDianliV1Handler:
    """江苏电力日级预测模型 V1 的统一接口。"""

    info = ModelInfo(
        agent_code="short-term",
        model_code="MODEL_JIANGSHU_DIANLI_V1.0",
        model_version="1.0.0",
        model_name="江苏电力日级预测模型 V1",
        description="Prophet 与 LightGBM 残差融合，使用 Walk-Forward 回测选择参数。",
        capabilities=["train", "backtest", "predict"],
        training_data_range=TrainingDataRange(
            type="history_length",
            frequency="day",
            minimum=880,
            recommended=1060,
            continuous=True,
            description="至少需要880天连续日数据，建议提供1060天以上。",
        ),
    )

    def validate_training_data(
        self, context: ModelContext
    ) -> TrainingDataValidationResult:
        clean = prepare_training_data(pd.DataFrame(context.dataset))
        clean = clean.sort_values("ds").reset_index(drop=True)
        dates = clean["ds"]
        duplicate_count = int(dates.duplicated().sum())
        unique_dates = pd.DatetimeIndex(dates.drop_duplicates())
        expected_dates = pd.date_range(unique_dates.min(), unique_dates.max(), freq="D")
        missing_dates = expected_dates.difference(unique_dates)
        requirement = self.info.training_data_range
        if requirement is None:  # pragma: no cover - 注册契约会提前阻止
            raise RuntimeError("江苏电力模型未声明训练数据范围")

        errors: list[TrainingDataValidationIssue] = []
        warnings: list[TrainingDataValidationIssue] = []
        if len(clean) < requirement.minimum:
            errors.append(
                TrainingDataValidationIssue(
                    code="INSUFFICIENT_HISTORY",
                    message=(
                        f"江苏电力模型至少需要{requirement.minimum}天数据，"
                        f"当前只有{len(clean)}天"
                    ),
                    expected=requirement.minimum,
                    actual=len(clean),
                )
            )
        if duplicate_count:
            errors.append(
                TrainingDataValidationIssue(
                    code="DUPLICATE_DATES",
                    message=f"训练数据存在{duplicate_count}个重复日期",
                    expected=0,
                    actual=duplicate_count,
                )
            )
        if len(missing_dates):
            errors.append(
                TrainingDataValidationIssue(
                    code="NON_CONTINUOUS_DATES",
                    message=f"训练数据缺少{len(missing_dates)}个日期，日级数据必须连续",
                    expected=0,
                    actual=len(missing_dates),
                )
            )
        if requirement.recommended and len(clean) < requirement.recommended:
            warnings.append(
                TrainingDataValidationIssue(
                    code="BELOW_RECOMMENDED_HISTORY",
                    message=(
                        f"建议提供至少{requirement.recommended}天数据，"
                        f"当前为{len(clean)}天"
                    ),
                    expected=requirement.recommended,
                    actual=len(clean),
                )
            )

        return TrainingDataValidationResult(
            agent_code=self.info.agent_code,
            model_code=self.info.model_code,
            valid=not errors,
            summary={
                "row_count": len(clean),
                "unique_date_count": len(unique_dates),
                "start_date": str(unique_dates.min().date()),
                "end_date": str(unique_dates.max().date()),
                "missing_date_count": len(missing_dates),
                "duplicate_date_count": duplicate_count,
            },
            errors=errors,
            warnings=warnings,
        )

    def train(self, context: ModelContext) -> TrainResult:
        if not context.train_batch_no:
            raise ValueError("江苏电力模型训练需要提供顶层 train_batch_no")
        batch_no = self._safe_batch_no(context.train_batch_no)
        target = self._artifact_path(batch_no)
        if target.exists():
            raise ValueError(f"训练批次已存在，禁止覆盖: {batch_no}")
        output = train_model(pd.DataFrame(context.dataset))
        self._save_artifact(output.artifact, target)
        ranking = sorted(
            output.candidate_evaluations,
            key=lambda item: item["metrics"]["rmse"],
        )
        rolling = self._rolling_points(output.backtest_rows)
        return TrainResult(
            agent_code=self.info.agent_code,
            model_code=self.info.model_code,
            train_batch_no=batch_no,
            metrics=self._metrics(output.metrics),
            feature_names=output.artifact.feature_names,
            selected_model_name="Prophet+LightGBM",
            selection_reason="按原脚本4代进化搜索的 Walk-Forward 平均 RMSE 选择最优参数。",
            requested_candidate_count=len(ranking),
            successful_candidate_count=len(ranking),
            ranked_candidate_count=len(ranking),
            selected_model_params=output.selected_params,
            candidate_evaluations=[
                CandidateEvaluation(
                    rank=index,
                    model_name=(
                        f"Prophet+LightGBM-G{item['generation']}"
                        f"-P{item['candidate_index'] + 1}"
                    ),
                    model_type="hybrid",
                    selected=index == 1,
                    metrics=self._metrics(item["metrics"]),
                )
                for index, item in enumerate(ranking, start=1)
            ],
            rolling_backtest_results=rolling,
            rolling_backtest_fold_metrics=[
                RollingBacktestFoldMetric(
                    model_name="Prophet+LightGBM",
                    season=row["label"],
                    stage="evaluation",
                    metrics=self._metrics(row["metrics"]),
                )
                for row in output.fold_metrics
            ],
            metadata={
                "forecast_unit": "day",
                "algorithm": "Prophet+LightGBM",
                "artifact_sha256": sha256(target.read_bytes()).hexdigest(),
                "model_version": output.artifact.model_version,
                "artifact_version": output.artifact.artifact_version,
                "train_start": output.artifact.train_start,
                "train_end": output.artifact.train_end,
                "backtest_point_count": len(rolling),
                "baseline_metrics": output.baseline_metrics,
                "evolution_history": output.evolution_history,
                "security_checks": output.security_checks,
                "walk_forward_fold_count": output.n_folds,
                "walk_forward_rmse": output.walk_forward_rmse,
                "external_feature_names": output.artifact.external_feature_names,
            },
        )

    def backtest(self, context: ModelContext) -> BacktestResult:
        output = train_model(pd.DataFrame(context.dataset))
        return BacktestResult(
            agent_code=self.info.agent_code,
            model_code=self.info.model_code,
            metrics=self._metrics(output.metrics),
            points=[
                BacktestPoint(
                    stat_date=pd.Timestamp(row["date"]).date(),
                    actual_value=float(row["actual"]),
                    predicted_value=float(row["prediction"]),
                )
                for row in output.backtest_rows
            ],
            metadata={
                "algorithm": "Prophet+LightGBM",
                "selected_model_params": output.selected_params,
                "fold_metrics": output.fold_metrics,
                "baseline_metrics": output.baseline_metrics,
                "evolution_history": output.evolution_history,
                "security_checks": output.security_checks,
                "walk_forward_rmse": output.walk_forward_rmse,
            },
        )

    def predict(self, context: ModelContext) -> PredictResult:
        if context.forecast_unit != "day":
            raise ValueError("MODEL_JIANGSHU_DIANLI_V1.0 的 forecast_unit 必须是 day")
        if not context.train_batch_no:
            raise ValueError("江苏电力模型预测需要提供顶层 train_batch_no")
        if not context.forecast_batch_no:
            raise ValueError("江苏电力模型预测需要提供顶层 forecast_batch_no")
        if len(context.dataset) < context.forecast_horizon:
            raise ValueError("江苏电力模型预测日期条数不能少于 forecast_horizon")
        artifact = self._load_artifact(self._artifact_path(context.train_batch_no))
        future = prepare_future_data(
            pd.DataFrame(context.dataset),
            artifact.external_feature_names,
        ).iloc[:context.forecast_horizon]
        forecast = predict_with_artifact(artifact, future)
        return PredictResult(
            agent_code=self.info.agent_code,
            model_code=self.info.model_code,
            forecast_batch_no=context.forecast_batch_no,
            points=[
                ForecastPoint(
                    forecast_date=pd.Timestamp(row["date"]).date(),
                    prediction=float(row["prediction"]),
                    lower_value=float(row["lower"]),
                    upper_value=float(row["upper"]),
                    extra={"algorithm": "Prophet+LightGBM"},
                )
                for row in forecast
            ],
            metadata={
                "train_batch_no": context.train_batch_no,
                "train_start": artifact.train_start,
                "train_end": artifact.train_end,
                "model_version": artifact.model_version,
                "artifact_version": artifact.artifact_version,
            },
        )

    def _artifact_path(self, batch_no: str) -> Path:
        return (
            settings.artifact_root
            / self._safe_batch_no(batch_no)
            / "model.joblib"
        )

    @staticmethod
    def _safe_batch_no(value: str) -> str:
        batch_no = str(value).strip()
        if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,64}", batch_no):
            raise ValueError("train_batch_no 格式不正确，只能包含字母、数字、点、下划线和中划线")
        return batch_no

    @staticmethod
    def _save_artifact(artifact: JiangshuDianliV1Artifact, path: Path) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        joblib.dump(artifact, path)

    @staticmethod
    def _load_artifact(path: Path) -> JiangshuDianliV1Artifact:
        if not path.exists():
            raise FileNotFoundError(f"训练批次对应的模型产物不存在: {path}")
        artifact = joblib.load(path)
        if not isinstance(artifact, JiangshuDianliV1Artifact):
            raise TypeError("文件不是 MODEL_JIANGSHU_DIANLI_V1.0 的模型产物")
        if artifact.artifact_version != "1.0":
            raise ValueError(f"不支持的江苏电力模型产物版本: {artifact.artifact_version}")
        return artifact

    def _rolling_points(self, rows: list[dict[str, Any]]) -> list[RollingBacktestPoint]:
        points: list[RollingBacktestPoint] = []
        for row in rows:
            actual = float(row["actual"])
            predicted = float(row["prediction"])
            error = predicted - actual
            points.append(
                RollingBacktestPoint(
                    model_name="Prophet+LightGBM",
                    season=str(row["fold"]),
                    stat_date=pd.Timestamp(row["date"]).date(),
                    actual_value=actual,
                    predicted_value=predicted,
                    error_value=error,
                    absolute_error=abs(error),
                    error_rate=abs(error) / abs(actual) * 100 if actual else None,
                )
            )
        return points

    @staticmethod
    def _metrics(values: dict[str, float]) -> MetricSet:
        return MetricSet(
            **{key: value for key, value in values.items() if key in MetricSet.model_fields}
        )
