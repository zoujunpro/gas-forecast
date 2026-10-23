"""
评估工具 — 多维度预测效果评估

支持指标：
- MAE, MSE, RMSE
- MAPE, sMAPE
- MASE (需要季节性周期)
- 方向准确率
- 分时段表现
"""

from __future__ import annotations

import json
from typing import Optional

import numpy as np
import pandas as pd

from ..base import ToolResult


def _coerce_array(values, name: str) -> np.ndarray:
    """
    把评估入参转成一维 float 数组。

    LLM 常把列表传成 '[262.5, 264.2]' 或 '[0,0,0]' 字符串，直接 np.asarray(..., dtype=float) 会报错。
    """
    if values is None:
        raise ValueError(f"{name} 为空")
    if isinstance(values, str):
        text = values.strip()
        if not text:
            raise ValueError(f"{name} 为空")
        if text[0] not in "[(":
            text = f"[{text}]"
        text = text.replace("(", "[").replace(")", "]").replace("'", '"')
        try:
            values = json.loads(text)
        except json.JSONDecodeError as exc:
            raise ValueError(f"{name} 不是合法数组: {values!r}") from exc
    arr = np.asarray(values, dtype=float).reshape(-1)
    if arr.size == 0:
        raise ValueError(f"{name} 为空")
    return arr


# ============================================
# 核心评估函数
# ============================================

def evaluate(
    y_true: np.ndarray,
    y_pred: np.ndarray,
    y_train: Optional[np.ndarray] = None,
    period: int = 7,
) -> ToolResult:
    """
    多指标评估预测效果

    参数：
        y_true: 真实值
        y_pred: 预测值
        y_train: 训练集数据（用于 MASE）
        period: 季节性周期（用于 MASE）

    返回：
        各种指标的字典
    """
    try:
        y_true = _coerce_array(y_true, "y_true")
        y_pred = _coerce_array(y_pred, "y_pred")
        if y_train is not None:
            y_train = _coerce_array(y_train, "y_train")

        if len(y_true) != len(y_pred):
            return ToolResult(
                data=None,
                success=False,
                error=f"真实值与预测值长度不一致: {len(y_true)} != {len(y_pred)}",
            )

        # 去除 NaN / inf
        mask = np.isfinite(y_true) & np.isfinite(y_pred)
        y_true = y_true[mask]
        y_pred = y_pred[mask]

        if len(y_true) == 0:
            return ToolResult(
                data=None,
                success=False,
                error="没有有效数据点",
            )

        # 误差
        errors = y_true - y_pred
        abs_errors = np.abs(errors)
        sq_errors = errors ** 2

        # 基础误差指标
        mae = float(np.mean(abs_errors))
        mse = float(np.mean(sq_errors))
        rmse = float(np.sqrt(mse))

        # 百分比误差
        # 避免除零保护：真值接近 0 的点不计入 MAPE
        mape_mask = np.abs(y_true) > 1e-8
        if mape_mask.sum() > 0:
            mape = float(np.mean(abs_errors[mape_mask] / np.abs(y_true[mape_mask])) * 100)
        else:
            mape = float("inf")

        smape_denom = np.abs(y_true) + np.abs(y_pred)
        smape_mask = smape_denom > 1e-8
        if smape_mask.sum() > 0:
            smape = float(np.mean(
                2 * abs_errors[smape_mask] / smape_denom[smape_mask]
            ) * 100)
        else:
            smape = 0.0

        # 方向准确率
        if len(y_true) < 2:
            direction_acc = None
        else:
            true_diff = np.diff(y_true)
            pred_diff = np.diff(y_pred)
            direction_correct = (true_diff * pred_diff) > 0
            direction_acc = float(np.mean(direction_correct) * 100)

        # MASE（平均绝对标度误差）
        mase = None
        if y_train is not None and period > 0 and len(y_train) > period:
            y_train_arr = np.asarray(y_train, dtype=float)
            # 季节朴素预测误差：与 period 个时间步之前的观测比较
            naive_errors = np.abs(y_train_arr[period:] - y_train_arr[:-period])
            if len(naive_errors) > 0:
                mae_naive = np.mean(naive_errors)
                if mae_naive > 1e-10:
                    mase = float(mae / mae_naive)

        # 偏差（平均偏差百分比）
        bias_pct = float(np.mean(errors / np.where(np.abs(y_true) > 1e-8, y_true, 1)) * 100)

        metrics = {
            "mae": mae,
            "mse": mse,
            "rmse": rmse,
            "mape": mape,
            "smape": smape,
            "bias_pct": bias_pct,
            "direction_acc": direction_acc,
            "mase": mase,
        }

        # 简洁总结
        summary = (
            f"RMSE={rmse:.2f}, MAPE={mape:.2f}%, "
            f"MAE={mae:.2f}"
        )
        if mase is not None:
            summary += f", MASE={mase:.3f}"
        if direction_acc is not None:
            summary += f", 方向准确率={direction_acc:.1f}%"

        return ToolResult(
            data=metrics,
            metadata={
                "n_points": len(y_true),
                "summary": summary,
            },
        )

    except Exception as e:
        return ToolResult(data=None, success=False, error=str(e))


