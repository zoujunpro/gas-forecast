"""运行苏州燃气新Pipeline，并导出全部候选的回测和留出集预测结果。"""

from __future__ import annotations

import json
import sys
import time
from pathlib import Path

import numpy as np
import pandas as pd


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "gas-model-engine" / "src"))
sys.path.insert(0, str(ROOT / "scripts"))

from gas_model_engine import ForecastRequest, create_default_forecast_pipeline
from gas_model_engine.evaluation import calculate_metrics
from validate_suzhou_city_gas import HORIZON, dataset, load_data


OUTPUT = ROOT / "outputs" / "suzhou-city-gas-new-pipeline"


def serializable(value):
    if isinstance(value, np.ndarray):
        return value.tolist()
    if isinstance(value, (np.floating, np.integer)):
        return value.item()
    raise TypeError(type(value).__name__)


def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    frame = load_data()
    train = frame.iloc[:-HORIZON].copy()
    holdout = frame.iloc[-HORIZON:].copy()
    actual = holdout["y"].to_numpy(dtype=float)

    started = time.perf_counter()
    result = create_default_forecast_pipeline().run(
        ForecastRequest("short-term", dataset(train), dataset(holdout, target=False))
    )
    elapsed = time.perf_counter() - started

    prediction_frame = pd.DataFrame(
        {
            "date": holdout["ds"].dt.strftime("%Y-%m-%d"),
            "actual": actual,
        }
    )
    holdout_metrics = {}
    for candidate_id, prediction in result.prediction.predictions.items():
        prediction_frame[candidate_id] = prediction.values
        holdout_metrics[candidate_id] = calculate_metrics(actual, prediction.values)

    prediction_frame.to_csv(
        OUTPUT / "all-candidate-predictions.csv", index=False, encoding="utf-8-sig"
    )
    metric_rows = []
    for candidate_id, metrics in result.backtest.candidate_metrics.items():
        metric_rows.append(
            {
                "candidate": candidate_id,
                "selected": candidate_id in result.selected_candidate_ids,
                "backtest_mape": metrics.get("mape"),
                "backtest_rmse": metrics.get("rmse"),
                "backtest_mae": metrics.get("mae"),
                "holdout_mape": holdout_metrics.get(candidate_id, {}).get("mape"),
                "holdout_rmse": holdout_metrics.get(candidate_id, {}).get("rmse"),
                "holdout_mae": holdout_metrics.get(candidate_id, {}).get("mae"),
            }
        )
    metrics_frame = pd.DataFrame(metric_rows).sort_values(
        ["backtest_mape", "candidate"], na_position="last"
    )
    metrics_frame.to_csv(OUTPUT / "all-candidate-metrics.csv", index=False, encoding="utf-8-sig")

    report = {
        "data": {
            "customer": "苏州燃气",
            "history_start": str(frame["ds"].min().date()),
            "history_end": str(frame["ds"].max().date()),
            "total_rows": len(frame),
            "train_rows": len(train),
            "holdout_rows": len(holdout),
            "holdout_start": str(holdout["ds"].min().date()),
            "holdout_end": str(holdout["ds"].max().date()),
        },
        "selected": result.selected_candidate_ids[0],
        "elapsed_seconds": elapsed,
        "execution_plan": result.backtest.metadata,
        "backtest_metrics": result.backtest.candidate_metrics,
        "holdout_metrics": holdout_metrics,
        "failures": result.prediction.failed_candidates,
    }
    (OUTPUT / "report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2, default=serializable),
        encoding="utf-8",
    )
    print(metrics_frame.to_string(index=False))
    print(json.dumps({"selected": report["selected"], "elapsed_seconds": elapsed}, ensure_ascii=False))


if __name__ == "__main__":
    main()
