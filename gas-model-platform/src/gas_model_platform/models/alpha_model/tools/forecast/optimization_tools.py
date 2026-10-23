"""
优化调参工具 — 超参数搜索、自动模型选择

支持：
- 网格搜索
- 随机搜索
- Optuna (可选依赖)
- 自动模型选择（跑一批模型选最优）
"""

from __future__ import annotations

import itertools
import math
import random
from typing import Optional, Union

import numpy as np
import pandas as pd

from ..base import ToolResult
from .eval_tools import evaluate


# 短名 -> 注册表工具名（ForecastAgent / 工作流用）
FORECAST_TOOL_NAMES = {
    "naive": "forecast_naive",
    "seasonal_naive": "forecast_seasonal_naive",
    "sma": "forecast_sma",
    "ewma": "forecast_ewma",
    "holt_winters": "forecast_holt_winters",
    "arima": "forecast_arima",
    "prophet": "forecast_prophet",
    "linear": "forecast_linear_regression",
    "rf": "forecast_random_forest",
    "xgb": "forecast_xgboost",
    "lgbm": "forecast_lightgbm",
    "prophet_xgboost": "forecast_prophet_xgboost",
    "prophet_lightgbm": "forecast_prophet_lightgbm",
    "prophet_ensemble": "forecast_prophet_ensemble",
}

# 随机/网格搜索的默认候选（list=离散候选，tuple=(low, high) 连续区间）
DEFAULT_PARAM_SPACES = {
    "sma": {"window": [3, 7, 14, 21, 30]},
    "ewma": {"alpha": [0.1, 0.3, 0.5, 0.7, 0.9]},
    "holt_winters": {"period": [7, 12, 24, 30], "trend": ["add"], "seasonal": ["add"]},
    "arima": {"p": [0, 1, 2], "d": [0, 1], "q": [0, 1, 2]},
    "prophet": {
        "changepoint_prior_scale": [0.05, 0.1, 0.2, 0.5],
        "seasonality_mode": ["additive", "multiplicative"],
    },
    "linear": {"use_time_features": [True, False]},
    "rf": {"n_estimators": [50, 100, 200], "max_depth": [3, 5, 8]},
    "xgb": {
        "n_estimators": [100, 300, 500],
        "max_depth": [3, 4, 6],
        "learning_rate": [0.01, 0.05, 0.1],
    },
    "lgbm": {
        "n_estimators": [100, 300, 500],
        "max_depth": [3, 4, 6],
        "learning_rate": [0.01, 0.05, 0.1],
    },
    "prophet_xgboost": {
        "max_depth": [3, 4],
        "learning_rate": [0.02, 0.05],
        "n_estimators": [200, 500],
    },
    "prophet_lightgbm": {
        "max_depth": [3, 4],
        "learning_rate": [0.01, 0.05],
        "n_estimators": [300, 800],
    },
}


REVERSE_TOOL_NAMES = {v: k for k, v in FORECAST_TOOL_NAMES.items()}

# auto_select 默认候选：统计 + ML + Prophet 残差模型（失败的会自动跳过）
DEFAULT_SELECT_MODELS = [
    "naive",
    "sma",
    "ewma",
    "holt_winters",
    "arima",
    "prophet",
    "xgb",
    "lgbm",
    "prophet_xgboost",
    "prophet_lightgbm",
]

SUPPORTED_METRICS = {
    "mae", "mse", "rmse", "mape", "smape", "bias_pct", "direction_acc", "mase",
}
MAXIMIZE_METRICS = {"direction_acc"}
TARGET_ZERO_METRICS = {"bias_pct"}


def _normalize_metric(metric: str) -> str:
    """校验并标准化评估指标名。"""
    normalized = (metric or "").strip().lower()
    if normalized not in SUPPORTED_METRICS:
        available = ", ".join(sorted(SUPPORTED_METRICS))
        raise ValueError(f"不支持的评估指标: {metric!r}，可选: {available}")
    return normalized


