import numpy as np

from ..contracts import AlgorithmFamily, AlgorithmMetadata
from .seasonal_naive import SeasonalNaiveAlgorithm


class SeasonalMeanAlgorithm(SeasonalNaiveAlgorithm):
    """季节同期均值：对多个历史季节的同期值求均值，以减弱单季异常影响。"""

    metadata = AlgorithmMetadata("SEASONAL_MEAN_V1", "季节同期均值", AlgorithmFamily.BASELINE)

    def get_default_params(self):
        """返回季节周期和参与平均的历史季节数量。"""
        return {"period": 36, "seasons": 2}

    def _validate_history(self, history, params):
        """确保历史数据覆盖指定数量的完整季节。"""
        required = params["period"] * params["seasons"]
        if len(history) < required:
            raise ValueError(f"季节同期均值至少需要{required}条数据")

    def _next(self, history, params):
        """计算多个历史季节同期值的平均数。"""
        return np.mean(
            [history[-params["period"] * index] for index in range(1, params["seasons"] + 1)]
        )
