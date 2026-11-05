from ..contracts import AlgorithmFamily, AlgorithmMetadata, ValidationIssue, ValidationResult
from ._history import HistoryAlgorithm


class EwmaAlgorithm(HistoryAlgorithm):
    """指数加权移动平均：给予近期观测更高权重，适合平稳序列的短期预测。"""

    metadata = AlgorithmMetadata("EWMA_V1", "指数加权移动平均", AlgorithmFamily.BASELINE)

    def get_default_params(self):
        """返回指数平滑系数的默认值。"""
        return {"alpha": 0.3}

    def validate_params(self, params):
        """校验平滑系数 alpha 是否位于 (0, 1]。"""
        result = super().validate_params(params)
        alpha = result.normalized_params.get("alpha")
        if result.valid and (not isinstance(alpha, (int, float)) or not 0 < alpha <= 1):
            return ValidationResult(
                False,
                result.normalized_params,
                [ValidationIssue("INVALID_ALPHA", "alpha必须在(0, 1]内", "alpha")],
            )
        return result

    def _next(self, history, params):
        """计算整个历史序列的指数加权水平，作为下一期预测。"""
        level = history[0]
        for value in history[1:]:
            level = params["alpha"] * value + (1 - params["alpha"]) * level
        return level