def _metric_value(metrics: dict, metric: str) -> Optional[float]:
    """读取有限的数值指标；None/NaN/inf 均视为无效。"""
    value = metrics.get(metric)
    if value is None or not isinstance(value, (int, float, np.integer, np.floating)):
        return None
    value = float(value)
    return value if math.isfinite(value) else None


def _metric_sort_key(metrics: dict, metric: str) -> float:
    """统一转换成越小越优的排序值。"""
    value = _metric_value(metrics, metric)
    if value is None:
        return float("inf")
    if metric in MAXIMIZE_METRICS:
        return -value
    if metric in TARGET_ZERO_METRICS:
        return abs(value)
    return value


def resolve_model_name(model_name: str) -> str:
    """把工具全名或短名统一成 auto_select 使用的短名。"""
    if not model_name:
        return model_name
    if model_name in FORECAST_TOOL_NAMES:
        return model_name
    if model_name in REVERSE_TOOL_NAMES:
        return REVERSE_TOOL_NAMES[model_name]
    return model_name


def resolve_forecast_tool_name(model_name: str) -> str:
    """把短名解析成注册表中的 forecast_* 工具名。"""
    short = resolve_model_name(model_name)
    return FORECAST_TOOL_NAMES.get(short, f"forecast_{short}")


# ============================================
# 自动模型选择
# ============================================

def auto_select_model(
    train_df: pd.DataFrame,
    test_df: pd.DataFrame,
    date_col: str,
    value_col: str,
    horizon: Optional[int] = None,
    models: Optional[list[str]] = None,
    metric: str = "rmse",
) -> ToolResult:
    """
    自动模型选择 — 跑多个模型，在测试集上选最优

    models: 可选的模型列表。默认包含统计模型、XGBoost/LightGBM，以及
    Prophet+XGBoost / Prophet+LightGBM。某个模型失败会跳过，不拖垮整次选择。
    """
    try:
        metric = _normalize_metric(metric)
        if models is None:
            models = list(DEFAULT_SELECT_MODELS)
        elif isinstance(models, str):
            models = [m.strip() for m in models.split(",") if m.strip()]

        if horizon is None:
            horizon = len(test_df)

        results = {}
        all_metrics = {}

        y_true = test_df[value_col].values

        for model_name in models:
            try:
                fc_result = _run_forecast(
                    model_name, train_df, date_col, value_col, horizon
                )
                if not fc_result.success:
                    continue

                y_pred = np.asarray(fc_result.data["forecast_values"], dtype=float)
                if len(y_pred) < len(y_true):
                    continue
                y_pred_aligned = y_pred[:len(y_true)]
                if not np.all(np.isfinite(y_pred_aligned)):
                    continue

                eval_result = evaluate(
                    y_true, y_pred_aligned,
                    y_train=train_df[value_col].values,
                )
                if eval_result.success and _metric_value(eval_result.data, metric) is not None:
                    all_metrics[model_name] = eval_result.data
                    results[model_name] = fc_result.data
            except Exception:
                continue

        if not all_metrics:
            return ToolResult(
                data=None, success=False,
                error="没有模型成功运行",
            )

        # 排序找最佳
        ranked = sorted(
            all_metrics.items(),
            key=lambda x: _metric_sort_key(x[1], metric),
        )
        best_model = ranked[0][0]
        best_metric_val = ranked[0][1].get(metric)
        eval_predictions = {}
        y_eval = np.asarray(y_true).tolist()
        n_eval = len(y_eval)
        for name in results:
            raw = results[name]
            if isinstance(raw, dict) and "forecast_values" in raw:
                pred = np.asarray(raw["forecast_values"]).tolist()
                eval_predictions[name] = pred[:n_eval]

        return ToolResult(
            data={
                "best_model": best_model,
                f"best_{metric}": best_metric_val,
                "best_metrics": ranked[0][1],
                "all_metrics": all_metrics,
                "all_results": results,
                "ranking": [name for name, _ in ranked],
                "metric": metric,
                "n_models_tried": len(models),
                "n_models_success": len(all_metrics),
                "eval_predictions": eval_predictions,
                "y_eval": y_eval,
            },
            metadata={
                "best_model": best_model,
                "n_success": len(all_metrics),
                "n_total": len(models),
            },
        )

    except Exception as e:
        return ToolResult(data=None, success=False, error=str(e))


