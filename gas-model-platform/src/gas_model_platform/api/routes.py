"""路由聚合入口。

特征接口和模型接口分别维护在独立模块；这里仅负责组合并保留原导入路径，
因此 main.py 和已有调用方无需调整。
"""

from fastapi import APIRouter

from gas_model_platform.api.feature_routes import (
    compute_features,
    compute_features_batch,
    router as feature_router,
)
from gas_model_platform.api.model_routes import (
    backtest,
    list_models,
    predict,
    router as model_router,
    train,
    validate_training_data,
)

router = APIRouter()
router.include_router(feature_router)
router.include_router(model_router)

__all__ = [
    "backtest",
    "compute_features",
    "compute_features_batch",
    "list_models",
    "predict",
    "router",
    "train",
    "validate_training_data",
]
