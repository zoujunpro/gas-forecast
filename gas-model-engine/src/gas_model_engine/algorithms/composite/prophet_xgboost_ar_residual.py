from __future__ import annotations

import numpy as np

from ..contracts import AlgorithmFamily, AlgorithmKind, AlgorithmMetadata
from ._legacy_residual import LegacyResidualComposite, LegacyResidualPayload


class ProphetXgboostArResidualAlgorithm(LegacyResidualComposite):
    """旧v2组合：XGBoost与AR残差按训练误差倒数加权，再修正Prophet。"""

    metadata = AlgorithmMetadata(
        "PROPHET_XGBOOST_AR_RESIDUAL_V1",
        "Prophet+XGBoost/AR残差融合",
        AlgorithmFamily.COMPOSITE,
        AlgorithmKind.COMPOSITE,
    )

    def residual_codes(self):
        return ("XGBOOST_V1", "AUTOREGRESSION_V1")

    def get_default_params(self):
        return {
            "prophet_params": {},
            "xgboost_params": {},
            "ar_params": {"lags": 4},
            "residual_feature_policy": "TARGET_HISTORY_V1",
            "oof_params": {"folds": 3, "minimum_train_size": 30},
            "residual_clip": {
                "enabled": True,
                "lower_quantile": 0.01,
                "upper_quantile": 0.99,
            },
        }

    def fit(self, train_data, params):
        """训练两个残差成员，根据残差RMSE倒数计算归一化融合权重。"""
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
        models = {}
        predictions = {}
        failures = {}
        for name, code, key in (
            ("xgboost", "XGBOOST_V1", "xgboost_params"),
            ("ar", "AUTOREGRESSION_V1", "ar_params"),
        ):
            try:
                _, member = self._fit_residual(code, residual_data, residual, normalized[key])
                models[name] = member
                predictions[name] = self._prediction_on(code, member, residual_data)
            except Exception as exc:
                failures[name] = str(exc)
        if not models:
            raise RuntimeError(f"旧v2的XGBoost和AR残差模型均训练失败: {failures}")
        inverse = {
            name: 1.0 / (float(np.sqrt(np.mean((values - residual) ** 2))) + 1e-8)
            for name, values in predictions.items()
        }
        total = sum(inverse.values())
        weights = {name: value / total for name, value in inverse.items()}
        combined = sum(weights[name] * predictions[name] for name in models)
        payload = LegacyResidualPayload(
            base_model,
            models,
            weights,
            self._sigma(residual, combined),
            self._clip_bounds(residual, normalized),
            train_data.target.to_numpy(dtype=float),
            normalized["residual_feature_policy"],
        )
        return self._build_model(payload, normalized)
