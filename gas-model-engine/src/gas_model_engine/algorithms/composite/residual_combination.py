from ..contracts import (
    AlgorithmFamily,
    AlgorithmKind,
    AlgorithmMetadata,
    PredictionResult,
    ValidationIssue,
)
from ._base import CompositeAlgorithm, CompositePayload


class ResidualCombinationAlgorithm(CompositeAlgorithm):
    """残差组合：使用第二个模型学习基础模型残差，并将两部分预测相加。"""

    metadata = AlgorithmMetadata(
        "RESIDUAL_COMBINATION_V1", "残差组合", AlgorithmFamily.COMPOSITE, AlgorithmKind.COMPOSITE
    )

    def get_default_params(self):
        """返回基础算法、残差算法和成员列表的默认配置。"""
        return {"members": [], "base": None, "residual": None}

    def validate_params(self, params):
        """规范 base/residual 简写，并确保组合恰好包含两个成员。"""
        merged = {**self.get_default_params(), **dict(params or {})}
        if merged["base"] and merged["residual"] and not merged["members"]:
            merged["members"] = [merged["base"], merged["residual"]]
        result = super().validate_params(merged)
        if len(result.normalized_params.get("members", [])) != 2:
            result.errors.append(
                ValidationIssue("INVALID_MEMBERS", "残差组合必须包含base和residual两个成员")
            )
            result.valid = False
        return result

    def fit(self, train_data, params):
        """先训练基础模型，再使用基础模型残差训练第二个模型。"""
        normalized = self.normalized_params(params)
        base_spec, residual_spec = normalized["members"]
        base_algorithm = self.registry.get(base_spec["code"])
        base_model = base_algorithm.fit(train_data, base_spec.get("params", {}))
        base_prediction = base_algorithm.predict(base_model, train_data).values
        if len(base_prediction) != len(train_data.target):
            raise ValueError("基础算法必须支持对训练日期生成拟合值；否则应由回测层提供残差训练数据")
        residual_algorithm = self.registry.get(residual_spec["code"])
        residual_model = residual_algorithm.fit(
            train_data.with_target(train_data.target.to_numpy() - base_prediction),
            residual_spec.get("params", {}),
        )
        return self._model(
            CompositePayload(
                {
                    base_spec.get("id", "base"): base_model,
                    residual_spec.get("id", "residual"): residual_model,
                }
            ),
            normalized,
        )

    def predict(self, model, future_data):
        """将基础预测和残差预测相加得到最终结果。"""
        predictions = self._member_predictions(model.payload.member_models, future_data)
        return PredictionResult(sum(predictions.values()), metadata={"members": list(predictions)})