# ============================================
# 回测报告
# ============================================

def backtest_report(
    y_true: np.ndarray,
    y_pred: np.ndarray,
    dates: Optional[np.ndarray] = None,
    n_segments: int = 4,
) -> ToolResult:
    """
    回测报告 — 分时段表现分析

    把测试集分成多段，分别评估，观察表现是否随时间退化。
    """
    try:
        y_true = np.asarray(y_true, dtype=float)
        y_pred = np.asarray(y_pred, dtype=float)
        n = len(y_true)

        segments = []
        seg_size = n // n_segments

        for i in range(n_segments):
            start = i * seg_size
            end = (i + 1) * seg_size if i < n_segments - 1 else n
            seg_true = y_true[start:end]
            seg_pred = y_pred[start:end]

            seg_rmse = float(np.sqrt(np.nanmean((seg_true - seg_pred) ** 2)))
            seg_mape = _safe_mape(seg_true, seg_pred)

            segments.append({
                "segment": i + 1,
                "start_idx": start,
                "end_idx": end,
                "rmse": seg_rmse,
                "mape": seg_mape,
                "n_points": end - start,
            })

        overall_mape = _safe_mape(y_true, y_pred)
        overall_rmse = float(np.sqrt(np.nanmean((y_true - y_pred) ** 2)))

        # 趋势：后期表现稳定性（最后一段比第一段差多少）
        if len(segments) >= 2:
            first_mape = segments[0]["mape"]
            last_mape = segments[-1]["mape"]
            degradation = last_mape - first_mape
            if first_mape > 0:
                degradation_pct = (last_mape - first_mape) / first_mape * 100
            else:
                degradation_pct = 0
        else:
            degradation = 0
            degradation_pct = 0

        report = {
            "overall_rmse": overall_rmse,
            "overall_mape": overall_mape,
            "segments": segments,
            "degradation_abs": degradation,
            "degradation_pct": degradation_pct,
            "is_stable": abs(degradation_pct) < 20,  # 20%以内算稳定
            "n_points": n,
        }

        return ToolResult(
            data=report,
            metadata={
                "overall_rmse": overall_rmse,
                "overall_mape": overall_mape,
                "n_segments": n_segments,
            },
        )

    except Exception as e:
        return ToolResult(data=None, success=False, error=str(e))


# ============================================
#多模型对比
# ============================================

def compare_models(
    results: dict[str, dict],
    metric: str = "rmse",
) -> ToolResult:
    """
    多模型对比排名

    results: {模型名: {rmse: ..., mape: ..., ...}
    返回按指定指标排序的对比表
    """
    try:
        rows = []
        for name, metrics in results.items():
            row = {"model": name, **metrics}
            rows.append(row)

        # 按指标排序（越小越好）
        rows.sort(key=lambda r: r.get(metric, float("inf")))

        best = rows[0]
        ranking = []
        for i, row in enumerate(rows):
            r = dict(row)
            r["rank"] = i + 1
            if i > 0 and best.get(metric, float("inf")) > 0:
                improvement = (best[metric] - row[metric]) / best[metric] * 100
                r["improvement_vs_best_pct"] = improvement
            else:
                r["improvement_vs_best_pct"] = 0.0
            ranking.append(r)

        return ToolResult(
            data={
                "ranking": ranking,
                "best_model": best["model"],
                "best_metric": best.get(metric),
                "metric": metric,
                "n_models": len(rows),
            },
            metadata={"best": best["model"], "metric": metric},
        )

    except Exception as e:
        return ToolResult(data=None, success=False, error=str(e))


def _safe_mape(y_true: np.ndarray, y_pred: np.ndarray) -> float:
    """安全计算 MAPE，忽略真值为零时的处理"""
    valid = np.abs(y_true) > 1e-8
    if valid.sum() == 0:
        return float("inf")
    return float(np.mean(np.abs(y_true[valid] - y_pred[valid]) / np.abs(y_true[valid])) * 100)