def _run_forecast(model_name, train_df, date_col, value_col, horizon):
    """运行单个预测模型"""
    model_name = resolve_model_name(model_name)
    if model_name == "naive":
        from .stat_models import forecast_naive
        return forecast_naive(train_df, date_col, value_col, horizon)
    elif model_name == "seasonal_naive":
        from .stat_models import forecast_seasonal_naive
        return forecast_seasonal_naive(train_df, date_col, value_col, horizon)
    elif model_name == "sma":
        from .stat_models import forecast_sma
        return forecast_sma(train_df, date_col, value_col, horizon)
    elif model_name == "ewma":
        from .stat_models import forecast_ewma
        return forecast_ewma(train_df, date_col, value_col, horizon)
    elif model_name == "holt_winters":
        from .stat_models import forecast_holt_winters
        return forecast_holt_winters(train_df, date_col, value_col, horizon)
    elif model_name == "arima":
        from .stat_models import forecast_arima
        return forecast_arima(train_df, date_col, value_col, horizon)
    elif model_name == "prophet":
        from .stat_models import forecast_prophet
        return forecast_prophet(train_df, date_col, value_col, horizon)
    elif model_name == "linear":
        from .ml_models import forecast_linear_regression
        return forecast_linear_regression(train_df, date_col, value_col, horizon)
    elif model_name == "rf":
        from .ml_models import forecast_random_forest
        return forecast_random_forest(train_df, date_col, value_col, horizon)
    elif model_name == "xgb":
        from .ml_models import forecast_xgboost
        return forecast_xgboost(train_df, date_col, value_col, horizon)
    elif model_name == "lgbm":
        from .ml_models import forecast_lightgbm
        return forecast_lightgbm(train_df, date_col, value_col, horizon)
    elif model_name in ("prophet_xgboost", "prophet_xgb"):
        from .prophet_residual import forecast_prophet_xgboost
        return forecast_prophet_xgboost(train_df, date_col, value_col, horizon)
    elif model_name in ("prophet_lightgbm", "prophet_lgbm"):
        from .prophet_residual import forecast_prophet_lightgbm
        return forecast_prophet_lightgbm(train_df, date_col, value_col, horizon)
    elif model_name == "prophet_ensemble":
        from .prophet_residual import forecast_prophet_ensemble
        return forecast_prophet_ensemble(train_df, date_col, value_col, horizon)
    else:
        return ToolResult(data=None, success=False, error=f"未知模型: {model_name}")


# ============================================
# 网格搜索（针对特定模型）
# ============================================

def grid_search(
    train_df: pd.DataFrame,
    val_df: pd.DataFrame,
    date_col: str,
    value_col: str,
    model_name: str,
    param_grid: dict,
    metric: str = "rmse",
) -> ToolResult:
    """
    网格搜索超参数

    param_grid: {param_name: [候选值列表]}
    """
    try:
        metric = _normalize_metric(metric)
        horizon = len(val_df)
        y_true = val_df[value_col].values
        model_name = resolve_model_name(model_name)

        # 生成所有参数组合
        keys = list(param_grid.keys())
        values = list(param_grid.values())
        combos = [dict(zip(keys, combo)) for combo in itertools.product(*values)]

        results = []
        for params in combos:
            try:
                fc_result = _run_forecast_with_params(
                    model_name, train_df, date_col, value_col, horizon, params
                )
                if not fc_result.success:
                    continue

                y_pred = np.asarray(fc_result.data["forecast_values"], dtype=float)
                if len(y_pred) < len(y_true):
                    continue
                y_pred = y_pred[:len(y_true)]
                if not np.all(np.isfinite(y_pred)):
                    continue
                eval_result = evaluate(y_true, y_pred)
                if eval_result.success and _metric_value(eval_result.data, metric) is not None:
                    results.append({
                        "params": params,
                        "metrics": eval_result.data,
                        "score": _metric_value(eval_result.data, metric),
                    })
            except Exception:
                continue

        if not results:
            return ToolResult(
                data=None, success=False,
                error="没有参数组合成功运行",
            )

        results.sort(key=lambda x: _metric_sort_key(x["metrics"], metric))
        best = results[0]

        return ToolResult(
            data={
                "best_params": best["params"],
                f"best_{metric}": best["score"],
                "best_metrics": best["metrics"],
                "all_results": results,
                "n_combos": len(combos),
                "n_success": len(results),
                "model": model_name,
            },
            metadata={
                "best_score": best["score"],
                "n_combos": len(combos),
                "model": model_name,
            },
        )

    except Exception as e:
        return ToolResult(data=None, success=False, error=str(e))


