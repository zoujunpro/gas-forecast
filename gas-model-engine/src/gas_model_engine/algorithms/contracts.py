from __future__ import annotations

from dataclasses import dataclass, field
from enum import StrEnum
from typing import Any

import numpy as np
import pandas as pd


class AlgorithmFamily(StrEnum):
    BASELINE = "BASELINE"
    STATISTICAL = "STATISTICAL"
    LINEAR = "LINEAR"
    TREE = "TREE"
    COMPOSITE = "COMPOSITE"


class AlgorithmKind(StrEnum):
    ATOMIC = "ATOMIC"
    COMPOSITE = "COMPOSITE"


@dataclass(frozen=True)
class AlgorithmCapabilities:
    trainable: bool = True
    supports_features: bool = True
    supports_prediction_interval: bool = False
    supports_sample_weight: bool = False
    supports_missing_values: bool = False
    supports_multi_step: bool = True
    minimum_training_rows: int = 1


@dataclass(frozen=True)
class AlgorithmMetadata:
    code: str
    name: str
    family: AlgorithmFamily
    kind: AlgorithmKind = AlgorithmKind.ATOMIC
    version: str = "1.0.0"
    description: str = ""


@dataclass
class AlgorithmDataset:
    features: pd.DataFrame
    dates: pd.DatetimeIndex
    target: pd.Series | None = None
    sample_weight: pd.Series | None = None
    metadata: dict[str, Any] = field(default_factory=dict)

    def __post_init__(self) -> None:
        self.features = pd.DataFrame(self.features).reset_index(drop=True)
        self.dates = pd.DatetimeIndex(pd.to_datetime(self.dates))
        if self.target is not None:
            self.target = pd.Series(self.target, dtype=float).reset_index(drop=True)
        if self.sample_weight is not None:
            self.sample_weight = pd.Series(self.sample_weight, dtype=float).reset_index(drop=True)

    def with_target(self, target: pd.Series | np.ndarray) -> "AlgorithmDataset":
        return AlgorithmDataset(
            features=self.features.copy(), dates=self.dates.copy(), target=pd.Series(target),
            sample_weight=self.sample_weight, metadata=dict(self.metadata),
        )


@dataclass
class AlgorithmModel:
    algorithm_code: str
    algorithm_version: str
    payload: Any
    feature_names: list[str] = field(default_factory=list)
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass
class PredictionResult:
    values: np.ndarray
    lower_bounds: np.ndarray | None = None
    upper_bounds: np.ndarray | None = None
    metadata: dict[str, Any] = field(default_factory=dict)

    def __post_init__(self) -> None:
        self.values = np.asarray(self.values, dtype=float).reshape(-1)
        if self.lower_bounds is not None:
            self.lower_bounds = np.asarray(self.lower_bounds, dtype=float).reshape(-1)
        if self.upper_bounds is not None:
            self.upper_bounds = np.asarray(self.upper_bounds, dtype=float).reshape(-1)


@dataclass(frozen=True)
class ValidationIssue:
    code: str
    message: str
    field: str | None = None


@dataclass
class ValidationResult:
    valid: bool
    normalized_params: dict[str, Any] = field(default_factory=dict)
    errors: list[ValidationIssue] = field(default_factory=list)
    warnings: list[ValidationIssue] = field(default_factory=list)

    @classmethod
    def success(cls, params: dict[str, Any] | None = None) -> "ValidationResult":
        return cls(valid=True, normalized_params=dict(params or {}))
