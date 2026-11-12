import argparse
from pathlib import Path

import matplotlib.pyplot as plt
import pandas as pd


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUTPUT = ROOT / "outputs" / "suzhou-city-gas-pre-national-day-10d"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--title", default="苏州燃气：国庆前10天全部模型回测")
    parser.add_argument("--filename", default="国庆前10天全部模型回测.png")
    args = parser.parse_args()
    output = args.input
    predictions = pd.read_csv(output / "all-candidate-predictions.csv")
    metrics = pd.read_csv(output / "all-candidate-metrics.csv").sort_values("holdout_mape")
    predictions["date"] = pd.to_datetime(predictions["date"])

    plt.rcParams["font.sans-serif"] = ["Heiti SC", "Arial Unicode MS", "DejaVu Sans"]
    plt.rcParams["axes.unicode_minus"] = False
    fig, (ax_curve, ax_metric) = plt.subplots(
        2, 1, figsize=(15, 11), gridspec_kw={"height_ratios": [2.1, 1]}
    )

    ax_curve.plot(
        predictions["date"], predictions["actual"], color="black", linewidth=3.4,
        marker="o", markersize=7, label="实际销量", zorder=20,
    )
    candidates = [column for column in predictions.columns if column not in {"date", "actual"}]
    for candidate in candidates:
        mape = metrics.loc[metrics["candidate"] == candidate, "holdout_mape"].iloc[0]
        linewidth = 2.5 if candidate in {"lightgbm", "xgboost"} else 1.35
        alpha = 1.0 if candidate in {"lightgbm", "xgboost"} else 0.68
        ax_curve.plot(
            predictions["date"], predictions[candidate], linewidth=linewidth,
            alpha=alpha, marker="." if linewidth > 2 else None,
            label=f"{candidate}（MAPE {mape:.2f}%）",
        )
    date_range = f"{predictions['date'].min():%Y-%m-%d} 至 {predictions['date'].max():%Y-%m-%d}"
    ax_curve.set_title(f"{args.title}（{date_range}）", fontsize=17)
    ax_curve.set_ylabel("日销量")
    ax_curve.grid(alpha=0.22)
    ax_curve.legend(ncol=3, fontsize=9, loc="best")
    ax_curve.tick_params(axis="x", rotation=25)

    colors = ["#16a34a" if name == "lightgbm" else "#f59e0b" if name == "xgboost" else "#64748b"
              for name in metrics["candidate"]]
    bars = ax_metric.bar(metrics["candidate"], metrics["holdout_mape"], color=colors)
    for bar, value in zip(bars, metrics["holdout_mape"]):
        ax_metric.text(bar.get_x() + bar.get_width() / 2, value + 0.22, f"{value:.2f}%",
                       ha="center", va="bottom", fontsize=9)
    ax_metric.set_title("各模型MAPE（越低越好）")
    ax_metric.set_ylabel("MAPE（%）")
    ax_metric.tick_params(axis="x", rotation=28)
    ax_metric.grid(axis="y", alpha=0.2)
    ax_metric.set_ylim(0, metrics["holdout_mape"].max() * 1.18)

    fig.tight_layout()
    path = output / args.filename
    fig.savefig(path, dpi=180, bbox_inches="tight", facecolor="white")
    print(path)


if __name__ == "__main__":
    main()
