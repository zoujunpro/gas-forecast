"""使用训练请求中的同一份数据，直接调用指定的原始城燃脚本做独立回测。"""

from __future__ import annotations

import argparse
import importlib.util
import json
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd


def load_engine(path: Path):
    spec = importlib.util.spec_from_file_location("city_gas_original_engine", path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"无法加载模型脚本：{path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def metrics(points: pd.DataFrame) -> dict[str, float]:
    actual = points["y"].astype(float).to_numpy()
    predicted = points["yhat"].astype(float).to_numpy()
    error = predicted - actual
    nonzero = actual != 0
    return {
        "rmse": float(np.sqrt(np.mean(np.square(error)))),
        "mae": float(np.mean(np.abs(error))),
        "mape": float(np.mean(np.abs(error[nonzero] / actual[nonzero])) * 100),
        "wmape": float(np.sum(np.abs(error)) / np.sum(np.abs(actual)) * 100),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("request_json")
    parser.add_argument("model_script")
    parser.add_argument("--output-dir", default="outputs/city-gas-backtest-verification")
    args = parser.parse_args()

    payload = json.loads(Path(args.request_json).read_text(encoding="utf-8"))
    rows = payload["dataset"]
    engine = load_engine(Path(args.model_script))
    frame = pd.DataFrame(rows).rename(columns={"date": "ds", "gas_sales": "y"})
    frame["ds"] = pd.to_datetime(frame["ds"])
    frame["y"] = pd.to_numeric(frame["y"], errors="coerce")
    regs = [column for column in frame.columns if column not in ("ds", "y")]
    frame[regs] = frame[regs].apply(pd.to_numeric, errors="coerce").interpolate(limit_direction="both").ffill().bfill()
    frame, engineered = engine.add_engineered_features(frame, regs)
    regs = list(dict.fromkeys([*regs, *engineered]))
    frame["ds"] -= pd.Timedelta(days=engine.DS_SHIFT_DAYS)
    frame = frame.dropna(subset=["ds", "y"]).sort_values("ds").reset_index(drop=True)

    params = {
        "seasonality_mode": "additive",
        "changepoint_prior_scale": 0.05,
        "seasonality_prior_scale": 10.0,
        "holidays_prior_scale": 20.0,
    }
    end = frame["ds"].max()
    start = max(frame["ds"].min() + pd.Timedelta(days=90), end - pd.Timedelta(days=119))
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    results: dict[str, tuple[pd.DataFrame, dict[str, float]]] = {}
    # 原始目录脚本把首日漏算，min_train_days=90 实际要求91条训练数据。
    # 传89可得到包含首日在内的90条训练数据，与平台修正后的语义一致。
    original_script_min_train_offset = 89
    for version in ("v1", "v3"):
        points, _, _ = engine.rolling_backtest(
            frame, regs, 50, "D", original_script_min_train_offset, 50, start, end, params, 4, version
        )
        points = points.copy()
        points["date"] = points["ds"] + pd.Timedelta(days=engine.DS_SHIFT_DAYS)
        score = metrics(points)
        points[["date", "y", "yhat", "yhat_lower", "yhat_upper", "cutoff"]].to_csv(
            output_dir / f"{version}_backtest.csv", index=False
        )
        results[version] = points, score

    summary = pd.DataFrame([{"version": version, **score} for version, (_, score) in results.items()])
    summary.to_csv(output_dir / "metrics.csv", index=False)

    fig, axes = plt.subplots(2, 1, figsize=(16, 11), sharex=True)
    for axis, (version, (points, score)) in zip(axes, results.items()):
        axis.plot(points["date"], points["y"], label="Actual", color="#2563eb", marker="o", markersize=3)
        axis.plot(points["date"], points["yhat"], label="Predicted", color="#16a34a", marker="o", markersize=3)
        axis.axhline(0, color="#64748b", linewidth=0.8)
        axis.set_title(f"{version.upper()}  RMSE={score['rmse']:.2f}  MAPE={score['mape']:.2f}%  WMAPE={score['wmape']:.2f}%")
        axis.set_ylabel("Gas sales")
        axis.grid(alpha=0.2)
        axis.legend()
    axes[-1].set_xlabel("Date")
    fig.suptitle("City gas backtest verification - same 140 database rows", fontsize=15)
    fig.tight_layout()
    fig.savefig(output_dir / "backtest_comparison.png", dpi=170, bbox_inches="tight")

    print(summary.to_string(index=False))
    print(f"rows={len(frame)} start={frame['ds'].min().date()} end={frame['ds'].max().date()}")
    print(f"features={','.join(regs)}")
    print(f"output={output_dir.resolve()}")


if __name__ == "__main__":
    main()
