from __future__ import annotations

from ..contracts import AlgorithmFamily, AlgorithmKind, AlgorithmMetadata
from ._legacy_residual import LegacyResidualComposite, LegacyResidualPayload


class ProphetLightgbmResidualAlgorithm(LegacyResidualComposite):
    """旧v3组合：优先使用LightGBM修正Prophet残差，失败时回退AR(4)。"""

    metadata = AlgorithmMetadata(
        "PROPHET_LIGHTGBM_RESIDUAL_V1",
        "Prophet+LightGBM残差",
        AlgorithmFamily.COMPOSITE,
        AlgorithmKind.COMPOSITE,
    )

    def residual_codes(self):
        return ("LIGHTGBM_V1", "AUTOREGRESSION_V1")

    def get_default_params(self):
        return {
            "prophet_params": {},
            "lightgbm_params": {},
            "ar_params": {"lags": 4},
            "minimum_lightgbm_rows": 40,
            "residual_feature_policy": "PASSTHROUGH_V1",
            "oof_params": {"folds": 3, "minimum_train_size": 30},
            "residual_clip": {
                "enabled": True,
                "lower_quantile": 0.01,
                "upper_quantile": 0.99,
            },
        }

    def fit(self, train_data, params):
        """训练Prophet后尝试LightGBM残差，数据不足或失败时训练AR残差。"""
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
        code = "LIGHTGBM_V1"
        name = "lightgbm"
        member_params = normalized["lightgbm_params"]
        residual_model = None
        if len(residual_data.features) >= int(normalized["minimum_lightgbm_rows"]):
            try:
                _, residual_model = self._fit_residual(code, residual_data, residual, member_params)
            except Exception:
                residual_model = None
        if residual_model is None:
            code = "AUTOREGRESSION_V1"
            name = "ar"
            _, residual_model = self._fit_residual(
                code, residual_data, residual, normalized["ar_params"]
            )
        residual_prediction = self._prediction_on(code, residual_model, residual_data)
        payload = LegacyResidualPayload(
            base_model,
            {name: residual_model},
            {name: 1.0},
            self._sigma(residual, residual_prediction),
            self._clip_bounds(residual, normalized),
            train_data.target.to_numpy(dtype=float),
            normalized["residual_feature_policy"],
        )
        return self._build_model(payload, normalized)
