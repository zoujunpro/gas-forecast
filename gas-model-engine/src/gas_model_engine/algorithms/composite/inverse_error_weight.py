import numpy as np

from ..contracts import AlgorithmFamily, AlgorithmKind, AlgorithmMetadata
from ._base import OofWeightedAlgorithm


class InverseErrorWeightAlgorithm(OofWeightedAlgorithm):
    """误差倒数加权：根据成员 OOF 误差的倒数自动分配组合权重。"""

    metadata = AlgorithmMetadata(
        "INVERSE_ERROR_WEIGHT_V1",
        "误差倒数加权",
        AlgorithmFamily.COMPOSITE,
        AlgorithmKind.COMPOSITE,
    )

    def get_default_params(self):
        """返回误差指标和防止除零的默认参数。"""
        return {"members": [], "metric": "wmape", "epsilon": 1e-8}

    def _weights(self, matrix, actual, params):
        """根据各成员 OOF 误差的倒数计算原始权重。"""
        if params["metric"].lower() == "rmse":
            errors = np.sqrt(np.mean((matrix - actual[:, None]) ** 2, axis=0))
        else:
            errors = np.sum(np.abs(matrix - actual[:, None]), axis=0) / max(
                np.sum(np.abs(actual)), params["epsilon"]
            )
        return 1.0 / np.maximum(errors, params["epsilon"])
