import numpy as np
import pandas as pd

from gas_model_engine import (
    AggregationMode,
    AlgorithmDataset,
    AlgorithmProfile,
    AlgorithmProfileRegistry,
    BacktestSpec,
    CandidateSpec,
    CombinationSpec,
    ForecastRequest,
    MethodSpec,
    SelectionSpec,
    create_default_forecast_pipeline,
)
from gas_model_engine.backtesting import ExpandingWalkForwardPlanner
from gas_model_engine.features import ConfiguredFeatureProcessor


def test_auto_backtest_plan_uses_shared_dynamic_windows():
    plan = ExpandingWalkForwardPlanner().plan(
        row_count=100,
        forecast_horizon=10,
        params={"mode": "auto", "minimum_train_size": 40, "max_folds": 4},
    )

    assert len(plan.folds) == 4
    assert plan.validation_size == 10
    assert plan.step_size == 10
    assert plan.available_fold_count == 6
    assert plan.initial_train_size == 60
    assert plan.folds[-1].validation_end == 100


def test_target_features_drop_warmup_rows_without_backfill_leakage():
    history = AlgorithmDataset(
        features=pd.DataFrame(index=range(8)),
        dates=pd.date_range("2025-01-01", periods=8),
        target=pd.Series(np.arange(1, 9, dtype=float)),
    )
    future = AlgorithmDataset(
        features=pd.DataFrame(index=range(1)),
        dates=pd.date_range("2025-01-09", periods=1),
    )

    train, _ = ConfiguredFeatureProcessor().prepare(
        history,
        future,
        (
            MethodSpec("LAG_V1", {"lags": [3]}),
            MethodSpec("ROLLING_V1", {"windows": [3], "statistics": ["mean"]}),
        ),
    )

    assert len(train.features) == 5
    assert train.target.iloc[0] == 4
    assert train.features.iloc[0]["target_lag_3"] == 1
    assert train.features.iloc[0]["target_rolling_mean_3"] == 2


def test_short_term_target_features_are_causal_and_support_recursive_future_rows():
    history = AlgorithmDataset(
        features=pd.DataFrame(index=range(35)),
        dates=pd.date_range("2025-01-01", periods=35),
        target=pd.Series(np.arange(1, 36, dtype=float)),
    )
    future = AlgorithmDataset(
        features=pd.DataFrame(index=range(2)),
        dates=pd.date_range("2025-02-05", periods=2),
    )
    specs = (
        MethodSpec("LAG_V1", {"lags": [1, 2, 7, 14, 28]}),
        MethodSpec("ROLLING_V1", {"windows": [7, 14, 28], "statistics": ["mean", "std"]}),
        MethodSpec("TARGET_CHANGE_V1", {"periods": [1, 7, 28]}),
    )

    train, predicted = ConfiguredFeatureProcessor().prepare(history, future, specs)

    assert train.features.iloc[-1]["target_lag_1"] == 34
    assert train.features.iloc[-1]["target_rolling_mean_7"] == 31
    assert train.features.iloc[-1]["target_diff_7"] == 7
    assert predicted.features.iloc[0]["target_lag_1"] == 35
    assert predicted.features.iloc[0]["target_diff_28"] == 28


def test_config_driven_pipeline_ranks_algorithms_and_legal_combinations():
    values = np.arange(1, 81, dtype=float)
    history = AlgorithmDataset(
        features=pd.DataFrame({"temperature": np.linspace(5, 20, 80)}),
        dates=pd.date_range("2025-01-01", periods=80),
        target=pd.Series(values),
    )
    future = AlgorithmDataset(
        features=pd.DataFrame({"temperature": np.linspace(20, 22, 5)}),
        dates=pd.date_range("2025-03-22", periods=5),
    )
    profile = AlgorithmProfile(
        code="configured-test",
        version="1.0.0",
        candidates=(
            CandidateSpec("naive", "NAIVE_V1", required=True),
            CandidateSpec("ridge", "RIDGE_V1"),
        ),
        aggregation=AggregationMode.MEAN,
        scenario_code="DEFAULT_FORECAST_SCENARIO_V1",
        features=(
            MethodSpec("DYNAMIC_PASSTHROUGH_V1"),
            MethodSpec("CALENDAR_V1"),
            MethodSpec("LAG_V1", {"lags": [1, 7]}),
        ),
        backtest=BacktestSpec(
            params={
                "mode": "auto",
                "minimum_train_size": 30,
                "min_folds": 2,
                "max_folds": 3,
            }
        ),
        combinations=(CombinationSpec("EQUAL_WEIGHT_V1", {"top_k": [2]}),),
        selection=SelectionSpec(params={"metric": "mape"}),
    )
    pipeline = create_default_forecast_pipeline(
        profile_registry=AlgorithmProfileRegistry([profile])
    )

    result = pipeline.run(ForecastRequest(profile.code, history, future))

    assert result.backtest is not None
    assert result.backtest.metadata["fold_count"] == 3
    assert "EQUAL_WEIGHT_V1_TOP2" in result.backtest.candidate_metrics
    assert set(result.prediction.predictions) == {"naive", "ridge", "EQUAL_WEIGHT_V1_TOP2"}
    assert result.selected_candidate_ids[0] in result.prediction.predictions
    assert result.backtest.metadata["tuning"]["ridge"]["variant_count"] == 1
    assert result.prediction.predictions["ridge"].lower_bounds is not None
    assert result.prediction.predictions["ridge"].upper_bounds is not None
    assert (
        result.prediction.predictions["ridge"].metadata["interval_method"]
        == "OOF_ABSOLUTE_ERROR_CONFORMAL"
    )
    assert result.prediction.predictions["ridge"].metadata["prediction_mode"] == "recursive"
    assert (
        result.prediction.predictions["EQUAL_WEIGHT_V1_TOP2"].metadata["prediction_mode"]
        == "recursive"
    )