# ============================================
# 随机搜索
# ============================================

def random_search(
    train_df: pd.DataFrame,
    val_df: pd.DataFrame,
    date_col: str,
    value_col: str,
    model_name: str,
    param_distributions: dict,
    n_iter: int = 20,
    metric: str = "rmse",
    random_state: Optional[int] = None,
) -> ToolResult:
    """
    随机搜索超参数

    param_distributions: {param_name: (low, high) 或 list(候选值)}
    数值型用 uniform 采样，列表型随机选。
    """
    try:
        metric = _normalize_metric(metric)
        if random_state is not None:
            random.seed(random_state)

        horizon = len(val_df)
        y_true = val_df[value_col].values
        model_name = resolve_model_name(model_name)

        results = []
        for _ in range(n_iter):
            params = {}
            for name, dist in param_distributions.items():
                params[name] = _sample_param(dist)

            try:
                fc_result = _run_forecast_with_params(
                    model_name, train_df, date_col, value_col, horizon, params
                )
                if not fc_result.success:
                    continue

                y_pred = np.asarray(fc_result.data["forecast_values"], dtype=float)
                if len(y_pred) < len(y_true):
                    continue
                y_pred = y_pred[:len(y_true)]
                if not np.all(np.isfinite(y_pred)):
                    continue
                eval_result = evaluate(y_true, y_pred)
                if eval_result.success and _metric_value(eval_result.data, metric) is not None:
                    results.append({
                        "params": params,
                        "metrics": eval_result.data,
                        "score": _metric_value(eval_result.data, metric),
                    })
            except Exception:
                continue

        if not results:
            return ToolResult(
                data=None, success=False,
                error="没有参数组合成功运行",
            )

        results.sort(key=lambda x: _metric_sort_key(x["metrics"], metric))
        best = results[0]

        return ToolResult(
            data={
                "best_params": best["params"],
                f"best_{metric}": best["score"],
                "best_metrics": best["metrics"],
                "all_results": results,
                "n_iter": n_iter,
                "n_success": len(results),
                "model": model_name,
            },
            metadata={
                "best_score": best["score"],
                "n_iter": n_iter,
                "model": model_name,
            },
        )

    except Exception as e:
        return ToolResult(data=None, success=False, error=str(e))


# ============================================
# Optuna 优化（可选依赖）
# ============================================

