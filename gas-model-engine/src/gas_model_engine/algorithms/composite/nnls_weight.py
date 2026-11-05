from scipy.optimize import nnls

from ..contracts import AlgorithmFamily, AlgorithmKind, AlgorithmMetadata
from ._base import OofWeightedAlgorithm


class NnlsWeightAlgorithm(OofWeightedAlgorithm):
    """非负最小二乘组合：从 OOF 预测中学习非负成员权重并归一化。"""

    metadata = AlgorithmMetadata(
        "NNLS_WEIGHT_V1", "非负最小二乘组合", AlgorithmFamily.COMPOSITE, AlgorithmKind.COMPOSITE
    )

    def _weights(self, matrix, actual, params):
        """通过非负最小二乘计算成员组合权重。"""
        return nnls(matrix, actual)[0]
