"""固定使用回测最优Prophet，预测苏州燃气未来50天。"""

from __future__ import annotations

import subprocess
from dataclasses import replace
from pathlib import Path

import matplotlib.pyplot as plt
import pandas as pd

from gas_model_engine import (
    AlgorithmDataset,
    AlgorithmProfileRegistry,
    ForecastRequest,
    create_default_forecast_pipeline,
)
from gas_model_engine.profiles.short_term import SHORT_TERM_PROFILE


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "output"
HORIZON = 50


def load_history() -> pd.DataFrame:
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


def build_future_weather(history: pd.DataFrame) -> pd.DataFrame:
    dates = pd.date_range(history["ds"].max() + pd.Timedelta(days=1), periods=HORIZON)
    weather = ["temp", "tempmax", "tempmin", "hdd"]
    source = history.copy()
    source["month_day"] = source["ds"].dt.strftime("%m-%d")
    climatology = source.groupby("month_day")[weather].mean()
    future = pd.DataFrame({"ds": dates})
    future["month_day"] = future["ds"].dt.strftime("%m-%d")
    future = future.join(climatology, on="month_day").drop(columns="month_day")
    fallback = source[weather].tail(60).mean()
    future[weather] = future[weather].fillna(fallback)
    # HDD与本次估计温度保持一致，避免两个天气信号互相矛盾。
    future["hdd"] = (18.0 - future["temp"]).clip(lower=0)
    return future


def main() -> None:
    history = load_history()
    future = build_future_weather(history)
    feature_columns = ["temp", "tempmax", "tempmin", "hdd"]
    metadata = {"industry_name": "城燃", "customer_code": "CUS000240", "region_code": "REG000012"}
    train_data = AlgorithmDataset(
        features=history[feature_columns], dates=history["ds"], target=history["target"], metadata=metadata
    )
    future_data = AlgorithmDataset(
        features=future[feature_columns], dates=future["ds"], metadata=metadata
    )
    prophet_candidate = next(item for item in SHORT_TERM_PROFILE.candidates if item.id == "prophet")
    prophet_profile = replace(
        SHORT_TERM_PROFILE,
        code="short-term-prophet-only",
        candidates=(prophet_candidate,),
        combinations=(),
    )
    pipeline = create_default_forecast_pipeline(
        profile_registry=AlgorithmProfileRegistry([prophet_profile])
    )
    result = pipeline.run(
        ForecastRequest(prophet_profile.code, train_data, future_data)
    )
    prediction = result.prediction.final_prediction
    output = future.copy()
    output["prediction"] = prediction.values
    if prediction.lower_bounds is not None:
        output["lower"] = prediction.lower_bounds
        output["upper"] = prediction.upper_bounds
    csv_path = OUTPUT / "suzhou-prophet-forecast-50d.csv"
    output.to_csv(csv_path, index=False, encoding="utf-8-sig")

    plt.rcParams.update(
        {
            "font.family": ["Arial Unicode MS", "PingFang SC", "Heiti SC", "DejaVu Sans"],
            "figure.facecolor": "white", "axes.facecolor": "white", "savefig.facecolor": "white",
        }
    )
    recent = history.tail(60)
    fig, ax = plt.subplots(figsize=(15, 7))
    ax.plot(recent["ds"], recent["target"], color="#222222", linewidth=2.3, label="Historical actual (last 60 days)")
    ax.plot(output["ds"], output["prediction"], color="#1677ff", linewidth=2.5, label="Prophet forecast (50 days)")
    if {"lower", "upper"} <= set(output.columns):
        ax.fill_between(output["ds"], output["lower"], output["upper"], color="#1677ff", alpha=0.14, label="80% calibrated interval")
    ax.axvline(history["ds"].max(), color="#e67e22", linestyle="--", linewidth=1.5, label="Forecast starts")
    ax.set_title("Suzhou City Gas - Prophet 50-Day Forecast", fontsize=18)
    ax.set_xlabel("Date")
    ax.set_ylabel("Daily gas sales")
    ax.grid(True, color="#dddddd", linewidth=0.8)
    ax.legend(frameon=False, ncol=2)
    fig.text(0.5, 0.015, "Future weather: historical same-calendar-day average; holiday calendar: confirmed 2026 data", ha="center", color="#555555")
    plt.tight_layout(rect=[0, 0.035, 1, 1])
    png_path = OUTPUT / "suzhou-prophet-forecast-50d.png"
    fig.savefig(png_path, dpi=180, bbox_inches="tight", facecolor="white")

    print(f"range={output['ds'].min().date()}..{output['ds'].max().date()}")
    print(f"min={output['prediction'].min():.6f}")
    print(f"max={output['prediction'].max():.6f}")
    print(f"mean={output['prediction'].mean():.6f}")
    print(csv_path)
    print(png_path)


if __name__ == "__main__":
    main()