def optuna_optimize(
    train_df: pd.DataFrame,
    val_df: pd.DataFrame,
    date_col: str,
    value_col: str,
    model_name: str,
    param_definitions: dict,
    n_trials: int = 50,
    metric: str = "rmse",
    direction: str = "auto",
) -> ToolResult:
    """
    使用 Optuna 进行超参数优化（可选依赖）

    param_definitions: {param_name: {type: "int"/"float"/"categorical", ...}}
    """
    try:
        import optuna
    except ImportError:
        # 退化为随机搜索
        result = random_search(
            train_df, val_df, date_col, value_col,
            model_name, _optuna_defs_to_dist(param_definitions),
            n_iter=min(n_trials, 20),
            metric=metric,
        )
        if result.success:
            result.data["note"] = "optuna 不可用，使用随机搜索替代"
            result.metadata["optimizer"] = "random_search_fallback"
        return result

    try:
        horizon = len(val_df)
        y_true = val_df[value_col].values
        model_name = resolve_model_name(model_name)
        metric = _normalize_metric(metric)
        if direction == "auto":
            study_direction = "maximize" if metric in MAXIMIZE_METRICS else "minimize"
        elif direction in ("minimize", "maximize"):
            study_direction = direction
        else:
            return ToolResult(
                data=None, success=False,
                error="direction 必须是 auto、minimize 或 maximize",
            )
        failed_value = float("-inf") if study_direction == "maximize" else float("inf")

        def objective(trial):
            params = {}
            for name, defn in param_definitions.items():
                ptype = defn.get("type", "float")
                if ptype == "int":
                    params[name] = trial.suggest_int(name, defn["low"], defn["high"])
                elif ptype == "float":
                    params[name] = trial.suggest_float(name, defn["low"], defn["high"])
                elif ptype == "categorical":
                    params[name] = trial.suggest_categorical(name, defn["choices"])
                elif ptype == "loguniform":
                    params[name] = trial.suggest_float(
                        name, defn["low"], defn["high"], log=True
                    )

            try:
                fc_result = _run_forecast_with_params(
                    model_name, train_df, date_col, value_col, horizon, params
                )
                if not fc_result.success:
                    return failed_value

                y_pred = np.asarray(fc_result.data["forecast_values"], dtype=float)
                if len(y_pred) < len(y_true):
                    return failed_value
                y_pred = y_pred[:len(y_true)]
                if not np.all(np.isfinite(y_pred)):
                    return failed_value
                eval_result = evaluate(y_true, y_pred)
                if eval_result.success:
                    raw_value = _metric_value(eval_result.data, metric)
                    if raw_value is not None:
                        trial.set_user_attr("raw_metric", raw_value)
                        return abs(raw_value) if metric in TARGET_ZERO_METRICS else raw_value
            except Exception:
                pass
            return failed_value

        study = optuna.create_study(direction=study_direction)
        study.optimize(objective, n_trials=n_trials, show_progress_bar=False)

        successful_trials = [
            trial for trial in study.trials
            if trial.user_attrs.get("raw_metric") is not None
        ]
        if not successful_trials:
            return ToolResult(
                data=None, success=False,
                error="没有 Optuna 参数组合成功运行",
            )
        best_metric_value = study.best_trial.user_attrs.get("raw_metric", study.best_value)

        return ToolResult(
            data={
                "best_params": study.best_params,
                f"best_{metric}": best_metric_value,
                "n_trials": n_trials,
                "n_success": len(successful_trials),
                "model": model_name,
                "study_summary": {
                    "n_trials": len(study.trials),
                    "best_trial_number": study.best_trial.number if study.best_trial else None,
                },
            },
            metadata={
                "best_score": best_metric_value,
                "n_trials": n_trials,
                "optimizer": "optuna",
                "direction": study_direction,
                "model": model_name,
            },
        )

    except Exception as e:
        return ToolResult(data=None, success=False, error=str(e))


def _optuna_defs_to_dist(definitions):
    """把 optuna 风格的 param 定义转成随机搜索用的 dist"""
    dist = {}
    for name, defn in definitions.items():
        ptype = defn.get("type", "float")
        if ptype in ("int", "float"):
            dist[name] = (defn["low"], defn["high"])
        elif ptype == "loguniform":
            dist[name] = {
                "type": "loguniform",
                "low": defn["low"],
                "high": defn["high"],
            }
        elif ptype == "categorical":
            dist[name] = defn["choices"]
    return dist


