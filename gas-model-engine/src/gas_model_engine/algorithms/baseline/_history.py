from typing import Any

import numpy as np

from ..base import BaseAlgorithm
from ..contracts import AlgorithmCapabilities, AlgorithmDataset, AlgorithmModel, PredictionResult


class HistoryAlgorithm(BaseAlgorithm):
    def get_capabilities(self):
        """声明历史递推算法只依赖目标历史值，不使用外生特征。"""
        return AlgorithmCapabilities(supports_features=False, minimum_training_rows=1)

    def fit(self, train_data: AlgorithmDataset, params: dict[str, Any]):
        """校验并保存目标历史序列，生成后续递推预测所需的模型。"""
        self.ensure_valid_input(train_data, "TRAIN")
        normalized = self.normalized_params(params)
        values = train_data.target.to_numpy(dtype=float)
        if not np.isfinite(values).all():
            raise ValueError("target包含非有限数值")
        self._validate_history(values, normalized)
        return AlgorithmModel(
            self.metadata.code,
            self.metadata.version,
            values.tolist(),
            metadata={"params": normalized},
        )

    def predict(self, model, future_data):
        """循环调用 `_next` 逐期生成预测，并把预测值追加到历史序列。"""
        history = list(map(float, model.payload))
        params = dict(model.metadata["params"])
        predictions = []
        for _ in range(len(future_data.features)):
            value = float(self._next(history, params))
            predictions.append(value)
            history.append(value)
        return PredictionResult(np.asarray(predictions))

    def _validate_history(self, history, params):
        """校验历史序列是否满足具体算法要求，子类可按需覆盖。"""
        return

    def _next(self, history, params):
        """根据当前历史序列计算下一期预测值，由具体算法实现。"""
        raise NotImplementedError
