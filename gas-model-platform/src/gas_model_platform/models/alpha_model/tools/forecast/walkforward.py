"""
Walk-Forward 验证工具

滚动时间窗验证，更准确地评估时间序列模型的真实预测能力。

支持方式：
- walk_forward_validate: 通用 walk-forward 验证函数
- walk_forward_prophet_residual: 针对 Prophet+残差模型的 WF 验证（带调参）

使用方式：
1. 指定初始训练长度、步长、测试长度
2. 对每个折叠训练模型、预测、计算指标
3. 返回各折指标 + 汇总统计
"""

from __future__ import annotations

import os
import warnings
from typing import Callable, Optional

import numpy as np
import pandas as pd

from ..base import ToolResult

os.environ.setdefault("OMP_NUM_THREADS", "1")
warnings.filterwarnings("ignore")


# ============================================
# 指标计算
# ============================================

def _mape(y_true, y_pred):
    mask = y_true != 0
    if not mask.any():
        return float("inf")
    return float(np.mean(np.abs((y_true[mask] - y_pred[mask]) / y_true[mask])) * 100)


def _rmse(y_true, y_pred):
    return float(np.sqrt(np.mean((y_true - y_pred) ** 2)))


def _mae(y_true, y_pred):
    return float(np.mean(np.abs(y_true - y_pred)))


_METRICS = {
    "mape": _mape,
    "rmse": _rmse,
    "mae": _mae,
}


# ============================================
# 通用 Walk-Forward 验证
# ============================================

def walk_forward_validate(
    df: pd.DataFrame,
    date_col: str,
    value_col: str,
    forecast_fn: Callable,
    initial_train: int = 365,
    step: int = 90,
    test_len: int = 90,
    metric: str = "mape",
    forecast_kwargs: Optional[dict] = None,
) -> ToolResult:
    """
    通用 Walk-Forward 验证

    Parameters
    ----------
    df : 完整数据 DataFrame
    date_col : 日期列名
    value_col : 值列名
    forecast_fn : 预测函数，签名为 (train_df, date_col, value_col, horizon, **kwargs) -> ToolResult
    initial_train : 初始训练集长度（行数）
    step : 每次滚动步长（行数）
    test_len : 每次测试集长度（行数）
    metric : 评估指标 "mape" | "rmse" | "mae"
    forecast_kwargs : 传给 forecast_fn 的额外参数

    Returns
    -------
    ToolResult.data = {
        "fold_metrics": [{'fold': i, 'train_end': int, 'test_start': int, 'test_end': int, 'metric': float}],
        "mean_metric": float,
        "std_metric": float,
        "n_folds": int,
        "metric_name": str,
        "all_predictions": list[np.ndarray],  # 各折预测
        "all_actuals": list[np.ndarray],      # 各折真值
    }
    """
    try:
        df = df.copy()
        df[date_col] = pd.to_datetime(df[date_col])
        df = df.sort_values(date_col).reset_index(drop=True)

        n = len(df)
        if forecast_kwargs is None:
            forecast_kwargs = {}

        horizon = test_len
        metric_fn = _METRICS.get(metric.lower())
        if metric_fn is None:
            return ToolResult(
                data=None, success=False,
                error=f"unknown metric: {metric}, available: {list(_METRICS.keys())}",
            )

        # 生成折叠
        folds = []
        end_train = initial_train
        while end_train + test_len <= n:
            folds.append((end_train, end_train + test_len))
            end_train += step

        if not folds:
            return ToolResult(
                data=None, success=False,
                error=f"data too short: n={n}, initial_train={initial_train}, test_len={test_len}",
            )

        fold_metrics = []
        all_preds = []
        all_actuals = []

        for fi, (tr_end, te_end) in enumerate(folds):
            train_df = df.iloc[:tr_end].copy()
            test_df = df.iloc[tr_end:te_end].copy()
            y_true = test_df[value_col].values.astype(float)

            result = forecast_fn(
                train_df, date_col, value_col, horizon, **forecast_kwargs
            )

            if not result.success:
                fold_metrics.append({
                    "fold": fi + 1,
                    "train_end": tr_end,
                    "test_start": tr_end,
                    "test_end": te_end,
                    "metric": float("nan"),
                    "error": result.error,
                })
                all_preds.append(np.full(horizon, np.nan))
                all_actuals.append(y_true)
                continue

            y_pred = np.array(result.data["forecast_values"])
            if len(y_pred) > len(y_true):
                y_pred = y_pred[:len(y_true)]
            elif len(y_pred) < len(y_true):
                y_pred = np.pad(y_pred, (0, len(y_true) - len(y_pred)), constant_values=np.nan)

            m_val = metric_fn(y_true, y_pred)
            fold_metrics.append({
                "fold": fi + 1,
                "train_end": tr_end,
                "test_start": tr_end,
                "test_end": te_end,
                "metric": m_val,
            })
            all_preds.append(y_pred)
            all_actuals.append(y_true)

        valid_metrics = [f["metric"] for f in fold_metrics if not np.isnan(f.get("metric", float("nan")))]
        mean_m = float(np.mean(valid_metrics)) if valid_metrics else float("nan")
        std_m = float(np.std(valid_metrics)) if valid_metrics else float("nan")

        if not valid_metrics:
            return ToolResult(
                data={
                    "fold_metrics": fold_metrics,
                    "mean_metric": mean_m,
                    "std_metric": std_m,
                    "n_folds": len(folds),
                    "n_successful": 0,
                    "metric_name": metric.lower(),
                    "all_predictions": [p.tolist() for p in all_preds],
                    "all_actuals": [a.tolist() for a in all_actuals],
                    "initial_train": initial_train,
                    "step": step,
                    "test_len": test_len,
                },
                success=False,
                error="all walk-forward folds failed",
                metadata={
                    "metric": metric.lower(),
                    "n_folds": len(folds),
                    "n_successful": 0,
                },
            )

        return ToolResult(
            data={
                "fold_metrics": fold_metrics,
                "mean_metric": mean_m,
                "std_metric": std_m,
                "n_folds": len(folds),
                "n_successful": len(valid_metrics),
                "metric_name": metric.lower(),
                "all_predictions": [p.tolist() for p in all_preds],
                "all_actuals": [a.tolist() for a in all_actuals],
                "initial_train": initial_train,
                "step": step,
                "test_len": test_len,
            },
            metadata={
                "metric": metric.lower(),
                "n_folds": len(folds),
                "mean": mean_m,
                "std": std_m,
                "initial_train": initial_train,
                "step": step,
                "test_len": test_len,
            },
        )
    except Exception as e:
        return ToolResult(data=None, success=False, error=str(e))


