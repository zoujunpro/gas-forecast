from dataclasses import dataclass
from typing import Any

import numpy as np

from ..base import BaseAlgorithm
from ..contracts import (
    AlgorithmCapabilities,
    AlgorithmModel,
    PredictionResult,
    ValidationIssue,
    ValidationResult,
)
from ..registry import AlgorithmRegistry


@dataclass
class CompositePayload:
    member_models: dict[str, AlgorithmModel]
    weights: dict[str, float] | None = None
    meta_model: Any = None


class CompositeAlgorithm(BaseAlgorithm):
    def __init__(self, registry: AlgorithmRegistry):
        """注入注册中心，用于解析和调用成员算法。"""
        self.registry = registry

    def get_capabilities(self):
        """声明组合算法通用的最小训练数据要求。"""
        return AlgorithmCapabilities(minimum_training_rows=3)

    def get_default_params(self):
        """返回组合算法默认的空成员列表。"""
        return {"members": []}

    def validate_params(self, params):
        """校验成员格式、成员编码和自身循环引用。"""
        result = super().validate_params(params)
        members = result.normalized_params.get("members")
        errors = list(result.errors)
        if not isinstance(members, list) or not members:
            errors.append(
                ValidationIssue("MEMBERS_REQUIRED", "组合算法至少需要一个成员算法", "members")
            )
        elif any(not isinstance(item, dict) or not item.get("code") for item in members):
            errors.append(ValidationIssue("INVALID_MEMBER", "每个成员必须包含code", "members"))
        elif any(item["code"] == self.metadata.code for item in members):
            errors.append(ValidationIssue("SELF_REFERENCE", "组合算法不能引用自身", "members"))
        return ValidationResult(not errors, result.normalized_params, errors, result.warnings)

    def dependencies(self, params=None):
        """返回组合参数中声明的成员算法编码。"""
        return tuple(item["code"] for item in (params or {}).get("members", []))

    def _fit_members(self, data, specs):
        """根据成员配置逐个解析并训练原子或组合算法。"""
        models = {}
        for spec in specs:
            member_id = spec.get("id", spec["code"])
            if member_id in models:
                raise ValueError(f"组合成员标识重复: {member_id}")
            models[member_id] = self.registry.get(spec["code"]).fit(data, spec.get("params", {}))
        return models

    def _member_predictions(self, models, data):
        """调用各成员模型生成预测，并按成员标识返回结果。"""
        return {
            key: self.registry.get(model.algorithm_code).predict(model, data).values
            for key, model in models.items()
        }

    def _model(self, payload, params):
        """将组合训练产物包装成统一模型对象。"""
        return AlgorithmModel(
            self.metadata.code, self.metadata.version, payload, metadata={"params": params}
        )


class OofWeightedAlgorithm(CompositeAlgorithm):
    def _oof(self, train_data, codes):
        """读取并校验成员算法的样本外预测矩阵。"""
        raw = train_data.metadata.get("member_oof_predictions")
        if not isinstance(raw, dict):
            raise TypeError("学习型组合需要metadata.member_oof_predictions")
        matrix = np.column_stack([np.asarray(raw[code], dtype=float) for code in codes])
        if train_data.target is None or len(matrix) != len(train_data.target):
            raise ValueError("OOF预测必须与target等长")
        return matrix, train_data.target.to_numpy(dtype=float)

    def fit(self, train_data, params):
        """根据 OOF 预测学习权重，然后重新训练所有成员模型。"""
        normalized = self.normalized_params(params)
        ids = [item.get("id", item["code"]) for item in normalized["members"]]
        matrix, actual = self._oof(train_data, ids)
        weights = np.maximum(np.asarray(self._weights(matrix, actual, normalized), dtype=float), 0)
        if weights.sum() <= 0:
            weights = np.ones(len(ids))
        weights /= weights.sum()
        models = self._fit_members(train_data, normalized["members"])
        return self._model(CompositePayload(models, dict(zip(ids, weights))), normalized)

    def predict(self, model, future_data):
        """使用已学习权重对成员预测进行加权汇总。"""
        predictions = self._member_predictions(model.payload.member_models, future_data)
        return PredictionResult(
            sum(model.payload.weights[key] * values for key, values in predictions.items()),
            metadata={"weights": model.payload.weights},
        )