def _sample_param(dist):
    """
    从参数分布中采样一个值。

    - list: 离散候选（即使只有两个数字也不当成区间，避免 [3, 4] 被当成 randint）
    - tuple(low, high): 连续/整数均匀区间
    - dict(type=loguniform): 对数均匀
    - 其他: 原样返回
    """
    if isinstance(dist, dict) and dist.get("type") == "loguniform":
        low = math.log(float(dist["low"]))
        high = math.log(float(dist["high"]))
        return math.exp(random.uniform(low, high))
    if isinstance(dist, tuple) and len(dist) == 2 \
            and all(isinstance(x, (int, float)) for x in dist) \
            and not isinstance(dist[0], bool):
        low, high = dist
        if isinstance(low, int) and isinstance(high, int):
            return random.randint(low, high)
        return random.uniform(low, high)
    if isinstance(dist, (list, tuple)):
        return random.choice(list(dist))
    return dist


def _filter_params(params: dict, allowed: list[str]) -> dict:
    """只保留目标预测函数认识的参数。"""
    return {k: v for k, v in (params or {}).items() if k in allowed}


# ============================================
# 辅助：带参数运行预测
# ============================================

def _run_forecast_with_params(model_name, train_df, date_col, value_col, horizon, params):
    """带参数运行预测模型（把 params 作为 kwargs 传给预测函数）"""
    model_name = resolve_model_name(model_name)
    params = params or {}
    # 过滤掉模型函数不接受的参数
    if model_name == "sma":
        from .stat_models import forecast_sma
        return forecast_sma(train_df, date_col, value_col, horizon, **_filter_params(params, ["window"]))
    elif model_name == "ewma":
        from .stat_models import forecast_ewma
        return forecast_ewma(
            train_df, date_col, value_col, horizon,
            **_filter_params(params, ["alpha", "span"]),
        )
    elif model_name == "holt_winters":
        from .stat_models import forecast_holt_winters
        return forecast_holt_winters(
            train_df, date_col, value_col, horizon,
            **_filter_params(params, ["period", "trend", "seasonal", "damped_trend"]),
        )
    elif model_name == "arima":
        from .stat_models import forecast_arima
        valid_params = {"auto": False}
        if "p" in params and "d" in params and "q" in params:
            valid_params["order"] = (int(params["p"]), int(params["d"]), int(params["q"]))
        elif "order" in params:
            valid_params["order"] = params["order"]
            valid_params["auto"] = False
        else:
            valid_params["auto"] = True
        if "period" in params:
            valid_params["period"] = params["period"]
        if "seasonal_order" in params:
            valid_params["seasonal_order"] = params["seasonal_order"]
        return forecast_arima(train_df, date_col, value_col, horizon, **valid_params)
    elif model_name == "prophet":
        from .stat_models import forecast_prophet
        return forecast_prophet(
            train_df, date_col, value_col, horizon,
            **_filter_params(params, [
                "yearly_seasonality", "weekly_seasonality", "daily_seasonality",
                "seasonality_mode", "changepoint_prior_scale",
            ]),
        )
    elif model_name == "linear":
        from .ml_models import forecast_linear_regression
        return forecast_linear_regression(
            train_df, date_col, value_col, horizon,
            **_filter_params(params, ["use_time_features"]),
        )
    elif model_name == "rf":
        from .ml_models import forecast_random_forest
        return forecast_random_forest(
            train_df, date_col, value_col, horizon,
            **_filter_params(params, ["n_estimators", "max_depth"]),
        )
    elif model_name == "xgb":
        from .ml_models import forecast_xgboost
        return forecast_xgboost(
            train_df, date_col, value_col, horizon,
            **_filter_params(params, ["n_estimators", "max_depth", "learning_rate"]),
        )
    elif model_name == "lgbm":
        from .ml_models import forecast_lightgbm
        return forecast_lightgbm(
            train_df, date_col, value_col, horizon,
            **_filter_params(params, ["n_estimators", "max_depth", "learning_rate"]),
        )
    elif model_name in ("prophet_xgboost", "prophet_xgb"):
        from .prophet_residual import forecast_prophet_xgboost
        return forecast_prophet_xgboost(
            train_df, date_col, value_col, horizon,
            **_filter_params(params, [
                "changepoint_prior_scale", "max_depth", "learning_rate",
                "n_estimators", "reg_lambda", "reg_alpha", "min_child_weight",
                "subsample", "colsample_bytree",
            ]),
        )
    elif model_name in ("prophet_lightgbm", "prophet_lgbm"):
        from .prophet_residual import forecast_prophet_lightgbm
        return forecast_prophet_lightgbm(
            train_df, date_col, value_col, horizon,
            **_filter_params(params, [
                "changepoint_prior_scale", "num_leaves", "max_depth",
                "learning_rate", "n_estimators", "reg_lambda", "reg_alpha",
                "min_child_samples", "subsample", "colsample_bytree",
            ]),
        )
    elif model_name == "prophet_ensemble":
        from .prophet_residual import forecast_prophet_ensemble
        return forecast_prophet_ensemble(
            train_df, date_col, value_col, horizon,
            **_filter_params(params, ["weights"]),
        )
    else:
        return _run_forecast(model_name, train_df, date_col, value_col, horizon)


