from ..contracts import AlgorithmFamily, AlgorithmMetadata
from .seasonal_naive import SeasonalNaiveAlgorithm


class SeasonalTrendAlgorithm(SeasonalNaiveAlgorithm):
    """季节趋势预测：在上一季节同期值基础上叠加跨季趋势修正。"""

    metadata = AlgorithmMetadata("SEASONAL_TREND_V1", "季节趋势预测", AlgorithmFamily.BASELINE)

    def get_default_params(self):
        """返回季节周期和趋势修正权重。"""
        return {"period": 36, "trend_weight": 0.5}

    def _validate_history(self, history, params):
        """确保历史数据至少覆盖两个完整季节。"""
        if len(history) < params["period"] * 2:
            raise ValueError(f"季节趋势预测至少需要{params['period'] * 2}条数据")

    def _next(self, history, params):
        """使用同期值，并根据相邻季节变化趋势进行修正。"""
        recent = history[-params["period"]]
        previous = history[-params["period"] * 2]
        return recent + params["trend_weight"] * (recent - previous)
