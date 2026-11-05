from ..contracts import AlgorithmFamily, AlgorithmMetadata
from ._history import HistoryAlgorithm


class NaiveAlgorithm(HistoryAlgorithm):
    """朴素预测：直接使用最近一期观测值预测后续各期，适合作为基准模型。"""

    metadata = AlgorithmMetadata("NAIVE_V1", "朴素预测", AlgorithmFamily.BASELINE)

    def _next(self, history, params):
        """使用最近一期观测值作为下一期预测值。"""
        return history[-1]
