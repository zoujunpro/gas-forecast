from __future__ import annotations

from ..contracts import AlgorithmFamily, AlgorithmKind, AlgorithmMetadata
from ._legacy_residual import LegacyResidualComposite, LegacyResidualPayload


class ProphetArAlgorithm(LegacyResidualComposite):
    """旧v1组合：Prophet预测主体，AR(4)学习并修正Prophet历史残差。"""

    metadata = AlgorithmMetadata(
        "PROPHET_AR_RESIDUAL_V1",
        "Prophet+AR残差",
        AlgorithmFamily.COMPOSITE,
        AlgorithmKind.COMPOSITE,
    )

    def residual_codes(self):
        return ("AUTOREGRESSION_V1",)

    def get_default_params(self):
        return {
            "prophet_params": {},
            "ar_params": {"lags": 4},
            "residual_feature_policy": "PASSTHROUGH_V1",
            "oof_params": {"folds": 3, "minimum_train_size": 30},
            "residual_clip": {
                "enabled": True,
                "lower_quantile": 0.01,
                "upper_quantile": 0.99,
            },
        }

    def fit(self, train_data, params):
        """依次训练Prophet和AR残差模型，并估计残差修正后的区间宽度。"""
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
        _, residual_model = self._fit_residual(
            "AUTOREGRESSION_V1", residual_data, residual, normalized["ar_params"]
        )
        residual_prediction = self._prediction_on(
            "AUTOREGRESSION_V1", residual_model, residual_data
        )
        payload = LegacyResidualPayload(
            base_model,
            {"ar": residual_model},
            {"ar": 1.0},
            self._sigma(residual, residual_prediction),
            self._clip_bounds(residual, normalized),
            train_data.target.to_numpy(dtype=float),
            normalized["residual_feature_policy"],
        )
        return self._build_model(payload, normalized)
