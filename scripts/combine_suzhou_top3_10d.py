from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "outputs" / "suzhou-city-gas-10d-no-rolling"
OUTPUT = ROOT / "outputs" / "suzhou-city-gas-top3-10d"
TOP3 = ["lightgbm", "xgboost", "ridge"]


def metric(actual, prediction) -> float:
    return float(np.mean(np.abs((actual - prediction) / actual)) * 100)


def main() -> None:
    predictions = pd.read_csv(SOURCE / "all-candidate-predictions.csv")
    metrics = pd.read_csv(SOURCE / "all-candidate-metrics.csv").set_index("candidate")
    inverse = 1.0 / metrics.loc[TOP3, "backtest_mape"]
    weights = inverse / inverse.sum()
    predictions["top3_weighted"] = sum(
        float(weights[model]) * predictions[model] for model in TOP3
    )
    predictions["top3_equal"] = predictions[TOP3].mean(axis=1)

    actual = predictions["actual"].to_numpy(dtype=float)
    rows = []
    for model in [*TOP3, "top3_weighted", "top3_equal"]:
        rows.append(
            {
                "model": model,
                "weight": float(weights.get(model, np.nan)),
                "mape": metric(actual, predictions[model].to_numpy(dtype=float)),
            }
        )
    result_metrics = pd.DataFrame(rows)
    OUTPUT.mkdir(parents=True, exist_ok=True)
    predictions[["date", "actual", *TOP3, "top3_weighted", "top3_equal"]].to_csv(
        OUTPUT / "predictions.csv", index=False, encoding="utf-8-sig"
    )
    result_metrics.to_csv(OUTPUT / "metrics.csv", index=False, encoding="utf-8-sig")

    dates = pd.to_datetime(predictions["date"])
    plt.rcParams["font.sans-serif"] = ["Heiti SC", "Arial Unicode MS", "DejaVu Sans"]
    plt.rcParams["axes.unicode_minus"] = False
    fig, ax = plt.subplots(figsize=(13, 7))
    ax.plot(dates, actual, color="black", linewidth=3.2, marker="o", label="实际销量")
    colors = {"lightgbm": "#16a34a", "xgboost": "#2563eb", "ridge": "#9333ea"}
    for model in TOP3:
        value = result_metrics.loc[result_metrics["model"] == model, "mape"].iloc[0]
        ax.plot(dates, predictions[model], linewidth=1.7, marker=".", color=colors[model],
                alpha=0.8, label=f"{model} ({value:.2f}%)")
    combined_mape = result_metrics.loc[
        result_metrics["model"] == "top3_weighted", "mape"
    ].iloc[0]
    ax.plot(dates, predictions["top3_weighted"], color="#f97316", linewidth=3,
            marker="o", label=f"前三名加权组合 (MAPE {combined_mape:.2f}%)")
    ax.set_title("苏州燃气：历史回测最优前三名加权预测未来10天", fontsize=16)
    ax.set_ylabel("日销量")
    ax.grid(alpha=0.25)
    ax.legend()
    ax.tick_params(axis="x", rotation=25)
    fig.tight_layout()
    fig.savefig(OUTPUT / "最优前三名加权预测10天.png", dpi=180, bbox_inches="tight")
    print("weights")
    print(weights.to_string())
    print(result_metrics.to_string(index=False))


if __name__ == "__main__":
    main()
