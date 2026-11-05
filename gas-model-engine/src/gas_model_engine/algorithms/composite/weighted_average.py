import numpy as np

from ..contracts import AlgorithmFamily, AlgorithmKind, AlgorithmMetadata, PredictionResult
from ._base import CompositeAlgorithm, CompositePayload


class WeightedAverageAlgorithm(CompositeAlgorithm):
    """加权平均组合：使用调用方指定或默认等权的权重汇总多个成员预测。"""

    metadata = AlgorithmMetadata(
        "WEIGHTED_AVERAGE_V1", "加权平均组合", AlgorithmFamily.COMPOSITE, AlgorithmKind.COMPOSITE
    )

    def get_default_params(self):
        """返回成员列表和可选固定权重的默认配置。"""
        return {"members": [], "weights": None}

    def fit(self, train_data, params):
        """训练成员算法，并归一化调用方提供的固定权重。"""
        normalized = self.normalized_params(params)
        models = self._fit_members(train_data, normalized["members"])
        ids = list(models)
        raw = normalized["weights"] or {key: 1.0 for key in ids}
        weights = np.asarray([float(raw.get(key, 0.0)) for key in ids])
        if np.any(weights < 0) or weights.sum() <= 0:
            raise ValueError("组合权重必须非负且总和大于0")
        weights /= weights.sum()
        return self._model(CompositePayload(models, dict(zip(ids, weights))), normalized)

    def predict(self, model, future_data):
        """按照训练阶段确定的权重汇总成员预测。"""
        predictions = self._member_predictions(model.payload.member_models, future_data)
        return PredictionResult(
            sum(model.payload.weights[key] * values for key, values in predictions.items()),
            metadata={"weights": model.payload.weights, "members": list(predictions)},
        )
