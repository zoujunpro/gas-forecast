from typing import Protocol

from gas_model_platform.schemas.modeling import (
    BacktestResult,
    ModelContext,
    ModelInfo,
    PredictResult,
    TrainingDataValidationResult,
    TrainResult,
)


class ModelHandler(Protocol):
    info: ModelInfo

    def validate_training_data(
        self, context: ModelContext
    ) -> TrainingDataValidationResult:
        ...

    def train(self, context: ModelContext) -> TrainResult:
        ...

    def backtest(self, context: ModelContext) -> BacktestResult:
        ...

    def predict(self, context: ModelContext) -> PredictResult:
        ...
