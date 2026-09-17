import asyncio
import json
from types import SimpleNamespace

from fastapi.exceptions import RequestValidationError

from gas_model_platform.api.v1.model_routes import list_models
from gas_model_platform.main import health, validation_exception_handler


def test_health_uses_unified_response() -> None:
    response = health()

    assert response.model_dump() == {
        "code": 0,
        "message": "success",
        "data": {
            "status": "ok",
            "service": "gas-model-platform",
        },
    }


def test_model_list_uses_unified_response() -> None:
    response = list_models()

    payload = response.model_dump()
    assert payload["code"] == 0
    assert payload["message"] == "success"
    assert isinstance(payload["data"], list)


def test_request_validation_error_uses_unified_response() -> None:
    error = RequestValidationError(
        [
            {
                "type": "missing",
                "loc": ("body", "modelCode"),
                "msg": "Field required",
                "input": {},
            }
        ]
    )
    request = SimpleNamespace(method="POST", url=SimpleNamespace(path="/api/v1/train"))
    response = asyncio.run(validation_exception_handler(request, error))

    payload = json.loads(response.body)
    assert response.status_code == 422
    assert payload["code"] == 422
    assert payload["message"] == "字段必填"
    assert isinstance(payload["data"]["errors"], list)
    assert "input" not in payload["data"]["errors"][0]
