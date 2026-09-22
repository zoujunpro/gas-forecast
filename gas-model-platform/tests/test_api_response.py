import asyncio
import json
from types import SimpleNamespace

from fastapi.exceptions import RequestValidationError

from gas_model_platform.api.model_routes import list_models, validate_training_data
from gas_model_platform.main import health, validation_exception_handler
from gas_model_platform.schemas.modeling import TrainingDataValidationRequest


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
    response = list_models(None)

    payload = response.model_dump()
    assert payload["code"] == 0
    assert payload["message"] == "success"
    assert isinstance(payload["data"], list)
    assert payload["data"]
    for model in payload["data"]:
        assert model["agent_code"] in {
            "winter-supply",
            "monthly-sales",
            "short-term",
        }
        if "train" in model["capabilities"]:
            assert model["training_data_range"] is not None


def test_model_list_can_filter_by_model_code() -> None:
    response = list_models("MODEL_JIANGSHU_DIANLI_V1.0")

    payload = response.model_dump()
    assert len(payload["data"]) == 1
    assert payload["data"][0]["model_code"] == "MODEL_JIANGSHU_DIANLI_V1.0"


def test_training_data_validation_uses_model_specific_rules() -> None:
    response = validate_training_data(
        TrainingDataValidationRequest(
            model_code="MODEL_JIANGSHU_DIANLI_V1.0",
            dataset=[{"date": "2026-01-01", "gas_sales": 100.0}],
        )
    )

    payload = response.model_dump()
    assert payload["code"] == 0
    assert payload["data"]["valid"] is False
    assert payload["data"]["errors"][0]["code"] == "INSUFFICIENT_HISTORY"


def test_request_validation_error_uses_unified_response() -> None:
    error = RequestValidationError(
        [
            {
                "type": "missing",
                "loc": ("body", "model_code"),
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
