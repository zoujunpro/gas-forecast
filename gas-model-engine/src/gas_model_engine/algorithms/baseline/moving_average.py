import numpy as np

from ..contracts import AlgorithmFamily, AlgorithmMetadata, ValidationIssue, ValidationResult
from ._history import HistoryAlgorithm


class MovingAverageAlgorithm(HistoryAlgorithm):
    """移动平均预测：使用最近若干期的均值平滑短期波动并递推预测。"""

    metadata = AlgorithmMetadata("MOVING_AVERAGE_V1", "移动平均", AlgorithmFamily.BASELINE)

    def get_default_params(self):
        """返回移动平均窗口的默认长度。"""
        return {"window": 3}

    def validate_params(self, params):
        """校验移动平均窗口必须为正整数。"""
        result = super().validate_params(params)
        window = result.normalized_params.get("window")
        if result.valid and (not isinstance(window, int) or window < 1):
            return ValidationResult(
                False,
                result.normalized_params,
                [ValidationIssue("INVALID_WINDOW", "window必须是正整数", "window")],
            )
        return result

    def _next(self, history, params):
        """计算最近 `window` 期历史值的平均数。"""
        return np.mean(history[-params["window"] :])
