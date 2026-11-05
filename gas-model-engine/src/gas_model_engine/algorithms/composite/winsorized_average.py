import numpy as np

from ..contracts import AlgorithmFamily, AlgorithmKind, AlgorithmMetadata, PredictionResult
from ._base import CompositeAlgorithm, CompositePayload


class WinsorizedAverageAlgorithm(CompositeAlgorithm):
    """缩尾平均组合：限制两端极端成员预测后取平均，提高组合稳健性。"""

    metadata = AlgorithmMetadata(
        "WINSORIZED_AVERAGE_V1", "缩尾平均组合", AlgorithmFamily.COMPOSITE, AlgorithmKind.COMPOSITE
    )

    def get_default_params(self):
        """返回成员列表和默认缩尾比例。"""
        return {"members": [], "trim_ratio": 0.1}

    def fit(self, train_data, params):
        """训练缩尾平均组合中的全部成员算法。"""
        normalized = self.normalized_params(params)
        return self._model(
            CompositePayload(self._fit_members(train_data, normalized["members"])), normalized
        )

    def predict(self, model, future_data):
        """对成员预测逐点缩尾后计算平均值。"""
        predictions = self._member_predictions(model.payload.member_models, future_data)
        matrix = np.sort(np.stack(list(predictions.values())), axis=0)
        count = matrix.shape[0]
        trim = min(
            int(np.floor(count * model.metadata["params"]["trim_ratio"])), max(0, count // 2 - 1)
        )
        if trim:
            matrix[:trim] = matrix[trim]
            matrix[-trim:] = matrix[-trim - 1]
        return PredictionResult(
            matrix.mean(axis=0), metadata={"members": list(predictions), "trim_count": trim}
        )
