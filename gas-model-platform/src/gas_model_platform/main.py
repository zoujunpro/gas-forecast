import logging

from fastapi import FastAPI, Request
from fastapi.encoders import jsonable_encoder
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from gas_model_platform.api.v1.routes import router as v1_router
from gas_model_platform.core.config import settings
from gas_model_platform.core.logging import configure_logging
from gas_model_platform.core.request_logging import RequestResponseLoggingMiddleware
from gas_model_platform.schemas.response import ApiResponse

log_file = configure_logging()
logger = logging.getLogger(__name__)

app = FastAPI(title=settings.app_name, version=settings.app_version)
app.add_middleware(RequestResponseLoggingMiddleware)


def _localize_validation_message(error_type: object, message: object) -> str:
    translations = {
        "missing": "字段必填",
        "list_type": "字段必须是数组",
        "dict_type": "字段必须是对象",
        "string_type": "字段必须是字符串",
        "int_type": "字段必须是整数",
        "greater_than_equal": "字段值不能小于允许的最小值",
        "less_than_equal": "字段值不能大于允许的最大值",
        "too_short": "字段数据数量不足",
        "too_long": "字段数据数量超过限制",
        "literal_error": "字段值不在允许范围内",
    }
    return translations.get(str(error_type), str(message))


def _concise_validation_errors(exc: RequestValidationError) -> list[dict[str, object]]:
    """移除可能包含完整训练数据的 input，只返回定位错误所需信息。"""
    errors = []
    for item in exc.errors():
        errors.append(
            {
                "type": item.get("type"),
                "field": ".".join(str(part) for part in item.get("loc", ())),
                "message": _localize_validation_message(
                    item.get("type"), item.get("msg")
                ),
            }
        )
    return errors


@app.exception_handler(StarletteHTTPException)
async def http_exception_handler(
    request: Request,
    exc: StarletteHTTPException,
) -> JSONResponse:
    message = exc.detail if isinstance(exc.detail, str) else "request failed"
    data = None if isinstance(exc.detail, str) else exc.detail
    logger.warning(
        "http error method=%s path=%s status=%s message=%s",
        request.method,
        request.url.path,
        exc.status_code,
        message,
    )
    body = ApiResponse[object].failure(exc.status_code, message, data)
    return JSONResponse(
        status_code=exc.status_code,
        content=jsonable_encoder(body),
        headers=exc.headers,
    )


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(
    request: Request,
    exc: RequestValidationError,
) -> JSONResponse:
    errors = _concise_validation_errors(exc)
    message = errors[0]["message"] if errors else "request validation failed"
    logger.warning(
        "request validation failed method=%s path=%s errors=%s",
        request.method,
        request.url.path,
        errors,
    )
    body = ApiResponse[object].failure(
        422,
        str(message),
        {"errors": errors},
    )
    return JSONResponse(status_code=422, content=jsonable_encoder(body))


@app.exception_handler(Exception)
async def unhandled_exception_handler(request: Request, exc: Exception) -> JSONResponse:
    logger.exception(
        "unhandled request exception method=%s path=%s",
        request.method,
        request.url.path,
        exc_info=exc,
    )
    body = ApiResponse[object].failure(500, "internal server error")
    return JSONResponse(status_code=500, content=jsonable_encoder(body))


@app.get("/health", response_model=ApiResponse[dict[str, str]])
def health() -> ApiResponse[dict[str, str]]:
    return ApiResponse.success({"status": "ok", "service": settings.app_name})


app.include_router(v1_router, prefix="/api/v1")

logger.info(
    "application initialized name=%s version=%s profile=%s config_file=%s profile_config_file=%s log_file=%s",
    settings.app_name,
    settings.app_version,
    settings.profile,
    settings.config_path,
    settings.profile_config_path,
    log_file,
)


if __name__ == "__main__":
    import uvicorn

    logger.info(
        "starting http server host=%s port=%s reload=%s",
        settings.server_host,
        settings.server_port,
        settings.server_reload,
    )
    uvicorn.run(
        "gas_model_platform.main:app" if settings.server_reload else app,
        host=settings.server_host,
        port=settings.server_port,
        reload=settings.server_reload,
        log_config=None,
    )
