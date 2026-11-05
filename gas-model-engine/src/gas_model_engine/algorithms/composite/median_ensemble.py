import numpy as np

from ..contracts import AlgorithmFamily, AlgorithmKind, AlgorithmMetadata, PredictionResult
from ._base import CompositeAlgorithm, CompositePayload


class MedianEnsembleAlgorithm(CompositeAlgorithm):
    """中位数组合：逐预测点取成员结果中位数，降低极端预测值的影响。"""

    metadata = AlgorithmMetadata(
        "MEDIAN_ENSEMBLE_V1", "中位数组合", AlgorithmFamily.COMPOSITE, AlgorithmKind.COMPOSITE
    )

    def fit(self, train_data, params):
        """训练中位数组合配置中的全部成员算法。"""
        normalized = self.normalized_params(params)
        return self._model(
            CompositePayload(self._fit_members(train_data, normalized["members"])), normalized
        )

    def predict(self, model, future_data):
        """逐预测点计算所有成功成员预测的中位数。"""
        predictions = self._member_predictions(model.payload.member_models, future_data)
        return PredictionResult(
            np.median(np.stack(list(predictions.values())), axis=0),
            metadata={"members": list(predictions)},
        )
