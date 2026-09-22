from gas_model_platform.models.base import ModelHandler
from gas_model_platform.models.contract import validate_model_handler
from gas_model_platform.models.short_agent import ModelJiangshuDianliV1Handler
from gas_model_platform.models.winter_agent import WinterAgentV1Handler
from gas_model_platform.schemas.modeling import ModelInfo


class ModelRegistry:
    """按对外模型编号保存和解析唯一的模型处理器。"""

    def __init__(self, handlers: dict[str, ModelHandler] | None = None) -> None:
        self._handlers: dict[str, ModelHandler] = {}
        for model_code, handler in (handlers or {}).items():
            self.register(model_code, handler)

    def register(self, model_code: str, handler: ModelHandler) -> None:
        validate_model_handler(model_code, handler)
        if model_code in self._handlers:
            raise ValueError(f"model handler already registered: {model_code}")
        self._handlers[model_code] = handler

    def get(self, model_code: str) -> ModelHandler:
        if model_code not in self._handlers:
            supported = ", ".join(self._handlers)
            raise KeyError(
                f"model handler not found: {model_code}; supported: {supported}"
            )
        return self._handlers[model_code]

    def resolve(self, model_code: str) -> ModelHandler:
        return self.get(model_code)

    def list_models(self) -> list[ModelInfo]:
        return [handler.info for handler in self._handlers.values()]


MODEL_REGISTRY: dict[str, ModelHandler] = {
    "WINTER_MODEL_V1.0": WinterAgentV1Handler(),
    "MODEL_JIANGSHU_DIANLI_V1.0": ModelJiangshuDianliV1Handler(),
}

registry = ModelRegistry(MODEL_REGISTRY)
