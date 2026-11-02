from __future__ import annotations

from dataclasses import dataclass
from typing import Any

import numpy as np
from scipy.optimize import nnls
from sklearn.linear_model import Ridge

from ..base import BaseAlgorithm
from ..contracts import (
    AlgorithmCapabilities, AlgorithmDataset, AlgorithmFamily, AlgorithmKind,
    AlgorithmMetadata, AlgorithmModel, PredictionResult, ValidationIssue,
    ValidationResult,
)
from ..registry import AlgorithmRegistry


@dataclass
class CompositePayload:
    member_models: dict[str, AlgorithmModel]
    weights: dict[str, float] | None = None
    meta_model: Any = None


class _CompositeAlgorithm(BaseAlgorithm):
    def __init__(self, registry: AlgorithmRegistry):
        self.registry = registry

    def get_capabilities(self):
        return AlgorithmCapabilities(minimum_training_rows=3)

    def get_default_params(self):
        return {"members": []}

    def validate_params(self, params):
        result = super().validate_params(params)
        members = result.normalized_params.get("members")
        errors = list(result.errors)
        if not isinstance(members, list) or not members:
            errors.append(ValidationIssue("MEMBERS_REQUIRED", "组合算法至少需要一个成员算法", "members"))
        elif any(not isinstance(item, dict) or not item.get("code") for item in members):
            errors.append(ValidationIssue("INVALID_MEMBER", "每个成员必须包含code", "members"))
        elif any(item["code"] == self.metadata.code for item in members):
            errors.append(ValidationIssue("SELF_REFERENCE", "组合算法不能引用自身", "members"))
        return ValidationResult(not errors, result.normalized_params, errors, result.warnings)

    def dependencies(self, params=None):
        return tuple(item["code"] for item in (params or {}).get("members", []))

    def _fit_members(self, data, specs):
        models = {}
        for spec in specs:
            algorithm = self.registry.get(spec["code"])
            member_id = spec.get("id", spec["code"])
            if member_id in models:
                raise ValueError(f"组合成员标识重复: {member_id}")
            models[member_id] = algorithm.fit(data, spec.get("params", {}))
        return models

    def _member_predictions(self, models, data):
        return {
            member_id: self.registry.get(model.algorithm_code).predict(model, data).values
            for member_id, model in models.items()
        }

    def _model(self, payload, params):
        return AlgorithmModel(self.metadata.code, self.metadata.version, payload, metadata={"params": params})


class WeightedAverageAlgorithm(_CompositeAlgorithm):
    metadata = AlgorithmMetadata("WEIGHTED_AVERAGE_V1", "加权平均组合", AlgorithmFamily.COMPOSITE, AlgorithmKind.COMPOSITE)

    def get_default_params(self):
        return {"members": [], "weights": None}

    def fit(self, train_data, params):
        normalized = self.normalized_params(params)
        models = self._fit_members(train_data, normalized["members"])
        member_ids = list(models)
        raw = normalized["weights"] or {member_id: 1.0 for member_id in member_ids}
        weights = np.asarray([float(raw.get(member_id, 0.0)) for member_id in member_ids])
        if np.any(weights < 0) or weights.sum() <= 0:
            raise ValueError("组合权重必须非负且总和大于0")
        weights /= weights.sum()
        return self._model(CompositePayload(models, dict(zip(member_ids, weights))), normalized)

    def predict(self, model, future_data):
        predictions = self._member_predictions(model.payload.member_models, future_data)
        values = sum(model.payload.weights[code] * values for code, values in predictions.items())
        return PredictionResult(values, metadata={"weights": model.payload.weights, "members": list(predictions)})


class MedianEnsembleAlgorithm(_CompositeAlgorithm):
    metadata = AlgorithmMetadata("MEDIAN_ENSEMBLE_V1", "中位数组合", AlgorithmFamily.COMPOSITE, AlgorithmKind.COMPOSITE)

    def fit(self, train_data, params):
        normalized = self.normalized_params(params)
        return self._model(CompositePayload(self._fit_members(train_data, normalized["members"])), normalized)

    def predict(self, model, future_data):
        predictions = self._member_predictions(model.payload.member_models, future_data)
        return PredictionResult(np.median(np.stack(list(predictions.values())), axis=0), metadata={"members": list(predictions)})


