"""可嵌入其他业务项目的冬供旬预测基础包。"""

from .artifacts import ModelArtifact, SavedArtifact, load_artifact, save_artifact
from .schemas import PredictionResult, TrainResult
from .service import predict, train
from .version import ARTIFACT_VERSION, MODEL_VERSION

__all__ = [
    "ModelArtifact",
    "MODEL_VERSION",
    "PredictionResult",
    "SavedArtifact",
    "TrainResult",
    "ARTIFACT_VERSION",
    "load_artifact",
    "predict",
    "save_artifact",
    "train",
]

__version__ = MODEL_VERSION
