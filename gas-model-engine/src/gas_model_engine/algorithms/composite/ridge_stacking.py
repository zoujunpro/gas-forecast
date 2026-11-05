import numpy as np
from sklearn.linear_model import Ridge

from ..contracts import AlgorithmFamily, AlgorithmKind, AlgorithmMetadata, PredictionResult
from ._base import CompositeAlgorithm, CompositePayload


class RidgeStackingAlgorithm(CompositeAlgorithm):
    """Ridge Stacking：以成员 OOF 预测为二层特征训练岭回归元模型。"""

    metadata = AlgorithmMetadata(
        "RIDGE_STACKING_V1", "Ridge Stacking", AlgorithmFamily.COMPOSITE, AlgorithmKind.COMPOSITE
    )

    def get_default_params(self):
        """返回 Ridge 元模型的正则化和正系数配置。"""
        return {"members": [], "alpha": 1.0, "positive": False}

    def fit(self, train_data, params):
        """使用成员 OOF 预测训练 Ridge 元模型，并训练成员最终模型。"""
        normalized = self.normalized_params(params)
        ids = [item.get("id", item["code"]) for item in normalized["members"]]
        raw = train_data.metadata.get("member_oof_predictions")
        if not isinstance(raw, dict):
            raise TypeError("Stacking需要metadata.member_oof_predictions")
        meta_model = Ridge(alpha=normalized["alpha"], positive=normalized["positive"])
        meta_model.fit(
            np.column_stack([np.asarray(raw[key], dtype=float) for key in ids]), train_data.target
        )
        return self._model(
            CompositePayload(
                self._fit_members(train_data, normalized["members"]), meta_model=meta_model
            ),
            normalized,
        )

    def predict(self, model, future_data):
        """将成员预测组成二层特征并交给 Ridge 元模型预测。"""
        predictions = self._member_predictions(model.payload.member_models, future_data)
        return PredictionResult(
            model.payload.meta_model.predict(np.column_stack(list(predictions.values()))),
            metadata={"members": list(predictions)},
        )
