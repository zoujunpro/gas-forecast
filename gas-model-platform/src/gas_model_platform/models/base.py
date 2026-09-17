from typing import Protocol

from gas_model_platform.schemas.modeling import (
    BacktestResult,
    ModelContext,
    ModelInfo,
    PredictResult,
    TrainResult,
)


class ModelHandler(Protocol):
    info: ModelInfo

    def train(self, context: ModelContext) -> TrainResult:
        ...

    def backtest(self, context: ModelContext) -> BacktestResult:
        ...

    def predict(self, context: ModelContext) -> PredictResult:
        ...

