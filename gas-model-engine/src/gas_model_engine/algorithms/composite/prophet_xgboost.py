from __future__ import annotations

from ..contracts import AlgorithmFamily, AlgorithmKind, AlgorithmMetadata
from ._legacy_residual import LegacyResidualComposite, LegacyResidualPayload


class ProphetXgboostAlgorithm(LegacyResidualComposite):
    """Prophet基线加XGBoost残差修正；训练失败时由上层处理。"""

    metadata = AlgorithmMetadata(
        "PROPHET_XGBOOST_RESIDUAL_V1",
        "Prophet+XGBoost",
        AlgorithmFamily.COMPOSITE,
        AlgorithmKind.COMPOSITE,
    )

    def residual_codes(self):
        return ("XGBOOST_V1",)

    def get_default_params(self):
        return {
            "prophet_params": {},
            "xgboost_params": {},
            "residual_feature_policy": "TARGET_HISTORY_V1",
            "oof_params": {"folds": 3, "minimum_train_size": 30},
            "residual_clip": {
                "enabled": True,
                "lower_quantile": 0.01,
                "upper_quantile": 0.99,
            },
        }

    def fit(self, train_data, params):
        """训练XGBoost残差成员，不做隐式回退。"""
        normalized = self.normalized_params(params)
        base_algorithm, base_model = self._fit_base(train_data, normalized["prophet_params"])
        oof = self._build_oof_residual_data(
            train_data, base_algorithm, normalized["prophet_params"], normalized
        )
        residual_data = self._prepare_residual_features(
            normalized["residual_feature_policy"],
            oof.dataset,
            train_data.target,
            oof.source_indices,
        )
        residual = residual_data.target.to_numpy(dtype=float)
        code = "XGBOOST_V1"
        _, member = self._fit_residual(
            code, residual_data, residual, normalized["xgboost_params"]
        )
        prediction = self._prediction_on(code, member, residual_data)
        payload = LegacyResidualPayload(
            base_model,
            {"xgboost": member},
            {"xgboost": 1.0},
            self._sigma(residual, prediction),
            self._clip_bounds(residual, normalized),
            train_data.target.to_numpy(dtype=float),
            normalized["residual_feature_policy"],
        )
        return self._build_model(payload, normalized)
