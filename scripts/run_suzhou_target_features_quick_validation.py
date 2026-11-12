from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
import pandas as pd


ROOT = Path(__file__).resolve().parents[1]
ENGINE_ROOT = ROOT / "gas-model-engine"
sys.path.insert(0, str(ENGINE_ROOT / "src"))
sys.path.insert(0, str(ROOT / "scripts"))

from gas_model_engine import (
    AggregationMode,
    AlgorithmProfile,
    AlgorithmProfileRegistry,
    BacktestSpec,
    CandidateSpec,
    ForecastRequest,
    SelectionSpec,
    create_default_forecast_pipeline,
)
from gas_model_engine.evaluation import calculate_metrics
from validate_suzhou_city_gas import dataset, load_data


HORIZON = 50
OUTPUT = ROOT / "outputs/suzhou-target-features-quick-validation"


def main() -> None:
    frame = load_data()
    train = frame.iloc[:-HORIZON].copy()
    holdout = frame.iloc[-HORIZON:].copy()
    profile = AlgorithmProfile(
        code="suzhou-target-features-quick",
        version="1.0.0",
        candidates=(
            CandidateSpec("ridge", "RIDGE_V1", search_space={"alpha": [0.1, 1.0, 10.0]}),
            CandidateSpec("xgboost", "XGBOOST_V1"),
            CandidateSpec("lightgbm", "LIGHTGBM_V1"),
        ),
        aggregation=AggregationMode.MEAN,
        scenario_code="SHORT_TERM_CUSTOMER_V1",
        feature_policy_code="SHORT_TERM_CUSTOMER_FEATURES_V1",
        backtest=BacktestSpec(
            "EXPANDING_WALK_FORWARD_V1",
            {
                "mode": "auto",
                "minimum_train_size": 365,
                "min_folds": 4,
                "max_folds": 4,
                "minimum_tune_folds": 2,
                "minimum_eval_folds": 2,
                "eval_ratio": 0.5,
            },
        ),
        combinations=(),
        selection=SelectionSpec("MIN_METRIC_SELECTOR_V1", {"metric": "mape"}),
    )
    pipeline = create_default_forecast_pipeline(
        profile_registry=AlgorithmProfileRegistry([profile])
    )
    result = pipeline.run(
        ForecastRequest(profile.code, dataset(train), dataset(holdout, target=False))
    )

    OUTPUT.mkdir(parents=True, exist_ok=True)
    rows = pd.DataFrame({"date": holdout["ds"], "actual": holdout["y"]})
    metrics = []
    for candidate_id, prediction in result.prediction.predictions.items():
        rows[candidate_id] = prediction.values
        values = calculate_metrics(holdout["y"], prediction.values)
        metrics.append({"candidate": candidate_id, **values})
    rows.to_csv(OUTPUT / "holdout-predictions.csv", index=False, encoding="utf-8-sig")
    metric_frame = pd.DataFrame(metrics).sort_values("mape")
    metric_frame.to_csv(OUTPUT / "metrics.csv", index=False, encoding="utf-8-sig")
    print(metric_frame.to_string(index=False))
    print("selected=" + ",".join(result.selected_candidate_ids))
    print(OUTPUT / "holdout-predictions.csv")


if __name__ == "__main__":
    main()
