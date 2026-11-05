from ..contracts import AlgorithmFamily, AlgorithmMetadata, ValidationIssue, ValidationResult
from ._history import HistoryAlgorithm


class SeasonalNaiveAlgorithm(HistoryAlgorithm):
    """季节朴素预测：使用上一季节的同期观测值预测当前对应时期。"""

    metadata = AlgorithmMetadata("SEASONAL_NAIVE_V1", "季节朴素预测", AlgorithmFamily.BASELINE)

    def get_default_params(self):
        """返回默认季节周期。"""
        return {"period": 7}

    def validate_params(self, params):
        """校验季节周期必须为正整数。"""
        result = super().validate_params(params)
        period = result.normalized_params.get("period")
        if result.valid and (not isinstance(period, int) or period < 1):
            return ValidationResult(
                False,
                result.normalized_params,
                [ValidationIssue("INVALID_PERIOD", "period必须是正整数", "period")],
            )
        return result

    def _validate_history(self, history, params):
        """确保历史数据至少覆盖一个完整季节周期。"""
        if len(history) < params["period"]:
            raise ValueError(f"季节朴素预测至少需要{params['period']}条数据")

    def _next(self, history, params):
        """使用上一个季节的同期值作为下一期预测。"""
        return history[-params["period"]]
