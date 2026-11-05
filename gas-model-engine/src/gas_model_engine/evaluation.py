from __future__ import annotations

import numpy as np


def calculate_metrics(actual, predicted) -> dict[str, float]:
    actual = np.asarray(actual, dtype=float)
    predicted = np.asarray(predicted, dtype=float)
    if actual.shape != predicted.shape or actual.size == 0:
        raise ValueError("actual与predicted必须形状相同且不能为空")
    error = predicted - actual
    nonzero = np.abs(actual) > 1e-12
    mape = (
        float(np.mean(np.abs(error[nonzero] / actual[nonzero])) * 100)
        if nonzero.any()
        else float("inf")
    )
    denominator = max(float(np.sum(np.abs(actual))), 1e-12)
    return {
        "mae": float(np.mean(np.abs(error))),
        "rmse": float(np.sqrt(np.mean(error**2))),
        "mape": mape,
        "wmape": float(np.sum(np.abs(error)) / denominator * 100),
    }


def conformal_radius(actual, predicted, coverage: float = 0.8) -> float:
    """使用样本外绝对误差计算有限样本校准半径。"""
    if not 0 < coverage < 1:
        raise ValueError("预测区间覆盖率必须在0和1之间")
    actual = np.asarray(actual, dtype=float)
    predicted = np.asarray(predicted, dtype=float)
    if actual.shape != predicted.shape or actual.size == 0:
        raise ValueError("区间校准的actual与predicted必须形状相同且不能为空")
    errors = np.abs(actual - predicted)
    rank = min(int(np.ceil((len(errors) + 1) * coverage)), len(errors))
    return float(np.sort(errors)[rank - 1])
