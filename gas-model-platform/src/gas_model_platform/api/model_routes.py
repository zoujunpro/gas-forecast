"""模型查询、数据校验、训练、回测和预测接口。"""

from collections.abc import Callable
import logging
from typing import NoReturn, TypeVar

from fastapi import APIRouter, HTTPException

from gas_model_platform.models.registry import registry
from gas_model_platform.schemas.modeling import (
    BacktestRequest,
    BacktestResult,
    ModelInfo,
    PredictRequest,
    PredictResult,
    TrainingDataValidationRequest,
    TrainingDataValidationResult,
    TrainRequest,
    TrainResult,
)
from gas_model_platform.schemas.response import ApiResponse
from gas_model_platform.services.runtime import ModelRuntime

router = APIRouter(tags=["models"])
runtime = ModelRuntime(registry)
logger = logging.getLogger(__name__)
ResultT = TypeVar("ResultT")


def _raise_model_http_error(exc: Exception) -> NoReturn:
    """把模型适配器的预期异常转换成稳定的 HTTP 状态码。"""
    if isinstance(exc, (KeyError, FileNotFoundError)):
        logger.warning("model operation not found error=%s", exc)
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    if isinstance(exc, (ValueError, TypeError)):
        logger.warning("model operation rejected error=%s", exc)
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    logger.exception("model operation failed")
    raise HTTPException(status_code=500, detail=str(exc)) from exc


def _execute(operation: Callable[[], ResultT]) -> ResultT:
    try:
        return operation()
    except (KeyError, FileNotFoundError, ValueError, TypeError, RuntimeError) as exc:
        _raise_model_http_error(exc)


@router.get("/models", response_model=ApiResponse[list[ModelInfo]])
def list_models(model_code: str | None = None) -> ApiResponse[list[ModelInfo]]:
    """查询已注册模型；传入 model_code 时只返回对应模型。"""
    if model_code is None:
        return ApiResponse.success(registry.list_models())
    handler = _execute(lambda: registry.resolve(model_code))
    return ApiResponse.success([handler.info])


@router.post(
    "/models/validate-training-data",
    response_model=ApiResponse[TrainingDataValidationResult],
)
def validate_training_data(
    request: TrainingDataValidationRequest,
) -> ApiResponse[TrainingDataValidationResult]:
    """使用目标模型的规则校验用户选择的训练数据。"""
    result = _execute(lambda: runtime.validate_training_data(request))
    return ApiResponse.success(result)


@router.post("/train", response_model=ApiResponse[TrainResult])
def train(request: TrainRequest) -> ApiResponse[TrainResult]:
    """按 model_code 找到模型处理器并执行训练。"""
    logger.info("model train started model_code=%s region=%s", request.model_code, request.region_code or request.region_name)
    result = _execute(lambda: runtime.train(request))
    logger.info("model train completed model_code=%s batch_no=%s", result.model_code, result.train_batch_no)
    return ApiResponse.success(result)


@router.post("/backtest", response_model=ApiResponse[BacktestResult])
def backtest(request: BacktestRequest) -> ApiResponse[BacktestResult]:
    """按 model_code 找到模型处理器并执行回测。"""
    logger.info("model backtest started model_code=%s region=%s", request.model_code, request.region_code or request.region_name)
    result = _execute(lambda: runtime.backtest(request))
    logger.info("model backtest completed model_code=%s points=%d", result.model_code, len(result.points))
    return ApiResponse.success(result)


@router.post("/predict", response_model=ApiResponse[PredictResult])
def predict(request: PredictRequest) -> ApiResponse[PredictResult]:
    """按 model_code 找到模型处理器并执行预测。"""
    logger.info("model predict started model_code=%s region=%s", request.model_code, request.region_code or request.region_name)
    result = _execute(lambda: runtime.predict(request))
    logger.info("model predict completed model_code=%s batch_no=%s points=%d", result.model_code, result.forecast_batch_no, len(result.points))
    return ApiResponse.success(result)
