import numpy as np
import pandas as pd
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


def dataset(rows=100, with_target=True, start="2025-01-01"):
    values = np.arange(1, rows + 1, dtype=float)
    return AlgorithmDataset(
        features=pd.DataFrame({"x": values}),
        target=pd.Series(values) if with_target else None,
        dates=pd.date_range(start, periods=len(values)),
    )


def profile_registry():
    return AlgorithmProfileRegistry(
        [
            AlgorithmProfile(
                code="pipeline-test",
                version="1.0.0",
                candidates=(
                    CandidateSpec("naive", "NAIVE_V1"),
                ),
                aggregation=AggregationMode.MEAN,
            )
        ]
    )


def test_pipeline_always_executes_complete_forecast_flow():
    pipeline = create_default_forecast_pipeline(profile_registry=profile_registry())
    result = pipeline.run(
        ForecastRequest(
            "pipeline-test",
            dataset(),
            dataset(7, False, "2025-04-11"),
        )
    )

    statuses = {item.stage: item.status for item in result.stages}
    assert statuses[PipelineStage.BACKTEST] == PipelineStageStatus.COMPLETED
    assert statuses[PipelineStage.SELECTION] == PipelineStageStatus.COMPLETED
    assert result.selected_candidate_ids == ("naive",)
    assert set(result.prediction.predictions) == {"naive"}
