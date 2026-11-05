from __future__ import annotations

from abc import ABC, abstractmethod
from collections.abc import Mapping
from pathlib import Path
from typing import Any

import joblib

from .contracts import (
    AlgorithmCapabilities,
    AlgorithmDataset,
    AlgorithmMetadata,
    AlgorithmModel,
    PredictionResult,
    ValidationIssue,
    ValidationResult,
)


class BaseAlgorithm(ABC):
    """原子算法和组合算法共同遵守的最小公共协议。"""

    @property
    @abstractmethod
    def metadata(self) -> AlgorithmMetadata:
        """返回算法编码、名称、技术簇、类型和实现版本。"""
        raise NotImplementedError

    @abstractmethod
    def fit(self, train_data: AlgorithmDataset, params: dict[str, Any]) -> AlgorithmModel:
        """使用标准训练数据和算法参数训练并返回统一模型对象。"""
        raise NotImplementedError

    @abstractmethod
    def predict(self, model: AlgorithmModel, future_data: AlgorithmDataset) -> PredictionResult:
        """使用训练模型和未来特征生成统一预测结果。"""
        raise NotImplementedError

    def save(self, model: AlgorithmModel, path: str | Path) -> None:
        """将统一模型对象持久化到指定路径。"""
        destination = Path(path)
        destination.parent.mkdir(parents=True, exist_ok=True)
        joblib.dump(model, destination)

    def load(self, path: str | Path) -> AlgorithmModel:
        """加载模型文件，并校验模型类型和算法编码。"""
        model = joblib.load(Path(path))
        if not isinstance(model, AlgorithmModel):
            raise TypeError("文件不是AlgorithmModel产物")
        if model.algorithm_code != self.metadata.code:
            raise ValueError(f"模型产物属于{model.algorithm_code}，不能由{self.metadata.code}加载")
        return model

    def get_default_params(self) -> dict[str, Any]:
        """返回算法默认训练参数，具体算法可覆盖。"""
        return {}

    def validate_params(self, params: dict[str, Any] | None) -> ValidationResult:
        """校验调用方参数，并与默认参数合并。"""
        if params is not None and not isinstance(params, Mapping):
            return ValidationResult(
                valid=False,
                errors=[ValidationIssue("INVALID_PARAMS", "算法参数必须是对象")],
            )
        normalized = {**self.get_default_params(), **dict(params or {})}
        return ValidationResult.success(normalized)

    def validate_input(self, data: AlgorithmDataset, stage: str) -> ValidationResult:
        """校验训练或预测数据的长度、目标、日期及最小样本数。"""
        errors: list[ValidationIssue] = []
        row_count = len(data.features)
        if len(data.dates) != row_count:
            errors.append(ValidationIssue("LENGTH_MISMATCH", "dates与features行数不一致"))
        if stage.upper() == "TRAIN":
            if data.target is None:
                errors.append(ValidationIssue("TARGET_REQUIRED", "训练数据必须包含target"))
            elif len(data.target) != row_count:
                errors.append(ValidationIssue("LENGTH_MISMATCH", "target与features行数不一致"))
            if row_count < self.get_capabilities().minimum_training_rows:
                errors.append(
                    ValidationIssue(
                        "INSUFFICIENT_ROWS",
                        f"至少需要{self.get_capabilities().minimum_training_rows}条训练数据",
                    )
                )
        if data.dates.has_duplicates:
            errors.append(ValidationIssue("DUPLICATE_DATES", "日期不能重复"))
        return ValidationResult(valid=not errors, errors=errors)

    def get_capabilities(self) -> AlgorithmCapabilities:
        """返回算法能力声明，具体算法簇可覆盖。"""
        return AlgorithmCapabilities()

    def dependencies(self, params: dict[str, Any] | None = None) -> tuple[str, ...]:
        """返回算法依赖的其他算法编码；原子算法默认无依赖。"""
        return ()

    def normalized_params(self, params: dict[str, Any] | None) -> dict[str, Any]:
        """返回校验并补齐默认值后的参数，校验失败时抛出异常。"""
        result = self.validate_params(params)
        if not result.valid:
            messages = "; ".join(issue.message for issue in result.errors)
            raise ValueError(messages)
        return result.normalized_params

    def ensure_valid_input(self, data: AlgorithmDataset, stage: str) -> None:
        """执行输入校验，并将校验错误转换为异常。"""
        result = self.validate_input(data, stage)
        if not result.valid:
            messages = "; ".join(issue.message for issue in result.errors)
            raise ValueError(messages)
