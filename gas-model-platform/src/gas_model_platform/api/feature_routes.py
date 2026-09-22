"""模型特征计算接口。"""

from fastapi import APIRouter

from gas_model_platform.features.calculator import FeatureCalculator
from gas_model_platform.schemas.modeling import (
    FeatureBatchComputeRequest,
    FeatureBatchComputeResult,
    FeatureComputeRequest,
    FeatureComputeResult,
)
from gas_model_platform.schemas.response import ApiResponse

router = APIRouter(prefix="/features", tags=["features"])
feature_calculator = FeatureCalculator()


@router.post("/compute", response_model=ApiResponse[FeatureComputeResult])
def compute_features(request: FeatureComputeRequest) -> ApiResponse[FeatureComputeResult]:
    """计算单个目标日期的特征。

    根据智能体编号、目标日期、业务维度、输入字段和历史数据计算特征。
    """
    return ApiResponse.success(feature_calculator.compute_one(request))


@router.post("/batch-compute", response_model=ApiResponse[FeatureBatchComputeResult])
def compute_features_batch(
    request: FeatureBatchComputeRequest,
) -> ApiResponse[FeatureBatchComputeResult]:
    """批量计算多个目标日期的特征。

    每个目标日期返回一组特征，最多支持 366 个日期。
    """
    return ApiResponse.success(feature_calculator.compute_batch(request))
