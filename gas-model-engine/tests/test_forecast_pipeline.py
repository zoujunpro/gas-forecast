import numpy as np
import pandas as pd
import pytest

from gas_model_engine import (
    AggregationMode,
    AlgorithmDataset,
    AlgorithmProfile,
    AlgorithmProfileRegistry,
    CandidateSpec,
    ForecastRequest,
    PipelineStage,
    PipelineStageStatus,
    create_default_forecast_pipeline,
)


def dataset(with_target=True):
    values = np.arange(1, 41, dtype=float)
    return AlgorithmDataset(
        features=pd.DataFrame({"x": values}),
        target=pd.Series(values) if with_target else None,
        dates=pd.date_range("2025-01-01", periods=len(values)),
    )


def direct_profile_registry():
    return AlgorithmProfileRegistry(
        [
            AlgorithmProfile(
                code="pipeline-test",
                version="1.0.0",
                candidates=(
                    CandidateSpec("naive", "NAIVE_V1"),
                    CandidateSpec("ridge", "RIDGE_V1"),
                ),
                aggregation=AggregationMode.MEAN,
                strategy_code="DIRECT_PROFILE_V1",
            )
        ]
    )


def test_pipeline_dispatches_explicit_direct_strategy():
    pipeline = create_default_forecast_pipeline(profile_registry=direct_profile_registry())
    result = pipeline.run(ForecastRequest("pipeline-test", dataset(), dataset(False)))

    statuses = {item.stage: item.status for item in result.stages}
    assert statuses[PipelineStage.BACKTEST] == PipelineStageStatus.SKIPPED
    assert statuses[PipelineStage.SELECTION] == PipelineStageStatus.SKIPPED
    assert result.selected_candidate_ids == ("naive", "ridge")
    assert set(result.prediction.predictions) == {"naive", "ridge"}


def test_builtin_legacy_profile_never_falls_back_to_generic_logic():
    pipeline = create_default_forecast_pipeline()

    with pytest.raises(NotImplementedError, match="不会回退到通用组合流程"):
        pipeline.run(ForecastRequest("winter-supply", dataset(), dataset(False)))
