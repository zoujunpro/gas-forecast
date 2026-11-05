from __future__ import annotations

import json
import os
from pathlib import Path

os.environ.setdefault("MPLCONFIGDIR", "/private/tmp/gas-model-validation-mpl")

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "outputs" / "suzhou-city-gas-validation"


def annotate_bars(ax, bars, suffix=""):
    maximum = max(bar.get_height() for bar in bars)
    for bar in bars:
        ax.text(
            bar.get_x() + bar.get_width() / 2,
            bar.get_height() + maximum * 0.025,
            f"{bar.get_height():.2f}{suffix}",
            ha="center",
            va="bottom",
            fontsize=9,
        )


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    data = json.loads((OUTPUT / "report.json").read_text(encoding="utf-8"))
    points = pd.read_csv(OUTPUT / "holdout-predictions.csv", encoding="utf-8-sig")
    points["date"] = pd.to_datetime(points["date"])
    plt.rcParams.update(
        {
            "font.family": ["Arial Unicode MS", "PingFang SC", "DejaVu Sans"],
            "axes.unicode_minus": False,
        }
    )
    blue, pale_blue, orange, black = "#2F75B5", "#82A9D0", "#E67E22", "#172033"

    aligned = data["aligned_backtest"]
    labels = ["旧 V1", "旧 V3", "新 Pipeline\nRidge"]
    mape = [
        aligned["legacy"]["v1"]["mape"],
        aligned["legacy"]["v3"]["mape"],
        aligned["new"]["metrics"]["mape"],
    ]
    rmse = [
        aligned["legacy"]["v1"]["rmse"],
        aligned["legacy"]["v3"]["rmse"],
        aligned["new"]["metrics"]["rmse"],
    ]
    holdout_mape = [data["legacy"]["holdout"]["mape"], data["new"]["holdout"]["mape"]]
    holdout_rmse = [data["legacy"]["holdout"]["rmse"], data["new"]["holdout"]["rmse"]]

    fig = plt.figure(figsize=(14, 10), dpi=180)
    grid = fig.add_gridspec(2, 2, height_ratios=[0.9, 1.25], hspace=0.34, wspace=0.24)
    colors = [blue, pale_blue, orange]

    ax1 = fig.add_subplot(grid[0, 0])
    bars = ax1.bar(labels, mape, color=colors, width=0.62)
    annotate_bars(ax1, bars, "%")
    ax1.set_title("同窗 Eval 回测 MAPE（4折，越低越好）", fontweight="bold")
    ax1.set_ylim(0, max(mape) * 1.22)
    ax1.grid(axis="y", alpha=0.25)
    ax1.spines[["top", "right"]].set_visible(False)

    ax2 = fig.add_subplot(grid[0, 1])
    x = np.arange(2)
    width = 0.34
    b1 = ax2.bar(x - width / 2, [holdout_mape[0], holdout_rmse[0]], width, color=blue, label="旧 V1")
    b2 = ax2.bar(x + width / 2, [holdout_mape[1], holdout_rmse[1]], width, color=orange, label="新 Pipeline / Ridge")
    annotate_bars(ax2, b1)
    annotate_bars(ax2, b2)
    ax2.set_xticks(x, ["MAPE (%)", "RMSE"])
    ax2.set_title("最后50天独立测试（越低越好）", fontweight="bold")
    ax2.set_ylim(0, max(holdout_rmse) * 1.25)
    ax2.legend(frameon=False)
    ax2.grid(axis="y", alpha=0.25)
    ax2.spines[["top", "right"]].set_visible(False)

    ax3 = fig.add_subplot(grid[1, :])
    ax3.plot(points["date"], points["actual"], color=black, lw=2.3, label="实际销量")
    ax3.plot(points["date"], points["old_prediction"], color=blue, lw=1.8, label="旧 V1")
    ax3.plot(points["date"], points["new_prediction"], color=orange, lw=1.8, label="新 Pipeline / Ridge")
    ax3.axvspan(pd.Timestamp("2026-10-01"), points["date"].max(), color="#FDECEC", alpha=0.75, label="国庆时段")
    ax3.set_title("最后50天独立测试：实际销量与预测走势", fontweight="bold")
    ax3.set_ylabel("日销量（万方）")
    ax3.grid(axis="y", alpha=0.25)
    ax3.spines[["top", "right"]].set_visible(False)
    ax3.legend(ncol=4, frameon=False, loc="upper left")
    fig.autofmt_xdate(rotation=25)

    plan = aligned["execution_plan"]
    fig.suptitle(
        "苏州燃气：优化后回测与预测结果对比\n"
        f"自动计划：最低训练 {plan['minimum_train_size']} 天；10折 = 6 Tune + 4 Eval；"
        f"验证窗口/步长均为 {plan['validation_size']} 天",
        fontsize=16,
        fontweight="bold",
        color="#17365D",
        y=0.98,
    )
    fig.savefig(OUTPUT / "优化后回测与预测结果对比.png", bbox_inches="tight", facecolor="white")
    plt.close(fig)

    # 单独输出同窗回测图，便于文档或页面引用。
    fig, axes = plt.subplots(1, 2, figsize=(10, 4.2), dpi=180)
    for ax, values, title, suffix in (
        (axes[0], mape, "MAPE", "%"),
        (axes[1], rmse, "RMSE", ""),
    ):
        bars = ax.bar(labels, values, color=colors, width=0.62)
        annotate_bars(ax, bars, suffix)
        ax.set_title(f"同窗4折 Eval {title}（越低越好）", fontweight="bold")
        ax.set_ylim(0, max(values) * 1.23)
        ax.grid(axis="y", alpha=0.25)
        ax.spines[["top", "right"]].set_visible(False)
    fig.tight_layout()
    fig.savefig(OUTPUT / "优化后同窗回测对比.png", bbox_inches="tight", facecolor="white")
    plt.close(fig)

    print(OUTPUT / "优化后回测与预测结果对比.png")
    print(OUTPUT / "优化后同窗回测对比.png")


if __name__ == "__main__":
    main()
