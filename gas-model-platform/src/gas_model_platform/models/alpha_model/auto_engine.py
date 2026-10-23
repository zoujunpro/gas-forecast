"""QuantaAlpha ForecastAgent Auto 流程的无 LLM 算法层。

本模块从原 ``agent_core.agent.forecast_agent.ForecastAgent._run_auto`` 拆出，
保留“划分后清洗、验证集选模调参、独立测试集评估、全量预测”的顺序，
去除 ReAct、LLM、记忆、绘图和本地 Markdown 报告职责。
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

import pandas as pd

from .tools.forecast.data_tools import clean_data, resolve_holdout_size, split_data
from .tools.forecast.optimization_tools import (
    DEFAULT_SELECT_MODELS,
    auto_select_model,
    evaluate_holdout,
    predict_best_model,
    tune_best_model,
)


@dataclass
class AutoTrainingOutput:
    history: pd.DataFrame
    train: pd.DataFrame
    validation: pd.DataFrame
    test: pd.DataFrame
    best_model: str
    tuned_params: dict[str, Any]
    candidate_metrics: dict[str, dict[str, Any]]
    test_metrics: dict[str, Any]
    test_predictions: list[float]
    issues: list[dict[str, str]]
    outlier_bounds: dict[str, Any] | None


def train_auto(
    frame: pd.DataFrame,
    *,
    date_col: str = "date",
    value_col: str = "gas_sales",
    holdout_size: int = 10,
    candidate_models: list[str] | None = None,
    tune_iterations: int = 12,
    metric: str = "rmse",
) -> AutoTrainingOutput:
    """执行原 Auto 模式的训练、选模、调参和独立测试集评估。"""
    source = frame[[date_col, value_col]].copy()
    source[date_col] = pd.to_datetime(source[date_col])
    source[value_col] = pd.to_numeric(source[value_col], errors="coerce")
    source = source.sort_values(date_col).reset_index(drop=True)

    n_test = resolve_holdout_size(len(source), holdout_size)
    split = split_data(
        source,
        date_col,
        value_col,
        train_ratio=0.7,
        val_ratio=0.15,
        n_test=n_test,
    )
    if not split.success:
        raise ValueError(f"数据划分失败：{split.error}")
    train = split.data["train"]
    validation = split.data.get("val")
    test = split.data["test"]
    if validation is None or validation.empty:
        cut = max(1, len(train) // 5)
        validation = train.iloc[-cut:].copy()
        train = train.iloc[:-cut].copy()
    if train.empty or validation.empty or test.empty:
        raise ValueError("训练、验证或测试数据为空，无法执行自动选模")

    cleaned_train = clean_data(
        train, date_col, value_col, fill_method="linear", outlier_method="iqr"
    )
    if not cleaned_train.success:
        raise ValueError(f"训练集清洗失败：{cleaned_train.error}")
    bounds = cleaned_train.metadata.get("outlier_bounds")
    eval_method = "iqr" if bounds is not None else "none"
    cleaned_validation = clean_data(
        validation, date_col, value_col, fill_method="linear",
        outlier_method=eval_method, outlier_bounds=bounds,
    )
    cleaned_test = clean_data(
        test, date_col, value_col, fill_method="linear",
        outlier_method=eval_method, outlier_bounds=bounds,
    )
    if not cleaned_validation.success:
        raise ValueError(f"验证集清洗失败：{cleaned_validation.error}")
    if not cleaned_test.success:
        raise ValueError(f"测试集清洗失败：{cleaned_test.error}")

    train = cleaned_train.data["df"]
    validation = cleaned_validation.data["df"]
    test = cleaned_test.data["df"]
    history = pd.concat([train, validation, test], ignore_index=True)
    history = history.sort_values(date_col).reset_index(drop=True)
    models = list(candidate_models or DEFAULT_SELECT_MODELS)

    selection = auto_select_model(
        train, validation, date_col, value_col,
        horizon=len(validation), metric=metric, models=models,
    )
    if not selection.success:
        raise ValueError(f"自动模型选择失败：{selection.error}")
    best_model = selection.data["best_model"]
    candidate_metrics = selection.data["all_metrics"]
    issues: list[dict[str, str]] = []

    tuning = tune_best_model(
        train, validation, date_col, value_col,
        model_name=best_model, n_iter=max(1, min(int(tune_iterations), 50)),
        metric=metric,
    )
    if tuning.success:
        tuned_params = tuning.data.get("best_params") or {}
    else:
        tuned_params = {}
        issues.append({"stage": "tuning", "message": str(tuning.error)})

    holdout = evaluate_holdout(
        train, test, date_col, value_col, model_name=best_model,
        params=tuned_params, val_df=validation, metric=metric,
    )
    if not holdout.success:
        raise ValueError(f"独立测试集评估失败：{holdout.error}")

    return AutoTrainingOutput(
        history=history,
        train=train,
        validation=validation,
        test=test,
        best_model=best_model,
        tuned_params=tuned_params,
        candidate_metrics=candidate_metrics,
        test_metrics=holdout.data.get("test_metrics") or {},
        test_predictions=list(holdout.data.get("test_y_pred") or []),
        issues=issues,
        outlier_bounds=bounds,
    )


def forecast_auto(
    history: pd.DataFrame,
    *,
    model_name: str,
    params: dict[str, Any],
    horizon: int,
    date_col: str = "date",
    value_col: str = "gas_sales",
) -> pd.DataFrame:
    """使用训练阶段选出的模型和参数在完整历史数据上生成未来预测。"""
    result = predict_best_model(
        history, date_col, value_col, horizon,
        model_name=model_name, params=params,
    )
    if not result.success:
        raise ValueError(f"预测失败：{result.error}")
    return result.data["forecast_df"].copy()
