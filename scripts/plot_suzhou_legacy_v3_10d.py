from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "gas-model-engine/output/suzhou-v3-database-holdout-10d.csv"
OUTPUT = ROOT / "outputs/suzhou-city-gas-10d-no-rolling/旧V3同数据10天回测.png"


def main() -> None:
    frame = pd.read_csv(SOURCE)
    frame["timestamp"] = pd.to_datetime(frame["timestamp"])
    mape = float(np.mean(np.abs((frame["actual"] - frame["yhat"]) / frame["actual"])) * 100)

    plt.rcParams["font.sans-serif"] = ["Heiti SC", "Arial Unicode MS", "DejaVu Sans"]
    plt.rcParams["axes.unicode_minus"] = False
    fig, ax = plt.subplots(figsize=(13, 7))
    ax.plot(frame["timestamp"], frame["actual"], color="black", linewidth=3,
            marker="o", markersize=7, label="实际销量")
    ax.plot(frame["timestamp"], frame["yhat"], color="#f97316", linewidth=2.8,
            marker="o", markersize=6, label=f"旧V3预测（MAPE {mape:.2f}%）")
    ax.plot(frame["timestamp"], frame["yhat_base"], color="#3b82f6", linewidth=1.6,
            linestyle="--", alpha=0.8, label="旧V3 Prophet基础预测")
    ax.set_title("苏州燃气：旧V3使用相同数据库数据预测未来10天", fontsize=17)
    ax.set_ylabel("日销量")
    ax.grid(alpha=0.25)
    ax.legend(fontsize=11)
    ax.tick_params(axis="x", rotation=25)
    fig.tight_layout()
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(OUTPUT, dpi=180, bbox_inches="tight", facecolor="white")
    print(OUTPUT)


if __name__ == "__main__":
    main()
