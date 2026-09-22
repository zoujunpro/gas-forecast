from __future__ import annotations

from dataclasses import dataclass, field
from hashlib import sha256
from pathlib import Path
from typing import Any, Dict, List

import joblib

from .forecasting import FittedForecastModel
from .version import ARTIFACT_VERSION, MODEL_VERSION


@dataclass
class ModelArtifact:
    """训练与预测之间传递的模型产物。"""

    # 可选业务元数据；模型由 model_code + train_batch_no 定位，不依赖省份。
    province: str | None
    best_model: str
    models: Dict[str, FittedForecastModel]
    features: List[str]
    weights: Dict[str, Any]
    residual_lower: float
    residual_upper: float
    train_start: str
    train_end: str
    metadata: Dict[str, Any] = field(default_factory=dict)
    model_version: str = MODEL_VERSION
    artifact_version: str = ARTIFACT_VERSION


@dataclass
class SavedArtifact:
    path: str
    sha256: str
    model_version: str
    artifact_version: str


def save_artifact(artifact: ModelArtifact, path: str | Path) -> SavedArtifact:
    """保存模型文件并返回路径和摘要；路径由业务项目指定。"""
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(artifact, target)
    digest = sha256(target.read_bytes()).hexdigest()
    return SavedArtifact(
        str(target),
        digest,
        artifact.model_version,
        artifact.artifact_version,
    )


def load_artifact(path: str | Path) -> ModelArtifact:
    artifact = joblib.load(Path(path))
    if not isinstance(artifact, ModelArtifact):
        raise TypeError("文件不是冬季保供智能体的模型产物")
    if artifact.artifact_version != ARTIFACT_VERSION:
        raise ValueError(
            f"不支持的模型产物版本: {artifact.artifact_version}"
        )
    # 兼容增加 model_version 字段以前生成的 1.0 格式产物。
    if not hasattr(artifact, "model_version"):
        artifact.model_version = MODEL_VERSION
    return artifact
