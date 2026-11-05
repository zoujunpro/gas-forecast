from __future__ import annotations

from dataclasses import dataclass, field
from enum import StrEnum
from typing import Any

from ..algorithms.contracts import AlgorithmDataset, AlgorithmModel, PredictionResult


@dataclass
class ProfileModelBundle:
    profile_code: str
    profile_version: str
    candidate_models: dict[str, AlgorithmModel]
    failed_candidates: dict[str, str] = field(default_factory=dict)


@dataclass(frozen=True)
class ProfileTrainRequest:
    profile_code: str
    train_data: AlgorithmDataset
    candidate_ids: tuple[str, ...] | None = None


@dataclass(frozen=True)
class ProfilePredictRequest:
    model_bundle: ProfileModelBundle
    future_data: AlgorithmDataset


@dataclass
class ProfilePredictionResult:
    profile_code: str
    predictions: dict[str, PredictionResult]
    final_prediction: PredictionResult
    failed_candidates: dict[str, str] = field(default_factory=dict)


class PipelineStage(StrEnum):
    FEATURE_PREPARATION = "FEATURE_PREPARATION"
    BACKTEST = "BACKTEST"
    SELECTION = "SELECTION"
    TRAINING = "TRAINING"
    PREDICTION = "PREDICTION"
    COMBINATION = "COMBINATION"


class PipelineStageStatus(StrEnum):
    COMPLETED = "COMPLETED"
    PENDING = "PENDING"
    SKIPPED = "SKIPPED"


@dataclass(frozen=True)
class PipelineStageResult:
    stage: PipelineStage
    status: PipelineStageStatus
    message: str


@dataclass(frozen=True)
class BacktestResult:
    """回测扩展点的标准输出；OOF 将供选型和学习型组合使用。"""

    candidate_metrics: dict[str, dict[str, float]]
    member_oof_predictions: dict[str, Any] = field(default_factory=dict)
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class ForecastRequest:
    profile_code: str
    train_data: AlgorithmDataset
    future_data: AlgorithmDataset


@dataclass
class ForecastResult:
    profile_code: str
    model_bundle: ProfileModelBundle
    prediction: ProfilePredictionResult
    selected_candidate_ids: tuple[str, ...]
    stages: list[PipelineStageResult]
    backtest: BacktestResult | None = None
