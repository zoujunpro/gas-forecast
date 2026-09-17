from gas_model_platform.models.base import ModelHandler
from gas_model_platform.models.registry import ModelRegistry
from gas_model_platform.schemas.modeling import (
    BacktestRequest,
    BacktestResult,
    ModelContext,
    PredictRequest,
    PredictResult,
    TrainRequest,
    TrainResult,
)


class ModelRuntime:
    def __init__(self, registry: ModelRegistry) -> None:
        self.registry = registry

    def train(self, request: TrainRequest) -> TrainResult:
        handler = self.registry.resolve(request.model_code)
        context = self._context_for_handler(request, handler)
        result = handler.train(context)
        result.metadata.update(self._selection_metadata(request.model_code, handler.info.model_code))
        return result

    def backtest(self, request: BacktestRequest) -> BacktestResult:
        handler = self.registry.resolve(request.model_code)
        context = self._context_for_handler(request, handler)
        result = handler.backtest(context)
        result.metadata.update(self._selection_metadata(request.model_code, handler.info.model_code))
        return result

    def predict(self, request: PredictRequest) -> PredictResult:
        handler = self.registry.resolve(request.model_code)
        context = self._context_for_handler(request, handler)
        result = handler.predict(context)
        result.metadata.update(self._selection_metadata(request.model_code, handler.info.model_code))
        return result

    def _context_for_handler(
        self,
        request: TrainRequest | BacktestRequest | PredictRequest,
        handler: ModelHandler,
    ) -> ModelContext:
        if request.agent_code and request.agent_code != handler.info.agent_code:
            raise ValueError(
                f"agent_code={request.agent_code} 与 modelCode={request.model_code} 不匹配"
            )
        data = request.model_dump()
        data["agent_code"] = handler.info.agent_code
        data["model_code"] = handler.info.model_code
        return ModelContext(**data)

    def _selection_metadata(self, requested_model_code: str | None, selected_model_code: str) -> dict[str, str | bool]:
        return {
            "auto_selected": False,
            "selection_scope": "modelCode",
            "requested_model_code": requested_model_code or "auto",
            "selected_model_code": selected_model_code,
        }
