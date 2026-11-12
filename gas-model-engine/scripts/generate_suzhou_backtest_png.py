"""根据当前10天回测CSV生成白底PNG对比图。"""

from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "output/suzhou-current-pipeline-holdout-10d.csv"
TARGET = ROOT / "output/suzhou-10d-backtest-comparison.png"


def mape(actual, predicted):
    return float(np.mean(np.abs((actual - predicted) / actual)) * 100)


def main():
    data = pd.read_csv(SOURCE)
    data["ds"] = pd.to_datetime(data["ds"])
    models = [
        column
        for column in data.columns
        if column not in {"ds", "actual", "selected_prediction"}
    ]
    scores = {model: mape(data["actual"], data[model]) for model in models}
    ranking = sorted(scores, key=scores.get)

    plt.rcParams.update(
        {
            "font.family": ["Arial Unicode MS", "PingFang SC", "Heiti SC", "DejaVu Sans"],
            "axes.unicode_minus": False,
            "figure.facecolor": "white",
            "axes.facecolor": "white",
            "savefig.facecolor": "white",
            "text.color": "#222222",
            "axes.labelcolor": "#222222",
            "xtick.color": "#333333",
            "ytick.color": "#333333",
        }
    )
    fig, (ax1, ax2) = plt.subplots(
        2, 1, figsize=(15, 11), gridspec_kw={"height_ratios": [1.35, 1]}
    )
    fig.suptitle("Suzhou City Gas - 10-Day Backtest Comparison", fontsize=20, fontweight="bold")

    ax1.plot(data["ds"], data["actual"], color="#111111", linewidth=3, marker="o", label="Actual")
    lines = {
        "prophet": ("Prophet (best)", "#1677ff"),
        "xgboost": ("XGBoost (selected)", "#e67e22"),
        "ridge": ("Ridge", "#2ca02c"),
        "lightgbm": ("LightGBM", "#9467bd"),
        "prophet_xgboost": ("Prophet + XGBoost", "#d62728"),
    }
    for model, (label, color) in lines.items():
        if model in data:
            ax1.plot(
                data["ds"], data[model], linewidth=2, marker="o", markersize=4,
                color=color, label=f"{label}  MAPE {scores[model]:.2f}%",
            )
    ax1.set_title("Actual vs. model predictions")
    ax1.set_ylabel("Daily gas sales")
    ax1.grid(True, color="#dddddd", linewidth=0.8, alpha=0.8)
    ax1.legend(ncol=2, frameon=False, loc="upper left")
    ax1.tick_params(axis="x", rotation=25)

    labels = [
        {
            "moving_average": "Moving average",
            "prophet_xgboost": "Prophet + XGBoost",
            "prophet_ar": "Prophet + AR",
        }.get(model, model.replace("_", " ").title())
        for model in ranking
    ]
    values = [scores[model] for model in ranking]
    colors = [
        "#1677ff" if model == "prophet" else "#e67e22" if model == "xgboost" else "#9aa0a6"
        for model in ranking
    ]
    bars = ax2.barh(labels, values, color=colors)
    ax2.invert_yaxis()
    ax2.set_title("MAPE ranking (lower is better)")
    ax2.set_xlabel("MAPE (%)")
    ax2.grid(True, axis="x", color="#dddddd", linewidth=0.8, alpha=0.8)
    ax2.set_axisbelow(True)
    for bar, value, model in zip(bars, values, ranking):
        suffix = "  BEST" if model == "prophet" else "  SELECTED" if model == "xgboost" else ""
        ax2.text(value + 0.35, bar.get_y() + bar.get_height() / 2, f"{value:.2f}%{suffix}", va="center")
    ax2.set_xlim(0, max(values) * 1.23)

    fig.text(
        0.5, 0.015,
        "Backtest period: 2026-09-30 to 2026-10-09 | Blue = actual best; Orange = pipeline selection",
        ha="center", color="#555555",
    )
    plt.tight_layout(rect=[0, 0.035, 1, 0.96])
    fig.savefig(TARGET, dpi=180, bbox_inches="tight", facecolor="white")
    print(TARGET)


if __name__ == "__main__":
    main()
