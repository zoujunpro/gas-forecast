"""使用数据库中苏州燃气最后10天做一次完整短期客户Pipeline回测。"""

from __future__ import annotations

import subprocess
from pathlib import Path

import numpy as np
import pandas as pd

from gas_model_engine import AlgorithmDataset, ForecastRequest, create_default_forecast_pipeline


OUTPUT = Path(__file__).resolve().parents[1] / "output"
CUSTOMER_CODE = "CUS000240"
REGION_CODE = "REG000012"
HORIZON = 10


def load_data() -> pd.DataFrame:
    sql = (
        "SELECT s.stat_date,CAST(s.gas_sales AS DECIMAL(18,6)),"
        "w.temp_avg,w.temp_max,w.temp_min,w.humidity,w.precipitation,w.wind_speed,"
        "COALESCE(w.hdd,GREATEST(18-w.temp_avg,0)) "
        "FROM data_daily_sales_tb s LEFT JOIN data_region_weather_daily_tb w "
        "ON w.stat_date=s.stat_date AND w.region_code='REG000012' "
        "AND w.weather_type='ACTUAL' "
        "WHERE s.customer_code='CUS000240' ORDER BY s.stat_date"
    )
    result = subprocess.run(
        [
            "docker", "exec", "mysql-dev", "mysql", "-uroot", "-pmysql2026",
            "--batch", "--raw", "--skip-column-names", "gas_data", "-e", sql,
        ],
        check=True,
        capture_output=True,
        text=True,
    )
    rows = [line.split("\t") for line in result.stdout.splitlines() if line.strip()]
    columns = ["ds", "target", "temp", "tempmax", "tempmin", "humidity", "precip", "windspeed", "hdd"]
    frame = pd.DataFrame(rows, columns=columns)
    frame["ds"] = pd.to_datetime(frame["ds"])
    for column in columns[1:]:
        frame[column] = pd.to_numeric(frame[column], errors="coerce")
    return frame


def mape(actual: np.ndarray, predicted: np.ndarray) -> float:
    valid = np.abs(actual) > 1e-8
    return float(np.mean(np.abs((actual[valid] - predicted[valid]) / actual[valid])) * 100)


def main() -> None:
    frame = load_data()
    train = frame.iloc[:-HORIZON].reset_index(drop=True)
    holdout = frame.iloc[-HORIZON:].reset_index(drop=True)
    # 与旧V3脚本保持一致：整列没有任何观测值的天气字段不能参与训练。
    feature_columns = [
        column
        for column in frame.columns
        if column not in {"ds", "target"} and not frame[column].isna().all()
    ]
    metadata = {"industry_name": "城燃", "customer_code": CUSTOMER_CODE, "region_code": REGION_CODE}
    history = AlgorithmDataset(
        features=train[feature_columns], dates=train["ds"], target=train["target"], metadata=metadata
    )
    future = AlgorithmDataset(
        features=holdout[feature_columns], dates=holdout["ds"], metadata=metadata
    )

    result = create_default_forecast_pipeline().run(
        ForecastRequest("short-term", history, future)
    )
    actual = holdout["target"].to_numpy(dtype=float)
    scores = {
        name: mape(actual, prediction.values)
        for name, prediction in result.prediction.predictions.items()
    }
    final = result.prediction.final_prediction.values
    output = holdout[["ds", "target"]].rename(columns={"target": "actual"}).copy()
    for name, prediction in result.prediction.predictions.items():
        output[name] = prediction.values
    output["selected_prediction"] = final
    output.to_csv(OUTPUT / "suzhou-current-pipeline-holdout-10d.csv", index=False, encoding="utf-8-sig")

    print(f"holdout={holdout['ds'].min().date()}..{holdout['ds'].max().date()}")
    print(f"selected={','.join(result.selected_candidate_ids)}")
    print(f"selected_mape={mape(actual, final):.6f}%")
    for name, score in sorted(scores.items(), key=lambda item: item[1]):
        print(f"{name}={score:.6f}%")


if __name__ == "__main__":
    main()