class WinsorizedAverageAlgorithm(MedianEnsembleAlgorithm):
    metadata = AlgorithmMetadata("WINSORIZED_AVERAGE_V1", "缩尾平均组合", AlgorithmFamily.COMPOSITE, AlgorithmKind.COMPOSITE)

    def get_default_params(self):
        return {"members": [], "trim_ratio": 0.1}

    def predict(self, model, future_data):
        predictions = self._member_predictions(model.payload.member_models, future_data)
        matrix = np.sort(np.stack(list(predictions.values())), axis=0)
        count = matrix.shape[0]
        trim = min(int(np.floor(count * model.metadata["params"]["trim_ratio"])), max(0, count // 2 - 1))
        if trim:
            matrix[:trim] = matrix[trim]
            matrix[-trim:] = matrix[-trim - 1]
        return PredictionResult(matrix.mean(axis=0), metadata={"members": list(predictions), "trim_count": trim})


class _OofWeightedAlgorithm(_CompositeAlgorithm):
    """权重只能由外部Walk-forward产生的OOF预测学习。"""

    def _oof(self, train_data, codes):
        raw = train_data.metadata.get("member_oof_predictions")
        if not isinstance(raw, dict):
            raise ValueError("学习型组合需要metadata.member_oof_predictions")
        matrix = np.column_stack([np.asarray(raw[code], dtype=float) for code in codes])
        if train_data.target is None or len(matrix) != len(train_data.target):
            raise ValueError("OOF预测必须与target等长")
        return matrix, train_data.target.to_numpy(dtype=float)

    def _weights(self, matrix, actual, params):
        raise NotImplementedError

    def fit(self, train_data, params):
        normalized = self.normalized_params(params)
        member_ids = [item.get("id", item["code"]) for item in normalized["members"]]
        matrix, actual = self._oof(train_data, member_ids)
        weights = np.asarray(self._weights(matrix, actual, normalized), dtype=float)
        weights = np.maximum(weights, 0)
        if weights.sum() <= 0:
            weights = np.ones(len(member_ids))
        weights /= weights.sum()
        models = self._fit_members(train_data, normalized["members"])
        return self._model(CompositePayload(models, dict(zip(member_ids, weights))), normalized)

    def predict(self, model, future_data):
        predictions = self._member_predictions(model.payload.member_models, future_data)
        values = sum(model.payload.weights[code] * values for code, values in predictions.items())
        return PredictionResult(values, metadata={"weights": model.payload.weights})


class InverseErrorWeightAlgorithm(_OofWeightedAlgorithm):
    metadata = AlgorithmMetadata("INVERSE_ERROR_WEIGHT_V1", "误差倒数加权", AlgorithmFamily.COMPOSITE, AlgorithmKind.COMPOSITE)

    def get_default_params(self):
        return {"members": [], "metric": "wmape", "epsilon": 1e-8}

    def _weights(self, matrix, actual, params):
        if params["metric"].lower() == "rmse":
            errors = np.sqrt(np.mean((matrix - actual[:, None]) ** 2, axis=0))
        else:
            errors = np.sum(np.abs(matrix - actual[:, None]), axis=0) / max(np.sum(np.abs(actual)), params["epsilon"])
        return 1.0 / np.maximum(errors, params["epsilon"])


class NnlsWeightAlgorithm(_OofWeightedAlgorithm):
    metadata = AlgorithmMetadata("NNLS_WEIGHT_V1", "非负最小二乘组合", AlgorithmFamily.COMPOSITE, AlgorithmKind.COMPOSITE)

    def _weights(self, matrix, actual, params):
        return nnls(matrix, actual)[0]


class RidgeStackingAlgorithm(_CompositeAlgorithm):
    metadata = AlgorithmMetadata("RIDGE_STACKING_V1", "Ridge Stacking", AlgorithmFamily.COMPOSITE, AlgorithmKind.COMPOSITE)

    def get_default_params(self):
        return {"members": [], "alpha": 1.0, "positive": False}

    def fit(self, train_data, params):
        normalized = self.normalized_params(params)
        member_ids = [item.get("id", item["code"]) for item in normalized["members"]]
        raw = train_data.metadata.get("member_oof_predictions")
        if not isinstance(raw, dict):
            raise ValueError("Stacking需要metadata.member_oof_predictions")
        matrix = np.column_stack([np.asarray(raw[member_id], dtype=float) for member_id in member_ids])
        meta_model = Ridge(alpha=normalized["alpha"], positive=normalized["positive"])
        meta_model.fit(matrix, train_data.target)
        models = self._fit_members(train_data, normalized["members"])
        return self._model(CompositePayload(models, meta_model=meta_model), normalized)

    def predict(self, model, future_data):
        predictions = self._member_predictions(model.payload.member_models, future_data)
        matrix = np.column_stack(list(predictions.values()))
        return PredictionResult(model.payload.meta_model.predict(matrix), metadata={"members": list(predictions)})


class ResidualCombinationAlgorithm(_CompositeAlgorithm):
    metadata = AlgorithmMetadata("RESIDUAL_COMBINATION_V1", "残差组合", AlgorithmFamily.COMPOSITE, AlgorithmKind.COMPOSITE)

    def get_default_params(self):
        return {"members": [], "base": None, "residual": None}

    def validate_params(self, params):
        merged = {**self.get_default_params(), **dict(params or {})}
        if merged["base"] and merged["residual"] and not merged["members"]:
            merged["members"] = [merged["base"], merged["residual"]]
        result = super().validate_params(merged)
        if len(result.normalized_params.get("members", [])) != 2:
            result.errors.append(ValidationIssue("INVALID_MEMBERS", "残差组合必须包含base和residual两个成员"))
            result.valid = False
        return result

    def fit(self, train_data, params):
        normalized = self.normalized_params(params)
        base_spec, residual_spec = normalized["members"]
        base_algorithm = self.registry.get(base_spec["code"])
        base_model = base_algorithm.fit(train_data, base_spec.get("params", {}))
        base_prediction = base_algorithm.predict(base_model, train_data).values
        if len(base_prediction) != len(train_data.target):
            raise ValueError("基础算法必须支持对训练日期生成拟合值；否则应由回测层提供残差训练数据")
        residual_data = train_data.with_target(train_data.target.to_numpy() - base_prediction)
        residual_algorithm = self.registry.get(residual_spec["code"])
        residual_model = residual_algorithm.fit(residual_data, residual_spec.get("params", {}))
        payload = CompositePayload({
            base_spec.get("id", "base"): base_model,
            residual_spec.get("id", "residual"): residual_model,
        })
        return self._model(payload, normalized)

    def predict(self, model, future_data):
        predictions = self._member_predictions(model.payload.member_models, future_data)
        return PredictionResult(sum(predictions.values()), metadata={"members": list(predictions)})


def composite_algorithms(registry: AlgorithmRegistry) -> list[BaseAlgorithm]:
    return [
        WeightedAverageAlgorithm(registry), MedianEnsembleAlgorithm(registry),
        WinsorizedAverageAlgorithm(registry), InverseErrorWeightAlgorithm(registry),
        NnlsWeightAlgorithm(registry), RidgeStackingAlgorithm(registry),
        ResidualCombinationAlgorithm(registry),
    ]
