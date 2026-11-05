from ..engine import AlgorithmEngine
from ..profiles import AlgorithmProfileRegistry, create_default_profile_registry
from .contracts import (
    BacktestResult,
    ForecastRequest,
    ForecastResult,
    PipelineStage,
    PipelineStageResult,
    PipelineStageStatus,
    ProfileModelBundle,
    ProfilePredictionResult,
    ProfilePredictRequest,
    ProfileTrainRequest,
)
from .forecast_pipeline import ForecastPipeline
from .ports import BacktestRunner, CombinationBuilder, FeatureProcessor, FinalModelSelector
from .profile_executor import AlgorithmProfileExecutor
from .scenario_strategies import (
    LegacyShortTermResidualStrategy,
    MonthlyIndustryStrategy,
    ShortTermCustomerStrategy,
    WinterSupplyStrategy,
)
from .strategies import (
    ConfigDrivenForecastStrategy,
    DirectProfileStrategy,
    ForecastStrategy,
    ForecastStrategyRegistry,
    PendingLegacyStrategy,
    create_default_strategy_registry,
)


def create_default_profile_executor(
    algorithm_engine: AlgorithmEngine | None = None,
    profile_registry: AlgorithmProfileRegistry | None = None,
) -> AlgorithmProfileExecutor:
    return AlgorithmProfileExecutor(
        algorithm_engine or AlgorithmEngine(),
        profile_registry or create_default_profile_registry(),
    )


def create_default_forecast_pipeline(
    algorithm_engine: AlgorithmEngine | None = None,
    profile_registry: AlgorithmProfileRegistry | None = None,
    strategy_registry: ForecastStrategyRegistry | None = None,
) -> ForecastPipeline:
    registry = profile_registry or create_default_profile_registry()
    executor = AlgorithmProfileExecutor(algorithm_engine or AlgorithmEngine(), registry)
    return ForecastPipeline(
        executor, registry, strategy_registry or create_default_strategy_registry()
    )


__all__ = [
    "AlgorithmProfileExecutor",
    "BacktestResult",
    "BacktestRunner",
    "CombinationBuilder",
    "ConfigDrivenForecastStrategy",
    "DirectProfileStrategy",
    "FeatureProcessor",
    "FinalModelSelector",
    "ForecastPipeline",
    "ForecastRequest",
    "ForecastResult",
    "ForecastStrategy",
    "ForecastStrategyRegistry",
    "LegacyShortTermResidualStrategy",
    "MonthlyIndustryStrategy",
    "PendingLegacyStrategy",
    "PipelineStage",
    "PipelineStageResult",
    "PipelineStageStatus",
    "ProfileModelBundle",
    "ProfilePredictRequest",
    "ProfilePredictionResult",
    "ProfileTrainRequest",
    "ShortTermCustomerStrategy",
    "WinterSupplyStrategy",
    "create_default_forecast_pipeline",
    "create_default_profile_executor",
    "create_default_strategy_registry",
]
