from typing import Any, Protocol

from gas_model_platform.schemas.modeling import ModelContext


class DatasetBuilder(Protocol):
    def build(self, context: ModelContext) -> list[dict[str, Any]]:
        ...


class InlineDatasetBuilder:
    def build(self, context: ModelContext) -> list[dict[str, Any]]:
        return context.dataset

