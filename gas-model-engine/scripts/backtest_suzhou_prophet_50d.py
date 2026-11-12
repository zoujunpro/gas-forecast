"""将数据库最后50天留作验证，生成Prophet预测值与真实值对比图。"""

from __future__ import annotations

import subprocess
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd

from gas_model_engine import AlgorithmDataset, ForecastRequest, create_default_forecast_pipeline
from gas_model_engine.engine import AlgorithmTrainRequest


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "output"
HORIZON = 50


def load_data() -> pd.DataFrame:
    sql = (
        "SELECT s.stat_date,CAST(s.gas_sales AS DECIMAL(18,6)),"
        "w.temp_avg,w.temp_max,w.temp_min,COALESCE(w.hdd,GREATEST(18-w.temp_avg,0)) "
        "FROM data_daily_sales_tb s LEFT JOIN data_region_weather_daily_tb w "
        "ON w.stat_date=s.stat_date AND w.region_code='REG000012' AND w.weather_type='ACTUAL' "
        "WHERE s.customer_code='CUS000240' ORDER BY s.stat_date"
    )
    result = subprocess.run(
        [
            "docker", "exec", "mysql-dev", "mysql", "-uroot", "-pmysql2026",
            "--batch", "--raw", "--skip-column-names", "gas_data", "-e", sql,
        ],
        check=True, capture_output=True, text=True,
    )
    rows = [line.split("\t") for line in result.stdout.splitlines() if line.strip()]
    frame = pd.DataFrame(rows, columns=["ds", "target", "temp", "tempmax", "tempmin", "hdd"])
    frame["ds"] = pd.to_datetime(frame["ds"])
    for column in frame.columns[1:]:
        frame[column] = pd.to_numeric(frame[column], errors="coerce")
    return frame


def main() -> None:
    frame = load_data()
    history_frame = frame.iloc[:-HORIZON].reset_index(drop=True)
    holdout = frame.iloc[-HORIZON:].reset_index(drop=True)
    feature_columns = ["temp", "tempmax", "tempmin", "hdd"]
    metadata = {"industry_name": "城燃", "customer_code": "CUS000240", "region_code": "REG000012"}
    history = AlgorithmDataset(
        history_frame[feature_columns], history_frame["ds"], history_frame["target"], metadata=metadata
    )
    future = AlgorithmDataset(holdout[feature_columns], holdout["ds"], metadata=metadata)

    pipeline = create_default_forecast_pipeline()
    profile = pipeline.profile_registry.get("short-term")
    scenario = pipeline.scenario_registry.get(profile.scenario_code)
    request = scenario.prepare_request(ForecastRequest("short-term", history, future), profile)
    feature_specs, _ = scenario._resolve_features(request, profile)
    prepared_train, prepared_future = scenario.feature_processor.prepare(
        request.train_data, request.future_data, feature_specs
    )
    params = {
        "yearly_seasonality": "auto",
        "weekly_seasonality": "auto",
        "daily_seasonality": False,
        "seasonality_mode": "additive",
        "changepoint_prior_scale": 0.05,
        "seasonality_prior_scale": 10.0,
        "holidays_prior_scale": 10.0,
    }
    engine = pipeline.profile_executor.algorithm_engine
    model = engine.train(AlgorithmTrainRequest("PROPHET_V1", prepared_train, params))
    prediction = scenario._predict_range(
        engine, model, request.train_data, request.future_data, prepared_future, feature_specs
    ).values
    actual = holdout["target"].to_numpy(dtype=float)
    mape = float(np.mean(np.abs((actual - prediction) / actual)) * 100)
    rmse = float(np.sqrt(np.mean((actual - prediction) ** 2)))

    result = pd.DataFrame({"ds": holdout["ds"], "actual": actual, "prediction": prediction})
    result["absolute_percentage_error"] = np.abs((actual - prediction) / actual) * 100
    csv_path = OUTPUT / "suzhou-prophet-backtest-50d.csv"
    result.to_csv(csv_path, index=False, encoding="utf-8-sig")

    plt.rcParams.update(
        {
            "font.family": ["Arial Unicode MS", "PingFang SC", "Heiti SC", "DejaVu Sans"],
            "figure.facecolor": "white", "axes.facecolor": "white", "savefig.facecolor": "white",
        }
    )
    fig, ax = plt.subplots(figsize=(16, 7.5))
    ax.plot(result["ds"], result["actual"], color="#111111", linewidth=2.8, marker="o", markersize=3.5, label="Actual")
    ax.plot(result["ds"], result["prediction"], color="#1677ff", linewidth=2.5, marker="o", markersize=3, label="Prophet backtest")
    ax.fill_between(result["ds"], result["actual"], result["prediction"], color="#1677ff", alpha=0.08)
    ax.set_title(f"Suzhou City Gas - Prophet 50-Day Backtest | MAPE {mape:.2f}%", fontsize=18)
    ax.set_xlabel("Date")
    ax.set_ylabel("Daily gas sales")
    ax.grid(True, color="#dddddd", linewidth=0.8)
    ax.legend(frameon=False)
    ax.tick_params(axis="x", rotation=25)
    fig.text(0.5, 0.015, f"Backtest: {result['ds'].min().date()} to {result['ds'].max().date()} | RMSE {rmse:.2f}", ha="center", color="#555555")
    plt.tight_layout(rect=[0, 0.035, 1, 1])
    png_path = OUTPUT / "suzhou-prophet-backtest-50d.png"
    fig.savefig(png_path, dpi=180, bbox_inches="tight", facecolor="white")

    print(f"range={result['ds'].min().date()}..{result['ds'].max().date()}")
    print(f"mape={mape:.6f}%")
    print(f"rmse={rmse:.6f}")
    print(csv_path)
    print(png_path)


if __name__ == "__main__":
    main()