def tune_best_model(
    train_df: pd.DataFrame,
    val_df: pd.DataFrame,
    date_col: str,
    value_col: str,
    model_name: str,
    n_iter: int = 15,
    metric: str = "rmse",
    param_grid: Optional[dict] = None,
    method: str = "random",
) -> ToolResult:
    """
    对指定模型在验证集上做超参搜索。

    无搜索空间的模型（如 naive）直接用默认参数评估，不报错。
    """
    try:
        metric = _normalize_metric(metric)
    except ValueError as exc:
        return ToolResult(data=None, success=False, error=str(exc))
    model_name = resolve_model_name(model_name)
    if val_df is None or len(val_df) == 0:
        return ToolResult(data=None, success=False, error="验证集为空，无法调参")

    space = param_grid if param_grid is not None else DEFAULT_PARAM_SPACES.get(model_name)
    if not space:
        fc = _run_forecast(model_name, train_df, date_col, value_col, len(val_df))
        if not fc.success:
            return fc
        y_true = val_df[value_col].values
        y_pred = np.asarray(fc.data["forecast_values"], dtype=float)
        if len(y_pred) < len(y_true):
            return ToolResult(
                data=None, success=False,
                error=f"预测长度不足: 需要 {len(y_true)}，实际 {len(y_pred)}",
            )
        y_pred = y_pred[:len(y_true)]
        if not np.all(np.isfinite(y_pred)):
            return ToolResult(
                data=None, success=False,
                error="预测结果包含 NaN 或无穷值",
            )
        ev = evaluate(y_true, y_pred)
        score = _metric_value(ev.data, metric) if ev.success else None
        if score is None:
            return ToolResult(
                data=None, success=False,
                error=f"模型未产生有效指标: {metric}",
            )
        return ToolResult(
            data={
                "best_params": {},
                f"best_{metric}": score,
                "best_metrics": ev.data if ev.success else {},
                "n_iter": 0,
                "n_success": 1 if ev.success else 0,
                "model": model_name,
                "note": "该模型无可调超参，已用默认参数评估",
            },
            metadata={"best_score": score, "model": model_name, "skipped_search": True},
        )

    if method == "grid":
        return grid_search(
            train_df, val_df, date_col, value_col, model_name, space, metric=metric,
        )
    if method == "optuna":
        definitions = {}
        for k, v in space.items():
            if isinstance(v, list):
                definitions[k] = {"type": "categorical", "choices": v}
            elif isinstance(v, tuple) and len(v) == 2:
                definitions[k] = {
                    "type": "int" if all(isinstance(x, int) for x in v) else "float",
                    "low": v[0],
                    "high": v[1],
                }
        return optuna_optimize(
            train_df, val_df, date_col, value_col, model_name,
            definitions, n_trials=n_iter, metric=metric,
        )
    return random_search(
        train_df, val_df, date_col, value_col, model_name, space,
        n_iter=n_iter, metric=metric,
    )


