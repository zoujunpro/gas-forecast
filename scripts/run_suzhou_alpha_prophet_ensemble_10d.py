from __future__ import annotations

import sys
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "gas-model-platform" / "src"))
sys.path.insert(0, str(ROOT / "scripts"))

from gas_model_platform.models.alpha_model.tools.forecast.prophet_residual import (
    forecast_prophet_ensemble,
)
from validate_suzhou_city_gas import load_data


OUTPUT = ROOT / "outputs" / "suzhou-alpha-prophet-ensemble-10d"


def mape(actual, prediction) -> float:
    return float(np.mean(np.abs((actual - prediction) / actual)) * 100)


def main() -> None:
    frame = load_data()
    train = frame.iloc[:-10][["ds", "y"]].copy()
    holdout = frame.iloc[-10:][["ds", "y"]].copy()
    result = forecast_prophet_ensemble(train, "ds", "y", horizon=10)
    if not result.success:
        raise RuntimeError(result.error)

    ensemble = np.asarray(result.data["forecast_values"], dtype=float)
    xgb = np.asarray(result.data["individual_predictions"]["prophet_xgboost"], dtype=float)
    lgb = np.asarray(result.data["individual_predictions"]["prophet_lightgbm"], dtype=float)
    actual = holdout["y"].to_numpy(dtype=float)
    output = pd.DataFrame(
        {
            "date": holdout["ds"].dt.strftime("%Y-%m-%d"),
            "actual": actual,
            "prophet_xgboost": xgb,
            "prophet_lightgbm": lgb,
            "ensemble_60_40": ensemble,
        }
    )
    OUTPUT.mkdir(parents=True, exist_ok=True)
    output.to_csv(OUTPUT / "predictions.csv", index=False, encoding="utf-8-sig")

    metrics = pd.DataFrame(
        [
            {"model": "Prophet+XGBoost", "mape": mape(actual, xgb)},
            {"model": "Prophet+LightGBM", "mape": mape(actual, lgb)},
            {"model": "组合模型(60% XGB + 40% LGB)", "mape": mape(actual, ensemble)},
        ]
    )
    metrics.to_csv(OUTPUT / "metrics.csv", index=False, encoding="utf-8-sig")

    dates = pd.to_datetime(output["date"])
    plt.rcParams["font.sans-serif"] = ["Heiti SC", "Arial Unicode MS", "DejaVu Sans"]
    plt.rcParams["axes.unicode_minus"] = False
    fig, ax = plt.subplots(figsize=(13, 7))
    ax.plot(dates, actual, color="black", linewidth=3.2, marker="o", label="实际销量")
    ax.plot(dates, xgb, linewidth=2, marker="o", label=f"Prophet+XGBoost ({metrics.iloc[0].mape:.2f}%)")
    ax.plot(dates, lgb, linewidth=2, marker="o", label=f"Prophet+LightGBM ({metrics.iloc[1].mape:.2f}%)")
    ax.plot(dates, ensemble, linewidth=3, marker="o", color="#f97316",
            label=f"组合预测60/40 (MAPE {metrics.iloc[2].mape:.2f}%)")
    ax.set_title("苏州燃气：旧项目Prophet+XGBoost+LightGBM组合预测（相同10天）", fontsize=16)
    ax.set_ylabel("日销量")
    ax.grid(alpha=0.25)
    ax.legend()
    ax.tick_params(axis="x", rotation=25)
    fig.tight_layout()
    fig.savefig(OUTPUT / "旧项目组合模型相同10天回测.png", dpi=180, bbox_inches="tight")
    print(metrics.to_string(index=False))
    print(OUTPUT)


if __name__ == "__main__":
    main()
