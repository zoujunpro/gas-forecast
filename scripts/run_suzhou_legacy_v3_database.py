from __future__ import annotations

import argparse
import importlib.util
import sys
from pathlib import Path

import numpy as np
import pandas as pd


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from validate_suzhou_city_gas import HORIZON as DEFAULT_HORIZON, LEGACY_SOURCE, load_data, prepare_legacy


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--horizon", type=int, default=DEFAULT_HORIZON)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    horizon = args.horizon
    if horizon < 1:
        raise ValueError("horizon必须大于0")
    spec = importlib.util.spec_from_file_location("legacy_city_gas_v3", LEGACY_SOURCE)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"无法加载 {LEGACY_SOURCE}")
    engine = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(engine)

    frame = load_data()
    prepared, regressors = prepare_legacy(engine, frame)
    train = prepared.iloc[:-horizon].copy()
    holdout = prepared.iloc[-horizon:].copy()
    params = {
        "seasonality_mode": "additive",
        "changepoint_prior_scale": 0.05,
        "seasonality_prior_scale": 10.0,
        "holidays_prior_scale": 20.0,
    }
    forecast, _, _ = engine.run_single_forecast_v3(
        train,
        regressors,
        horizon,
        "D",
        params,
        holdout[["ds", *regressors]],
        4,
    )
    result = forecast[forecast["ds"].isin(holdout["ds"])].copy()
    result["timestamp"] = result["ds"] + pd.Timedelta(days=engine.DS_SHIFT_DAYS)
    result["actual"] = holdout["y"].to_numpy(dtype=float)
    result = result[["timestamp", "actual", "yhat", "yhat_base", "resid_hat"]]
    output = args.output or (
        ROOT / f"gas-model-engine/output/suzhou-v3-database-holdout-{horizon}d.csv"
    )
    output.parent.mkdir(parents=True, exist_ok=True)
    result.to_csv(output, index=False, encoding="utf-8-sig")
    error = np.abs((result["actual"] - result["yhat"]) / result["actual"])
    print(output)
    print(f"rows={len(result)}")
    print(f"MAPE={error.mean() * 100:.4f}%")
    print("regressors=" + ",".join(regressors))


if __name__ == "__main__":
    main()