def predict_best_model(
    df: pd.DataFrame,
    date_col: str,
    value_col: str,
    horizon: int,
    model_name: str,
    params: Optional[dict] = None,
) -> ToolResult:
    """用指定模型（及可选超参）在全量数据上生成未来 horizon 步预测。"""
    model_name = resolve_model_name(model_name)
    if df is None or len(df) == 0:
        return ToolResult(data=None, success=False, error="训练数据为空")
    result = _run_forecast_with_params(
        model_name, df, date_col, value_col, horizon, params or {},
    )
    if result.success:
        result.data["model_name"] = model_name
        result.data["params"] = params or {}
    return result


def evaluate_holdout(
    train_df: pd.DataFrame,
    test_df: pd.DataFrame,
    date_col: str,
    value_col: str,
    model_name: str,
    params: Optional[dict] = None,
    val_df: Optional[pd.DataFrame] = None,
    metric: str = "rmse",
) -> ToolResult:
    """
    在独立测试集上评估模型。

    若提供 val_df，会拼进训练集（先用验证集选参，再用 train+val 评估 test）。
    """
    try:
        metric = _normalize_metric(metric)
    except ValueError as exc:
        return ToolResult(data=None, success=False, error=str(exc))
    model_name = resolve_model_name(model_name)
    if test_df is None or len(test_df) == 0:
        return ToolResult(data=None, success=False, error="测试集为空")

    fit_df = train_df
    if val_df is not None and len(val_df) > 0:
        fit_df = pd.concat([train_df, val_df], ignore_index=True)

    horizon = len(test_df)
    fc = _run_forecast_with_params(
        model_name, fit_df, date_col, value_col, horizon, params or {},
    )
    if not fc.success:
        return fc

    y_true = np.asarray(test_df[value_col].values, dtype=float)
    y_pred = np.asarray(fc.data["forecast_values"], dtype=float)
    if len(y_pred) < len(y_true):
        return ToolResult(
            data=None, success=False,
            error=f"预测长度不足: 需要 {len(y_true)}，实际 {len(y_pred)}",
        )
    y_pred = y_pred[:len(y_true)]
    if not np.all(np.isfinite(y_pred)):
        return ToolResult(
            data=None, success=False,
            error="预测结果包含 NaN 或无穷值",
        )
    ev = evaluate(y_true, y_pred, y_train=fit_df[value_col].values)
    if not ev.success:
        return ev

    return ToolResult(
        data={
            "test_y_true": y_true.tolist(),
            "test_y_pred": y_pred.tolist(),
            "test_metrics": ev.data,
            f"test_{metric}": ev.data.get(metric),
            "model_name": model_name,
            "params": params or {},
            "forecast_values": y_pred.tolist(),
        },
        metadata={
            "model": model_name,
            "metric": metric,
            "n_test": len(y_true),
        },
    )


def build_dataset_info(
    df: pd.DataFrame,
    date_col: str,
    value_col: str,
    best_model: Optional[str] = None,
    horizon: Optional[int] = None,
) -> ToolResult:
    """从数据框构造报告用的 dataset_info。"""
    date_series = pd.to_datetime(df[date_col])
    info = {
        "n_rows": int(len(df)),
        "date_col": date_col,
        "value_col": value_col,
        "date_range": f"{date_series.min()} ~ {date_series.max()}",
        "数据条数": int(len(df)),
        "日期列": date_col,
        "值列": value_col,
        "日期范围": f"{date_series.min()} ~ {date_series.max()}",
    }
    if best_model:
        info["best_model"] = best_model
    if horizon is not None:
        info["horizon"] = horizon
        info["预测期数"] = horizon
    return ToolResult(data={"dataset_info": info, **info})


# 工作流模板使用的工具名别名
_tune_best_model = tune_best_model
_predict_best_model = predict_best_model
_predict_final = predict_best_model
_evaluate_holdout = evaluate_holdout
_build_dataset_info = build_dataset_info
