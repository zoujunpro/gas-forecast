from __future__ import annotations

from dataclasses import dataclass

import numpy as np

from ...backtesting import WalkForwardOOFResidualBuilder
from ..base import BaseAlgorithm
from ..contracts import AlgorithmDataset, AlgorithmModel, PredictionResult
from ._base import CompositeAlgorithm
from ._residual_feature_policy import get_residual_feature_policy


@dataclass
class LegacyResidualPayload:
    """旧短期客户残差组合的基础模型、残差模型、权重和区间误差。"""

    base_model: AlgorithmModel
    residual_models: dict[str, AlgorithmModel]
    weights: dict[str, float]
    residual_sigma: float
    residual_clip_bounds: tuple[float, float] | None = None
    target_history: np.ndarray | None = None
    residual_feature_policy: str = "PASSTHROUGH_V1"


class LegacyResidualComposite(CompositeAlgorithm):
    """旧短期客户残差组合公共实现：基础预测加残差预测。"""

    base_code = "PROPHET_V1"

    def __init__(self, registry, oof_builder=None):
        super().__init__(registry)
        self.oof_builder = oof_builder or WalkForwardOOFResidualBuilder()

    def validate_params(self, params):
        """固定结构残差组合不要求调用方再声明通用members列表。"""
        return BaseAlgorithm.validate_params(self, params)

    def dependencies(self, params=None):
        """由具体组合声明固定依赖，供注册中心检查。"""
        return (self.base_code, *self.residual_codes())

    def residual_codes(self) -> tuple[str, ...]:
        raise NotImplementedError

    def _fit_base(self, train_data, params):
        algorithm = self.registry.get(self.base_code)
        model = algorithm.fit(train_data, params)
        return algorithm, model

    def _build_oof_residual_data(self, train_data, base_algorithm, base_params, params):
        """公共Walk-forward构造基础模型样本外残差，供残差成员训练。"""
        return self.oof_builder.build(
            train_data,
            base_algorithm,
            base_params,
            params.get("oof_params"),
        )

    def _fit_residual(self, code, train_data, residual, params):
        algorithm = self.registry.get(code)
        model = algorithm.fit(train_data.with_target(residual), params)
        return algorithm, model

    @staticmethod
    def _prepare_residual_features(policy_code, residual_data, full_target, source_indices):
        """委托特征策略准备训练数据，公共组合流程不再写死Lag/Rolling。"""
        return get_residual_feature_policy(policy_code).prepare_training(
            residual_data, full_target, source_indices
        )

    def _prediction_on(self, code, model, data):
        return self.registry.get(code).predict(model, data).values

    def _build_model(self, payload, params):
        return AlgorithmModel(
            self.metadata.code,
            self.metadata.version,
            payload,
            metadata={"params": params, "residual_training_mode": "WALK_FORWARD_OOF"},
        )

    def _combine_prediction(self, model, future_data):
        payload = model.payload
        base_result = self.registry.get(payload.base_model.algorithm_code).predict(
            payload.base_model, future_data
        )
        residual_future_data = AlgorithmDataset(
            features=future_data.features.copy(),
            dates=future_data.dates,
            metadata=dict(future_data.metadata),
        )
        for name, values in base_result.metadata.get("components", {}).items():
            residual_future_data.features[name] = np.asarray(values, dtype=float)
        residual = np.zeros(len(base_result.values), dtype=float)
        policy = get_residual_feature_policy(payload.residual_feature_policy)
        for name, residual_model in payload.residual_models.items():
            member_prediction = policy.predict(
                self,
                residual_model,
                residual_future_data,
                base_result.values,
                payload.target_history,
                payload.residual_clip_bounds,
            )
            residual += payload.weights[name] * member_prediction
        unclipped_residual = residual.copy()
        if payload.residual_clip_bounds is not None:
            residual = np.clip(residual, *payload.residual_clip_bounds)
        values = base_result.values + residual
        widen = float(model.payload.residual_sigma) * 1.2816
        lower = None
        upper = None
        if base_result.lower_bounds is not None:
            lower = base_result.lower_bounds + residual - widen
        if base_result.upper_bounds is not None:
            upper = base_result.upper_bounds + residual + widen
        return PredictionResult(
            values,
            lower,
            upper,
            metadata={
                "base": payload.base_model.algorithm_code,
                "residual_members": list(payload.residual_models),
                "residual_weights": payload.weights,
                "residual_feature_policy": payload.residual_feature_policy,
                "residual_clip_bounds": payload.residual_clip_bounds,
                "residual_clipped_count": int(
                    np.count_nonzero(~np.isclose(unclipped_residual, residual))
                ),
            },
        )

    @staticmethod
    def _sigma(actual_residual, predicted_residual):
        remaining = np.asarray(actual_residual) - np.asarray(predicted_residual)
        return float(np.nanstd(remaining)) if len(remaining) else 0.0

    @staticmethod
    def _clip_bounds(residual, params):
        """从OOF真实残差分布学习裁剪边界，避免把固定销量阈值写进算法。"""
        config = dict(params.get("residual_clip", {}))
        if not bool(config.get("enabled", True)):
            return None
        lower_quantile = float(config.get("lower_quantile", 0.01))
        upper_quantile = float(config.get("upper_quantile", 0.99))
        if not 0 <= lower_quantile < upper_quantile <= 1:
            raise ValueError("残差裁剪分位数必须满足0 <= lower < upper <= 1")
        values = np.asarray(residual, dtype=float)
        return (
            float(np.quantile(values, lower_quantile)),
            float(np.quantile(values, upper_quantile)),
        )

    def predict(self, model, future_data):
        """返回基础预测与加权残差预测之和，并同步调整预测区间。"""
        return self._combine_prediction(model, future_data)
