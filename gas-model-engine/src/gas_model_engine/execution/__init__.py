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
from .scenarios import (
    LegacyShortTermResidualScenario,
    MonthlyIndustryScenario,
    ShortTermCustomerScenario,
    WinterSupplyScenario,
)
from .scenario import (
    ForecastScenario,
    ForecastScenarioRegistry,
    create_default_scenario_registry,
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
    scenario_registry: ForecastScenarioRegistry | None = None,
) -> ForecastPipeline:
    registry = profile_registry or create_default_profile_registry()
    executor = AlgorithmProfileExecutor(algorithm_engine or AlgorithmEngine(), registry)
    return ForecastPipeline(
        executor, registry, scenario_registry or create_default_scenario_registry()
    )


__all__ = [
    "AlgorithmProfileExecutor",
    "BacktestResult",
    "BacktestRunner",
    "CombinationBuilder",
    "ForecastScenario",
    "FeatureProcessor",
    "FinalModelSelector",
    "ForecastPipeline",
    "ForecastRequest",
    "ForecastResult",
    "ForecastScenarioRegistry",
    "LegacyShortTermResidualScenario",
    "MonthlyIndustryScenario",
    "PipelineStage",
    "PipelineStageResult",
    "PipelineStageStatus",
    "ProfileModelBundle",
    "ProfilePredictRequest",
    "ProfilePredictionResult",
    "ProfileTrainRequest",
    "ShortTermCustomerScenario",
    "WinterSupplyScenario",
    "create_default_forecast_pipeline",
    "create_default_profile_executor",
    "create_default_scenario_registry",
]
