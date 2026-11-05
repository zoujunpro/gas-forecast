import numpy as np

from ..base import BaseAlgorithm
from ..contracts import AlgorithmCapabilities, AlgorithmModel


class StatisticalAlgorithm(BaseAlgorithm):
    def get_capabilities(self):
        """声明统计时序算法的通用能力。"""
        return AlgorithmCapabilities(
            supports_features=False, supports_prediction_interval=True, minimum_training_rows=20
        )

    def _target(self, data):
        """校验训练输入并提取不含非有限值的目标序列。"""
        self.ensure_valid_input(data, "TRAIN")
        values = data.target.to_numpy(dtype=float)
        if not np.isfinite(values).all():
            raise ValueError("统计时序算法不接受缺失target")
        return values

    def _model(self, payload, feature_names=None, metadata=None):
        """将第三方统计模型包装成统一的 AlgorithmModel。"""
        return AlgorithmModel(
            self.metadata.code, self.metadata.version, payload, feature_names or [], metadata or {}
        )