# ============================================
# Prophet + 残差模型 Walk-Forward 调参
# ============================================

def walk_forward_prophet_residual(
    df: pd.DataFrame,
    date_col: str,
    value_col: str,
    residual_model: str = "xgboost",
    initial_train: int = 912,
    step: int = 90,
    test_len: int = 90,
    metric: str = "mape",
    param_grid: Optional[list[dict]] = None,
) -> ToolResult:
    """
    Prophet + 残差模型 Walk-Forward 验证 + 超参搜索

    对每组超参跑完整 WF，返回平均指标最优的配置。

    Parameters
    ----------
    residual_model : "xgboost" | "lightgbm"
    param_grid : 超参列表，每项是一个 dict，如
        [{"max_depth": 3, "learning_rate": 0.02}, {"max_depth": 4, "learning_rate": 0.05}]
        为 None 时使用内置优化配置网格

    Returns
    -------
    ToolResult.data = {
        "best_config": dict,
        "best_mean_metric": float,
        "best_std_metric": float,
        "all_configs": [{"config": dict, "mean": float, "std": float, "fold_metrics": list}],
        "n_folds": int,
        "metric_name": str,
    }
    """
    try:
        from .prophet_residual import (
            forecast_prophet_xgboost,
            forecast_prophet_lightgbm,
        )

        if residual_model == "xgboost":
            fn = forecast_prophet_xgboost
            default_grid = [
                {"max_depth": 3, "learning_rate": 0.02, "n_estimators": 1000, "reg_lambda": 5.0, "min_child_weight": 20},
                {"max_depth": 3, "learning_rate": 0.05, "n_estimators": 500,  "reg_lambda": 5.0, "min_child_weight": 20},
                {"max_depth": 4, "learning_rate": 0.01, "n_estimators": 1500, "reg_lambda": 8.0, "min_child_weight": 15},
                {"max_depth": 4, "learning_rate": 0.05, "n_estimators": 500,  "reg_lambda": 5.0, "min_child_weight": 15},
            ]
        elif residual_model == "lightgbm":
            fn = forecast_prophet_lightgbm
            default_grid = [
                {"max_depth": 3, "num_leaves": 7,  "learning_rate": 0.02, "n_estimators": 800,  "reg_lambda": 10.0, "min_child_samples": 20},
                {"max_depth": 4, "num_leaves": 7,  "learning_rate": 0.01, "n_estimators": 1000, "reg_lambda": 20.0, "min_child_samples": 30},
                {"max_depth": 4, "num_leaves": 7,  "learning_rate": 0.005, "n_estimators": 2000, "reg_lambda": 20.0, "min_child_samples": 30},
                {"max_depth": 5, "num_leaves": 15, "learning_rate": 0.01, "n_estimators": 1000, "reg_lambda": 15.0, "min_child_samples": 20},
            ]
        else:
            return ToolResult(
                data=None, success=False,
                error=f"unknown residual_model: {residual_model}, use 'xgboost' or 'lightgbm'",
            )

        grid = param_grid if param_grid is not None else default_grid

        all_results = []
        for cfg in grid:
            wf_result = walk_forward_validate(
                df, date_col, value_col,
                forecast_fn=fn,
                initial_train=initial_train,
                step=step,
                test_len=test_len,
                metric=metric,
                forecast_kwargs=cfg,
            )
            if wf_result.success:
                mean_val = wf_result.data["mean_metric"]
                if mean_val is None or (isinstance(mean_val, float) and np.isnan(mean_val)):
                    continue
                all_results.append({
                    "config": cfg,
                    "mean": mean_val,
                    "std": wf_result.data["std_metric"],
                    "fold_metrics": wf_result.data["fold_metrics"],
                    "n_successful": wf_result.data["n_successful"],
                })

        if not all_results:
            return ToolResult(
                data=None, success=False,
                error="all configurations failed",
            )

        # 按平均指标排序（越小越好）
        all_results.sort(key=lambda x: x["mean"])
        best = all_results[0]

        return ToolResult(
            data={
                "best_config": best["config"],
                "best_mean_metric": best["mean"],
                "best_std_metric": best["std"],
                "all_configs": all_results,
                "n_folds": best["n_successful"],
                "metric_name": metric.lower(),
                "residual_model": residual_model,
                "initial_train": initial_train,
                "step": step,
                "test_len": test_len,
            },
            metadata={
                "best_mean": best["mean"],
                "best_std": best["std"],
                "n_configs": len(all_results),
                "n_folds": best["n_successful"],
                "metric": metric.lower(),
                "residual_model": residual_model,
            },
        )
    except Exception as e:
        return ToolResult(data=None, success=False, error=str(e))
