from types import SimpleNamespace

import pytest

from gas_model_platform.models.contract import ModelContractError, validate_model_handler
from gas_model_platform.models.registry import MODEL_REGISTRY
from gas_model_platform.schemas.modeling import ModelInfo


def test_all_registered_models_satisfy_contract() -> None:
    for model_code, handler in MODEL_REGISTRY.items():
        validate_model_handler(model_code, handler)


def test_contract_rejects_handler_with_missing_method() -> None:
    handler = SimpleNamespace(
        info=ModelInfo(
            agent_code="short-term",
            model_code="MODEL_BROKEN_V1",
            model_version="1.0.0",
            model_name="不完整模型",
            description="用于验证契约校验。",
            capabilities=["train", "backtest", "predict"],
        ),
        train=lambda context: None,
        backtest=lambda context: None,
    )

    with pytest.raises(ModelContractError, match="predict"):
        validate_model_handler("MODEL_BROKEN_V1", handler)


def test_contract_rejects_non_semantic_version() -> None:
    handler = SimpleNamespace(
        info=ModelInfo(
            agent_code="short-term",
            model_code="MODEL_BROKEN_V1",
            model_version="V1",
            model_name="版本错误模型",
            description="用于验证版本校验。",
            capabilities=["train", "backtest", "predict"],
        ),
        train=lambda context: None,
        backtest=lambda context: None,
        predict=lambda context: None,
    )

    with pytest.raises(ModelContractError, match="x.y.z"):
        validate_model_handler("MODEL_BROKEN_V1", handler)


def test_contract_rejects_model_code_without_version() -> None:
    handler = SimpleNamespace(
        info=ModelInfo(
            agent_code="short-term",
            model_code="BROKEN_MODEL",
            model_version="1.0.0",
            model_name="编号错误模型",
            description="用于验证模型编号校验。",
            capabilities=["train", "backtest", "predict"],
        ),
        train=lambda context: None,
        backtest=lambda context: None,
        predict=lambda context: None,
    )

    with pytest.raises(ModelContractError, match="MODEL_<业务名>_V<主版本>"):
        validate_model_handler("BROKEN_MODEL", handler)
