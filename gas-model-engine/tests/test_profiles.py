import numpy as np
import pandas as pd

from gas_model_engine import (
    AggregationMode,
    AlgorithmDataset,
    AlgorithmEngine,
    AlgorithmProfile,
    AlgorithmProfileExecutor,
    AlgorithmProfileRegistry,
    CandidateSpec,
    ProfilePredictRequest,
    ProfileTrainRequest,
    create_default_profile_registry,
)


def train_data():
    values = np.arange(1, 41, dtype=float)
    return AlgorithmDataset(
        features=pd.DataFrame({"x": values}),
        target=pd.Series(values),
        dates=pd.date_range("2025-01-01", periods=len(values)),
    )


def future_data():
    return AlgorithmDataset(
        features=pd.DataFrame({"x": [41.0, 42.0, 43.0]}),
        dates=pd.date_range("2025-03-01", periods=3),
    )


def test_profile_executor_uses_selected_algorithms_and_aggregates_results():
    profile = AlgorithmProfile(
        code="test-profile",
        version="1.0.0",
        candidates=(
            CandidateSpec("naive", "NAIVE_V1"),
            CandidateSpec("ridge", "RIDGE_V1", {"alpha": 0.01}),
        ),
        aggregation=AggregationMode.MEAN,
    )
    executor = AlgorithmProfileExecutor(
        AlgorithmEngine(),
        AlgorithmProfileRegistry([profile]),
    )

    bundle = executor.train(ProfileTrainRequest("test-profile", train_data()))
    assert set(bundle.candidate_models) == {"naive", "ridge"}

    result = executor.predict(ProfilePredictRequest(bundle, future_data()))
    assert set(result.predictions) == {"naive", "ridge"}
    assert result.final_prediction.values.shape == (3,)
    assert result.final_prediction.metadata["aggregation"] == "mean"


def test_default_profiles_expose_different_algorithm_scopes():
    registry = create_default_profile_registry()
    assert registry.list_profile_codes() == ["monthly-industry", "short-term", "winter-supply"]

    short_codes = {item.algorithm_code for item in registry.get("short-term").candidates}
    winter_codes = {item.algorithm_code for item in registry.get("winter-supply").candidates}
    assert {
        "PROPHET_AR_RESIDUAL_V1",
        "PROPHET_XGBOOST_RESIDUAL_V1",
        "PROPHET_LIGHTGBM_RESIDUAL_V1",
    } <= short_codes
    assert {"NAIVE_V1", "PROPHET_V1", "XGBOOST_V1", "LIGHTGBM_V1"} <= short_codes
    assert "SEASONAL_NAIVE_V1" in winter_codes
    assert "RIDGE_V1" in winter_codes

    short_profile = registry.get("short-term")
    assert short_profile.combinations == ()
