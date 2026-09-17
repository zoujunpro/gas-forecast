from typing import Any, Protocol

from gas_model_platform.schemas.modeling import ModelContext


class FeatureEngineer(Protocol):
    def transform(self, context: ModelContext) -> list[dict[str, Any]]:
        ...


class PassthroughFeatureEngineer:
    def transform(self, context: ModelContext) -> list[dict[str, Any]]:
        return context.dataset

