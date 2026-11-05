from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

from .algorithms import AlgorithmRegistry, create_default_registry
from .algorithms.contracts import (
    AlgorithmDataset,
    AlgorithmMetadata,
    AlgorithmModel,
    PredictionResult,
    ValidationResult,
)


@dataclass(frozen=True)
class AlgorithmTrainRequest:
    algorithm_code: str
    dataset: AlgorithmDataset
    params: dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class AlgorithmPredictRequest:
    model: AlgorithmModel
    dataset: AlgorithmDataset


class AlgorithmEngine:
    """Pipeline内部的单算法执行器，不作为上层业务入口。

    负责根据算法编码查找实现，并统一完成参数校验、输入校验、训练和预测。
    上层预测业务应调用 ``ForecastPipeline``，仅框架内部和算法单元测试直接使用本类。
    """

    def __init__(self, registry: AlgorithmRegistry | None = None) -> None:
        self.registry = registry or create_default_registry()

    def list_algorithms(self) -> list[AlgorithmMetadata]:
        return self.registry.list_metadata()

    def describe_algorithm(self, algorithm_code: str) -> dict[str, Any]:
        algorithm = self.registry.get(algorithm_code)
        return {
            "metadata": algorithm.metadata,
            "capabilities": algorithm.get_capabilities(),
            "default_params": algorithm.get_default_params(),
        }

    def validate(self, request: AlgorithmTrainRequest) -> ValidationResult:
        algorithm = self.registry.get(request.algorithm_code)
        parameter_result = algorithm.validate_params(request.params)
        if not parameter_result.valid:
            return parameter_result
        input_result = algorithm.validate_input(request.dataset, "TRAIN")
        input_result.normalized_params = parameter_result.normalized_params
        input_result.warnings = [*parameter_result.warnings, *input_result.warnings]
        return input_result

    def train(self, request: AlgorithmTrainRequest) -> AlgorithmModel:
        algorithm = self.registry.get(request.algorithm_code)
        validation = self.validate(request)
        if not validation.valid:
            messages = "; ".join(issue.message for issue in validation.errors)
            raise ValueError(messages)
        return algorithm.fit(request.dataset, validation.normalized_params)

    def predict(self, request: AlgorithmPredictRequest) -> PredictionResult:
        algorithm = self.registry.get(request.model.algorithm_code)
        validation = algorithm.validate_input(request.dataset, "PREDICT")
        if not validation.valid:
            messages = "; ".join(issue.message for issue in validation.errors)
            raise ValueError(messages)
        return algorithm.predict(request.model, request.dataset)
