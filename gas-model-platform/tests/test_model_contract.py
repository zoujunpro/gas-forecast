from types import SimpleNamespace

import pytest

from gas_model_platform.models.contract import ModelContractError, validate_model_handler
from gas_model_platform.models.registry import MODEL_REGISTRY
from gas_model_platform.schemas.modeling import ModelInfo


def test_all_registered_models_satisfy_contract() -> None:
    for model_code, handler in MODEL_REGISTRY.items():
        validate_model_handler(model_code, handler)


def test_all_trainable_models_expose_training_data_range() -> None:
    for handler in MODEL_REGISTRY.values():
        if "train" in handler.info.capabilities:
            assert handler.info.training_data_range is not None


def test_contract_rejects_handler_with_missing_method() -> None:
    handler = SimpleNamespace(
        info=ModelInfo(
            agent_code="short-term",
            model_code="MODEL_BROKEN_V1.0",
            model_version="1.0.0",
            model_name="不完整模型",
            description="用于验证契约校验。",
            capabilities=["train", "backtest", "predict"],
        ),
        train=lambda context: None,
        backtest=lambda context: None,
    )

    with pytest.raises(ModelContractError, match="predict"):
        validate_model_handler("MODEL_BROKEN_V1.0", handler)


def test_contract_rejects_non_semantic_version() -> None:
    handler = SimpleNamespace(
        info=ModelInfo(
            agent_code="short-term",
            model_code="MODEL_BROKEN_V1.0",
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
        validate_model_handler("MODEL_BROKEN_V1.0", handler)


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

    with pytest.raises(ModelContractError, match="<模型名称>_V<主版本>.<次版本>"):
        validate_model_handler("BROKEN_MODEL", handler)


def test_contract_rejects_trainable_model_without_training_data_range() -> None:
    handler = SimpleNamespace(
        info=ModelInfo(
            agent_code="short-term",
            model_code="MODEL_BROKEN_V1.0",
            model_version="1.0.0",
            model_name="缺少数据范围的模型",
            description="用于验证训练数据范围契约。",
            capabilities=["train", "backtest", "predict"],
        ),
        train=lambda context: None,
        backtest=lambda context: None,
        predict=lambda context: None,
    )

    with pytest.raises(ModelContractError, match="training_data_range"):
        validate_model_handler("MODEL_BROKEN_V1.0", handler)


def test_contract_rejects_trainable_model_without_validator() -> None:
    source = MODEL_REGISTRY["MODEL_JIANGSHU_DIANLI_V1.0"].info
    handler = SimpleNamespace(
        info=source,
        train=lambda context: None,
        backtest=lambda context: None,
        predict=lambda context: None,
    )

    with pytest.raises(ModelContractError, match="validate_training_data"):
        validate_model_handler(source.model_code, handler)
