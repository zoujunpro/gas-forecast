from __future__ import annotations

import importlib.util
import json
import subprocess
import time
from pathlib import Path

import pandas as pd


SOURCE = Path("/Users/zoujun/Documents/石油/江苏发电/城市燃气/predict_jiangsu_城燃_v3.py")
OUTPUT = Path("outputs/suzhou-city-gas-history-window-comparison")
CUSTOMER_CODE = "CUS000240"
REGION_CODE = "REG000012"


def load_engine():
    spec = importlib.util.spec_from_file_location("city_gas_original_v3", SOURCE)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"无法加载算法脚本: {SOURCE}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def mysql_rows(sql: str) -> list[list[str]]:
    command = [
        "docker", "exec", "mysql-dev", "mysql", "-uroot", "-pmysql2026",
        "--batch", "--raw", "--skip-column-names", "gas_data", "-e", sql,
    ]
    output = subprocess.run(command, check=True, capture_output=True, text=True).stdout
    return [line.split("\t") for line in output.splitlines() if line.strip()]


def load_data() -> pd.DataFrame:
    sales = mysql_rows(
        "SELECT stat_date,CAST(gas_sales AS DECIMAL(18,6)) "
        "FROM data_daily_sales_tb "
        f"WHERE customer_code='{CUSTOMER_CODE}' ORDER BY stat_date"
    )
    weather = mysql_rows(
        "SELECT stat_date,temp_avg,temp_max,temp_min,COALESCE(hdd,GREATEST(18-temp_avg,0)) "
        "FROM data_region_weather_daily_tb "
        f"WHERE region_code='{REGION_CODE}' AND weather_type='ACTUAL' ORDER BY stat_date"
    )
    sales_frame = pd.DataFrame(sales, columns=["ds", "y"])
    weather_frame = pd.DataFrame(weather, columns=["ds", "temp", "tempmax", "tempmin", "source_hdd"])
    frame = sales_frame.merge(weather_frame, on="ds", how="left")
    frame["ds"] = pd.to_datetime(frame["ds"])
    for column in ["y", "temp", "tempmax", "tempmin", "source_hdd"]:
        frame[column] = pd.to_numeric(frame[column], errors="coerce")
    return frame.sort_values("ds").reset_index(drop=True)


def evaluate(engine, source: pd.DataFrame, label: str) -> dict:
    prepared = source.drop(columns=["source_hdd"]).copy()
    regs_initial = [column for column in prepared.columns if column not in ["ds", "y"]]
    prepared[regs_initial] = prepared[regs_initial].interpolate(limit_direction="both").ffill().bfill()
    prepared, engineered = engine.add_engineered_features(prepared, regs_initial)
    regressors = list(dict.fromkeys([*regs_initial, *engineered]))
    prepared["ds"] = prepared["ds"] - pd.Timedelta(days=engine.DS_SHIFT_DAYS)

    end = prepared["ds"].max()
    start = max(prepared["ds"].min() + pd.Timedelta(days=90), end - pd.Timedelta(days=119))
    params = {
        "seasonality_mode": "additive",
        "changepoint_prior_scale": 0.05,
        "seasonality_prior_scale": 10.0,
        "holidays_prior_scale": 20.0,
    }
    started = time.time()
    points, rmse, mape = engine.rolling_backtest(
        df_all=prepared,
        regs=regressors,
        horizon=50,
        freq="D",
        min_train_days=90,
        stride=50,
        eval_start=start,
        eval_end=end,
        prophet_params=params,
        ar_order=4,
        version="v3",
    )
    elapsed = time.time() - started
    points = points.copy()
    points["stat_date"] = points["ds"] + pd.Timedelta(days=engine.DS_SHIFT_DAYS)
    points["error"] = points["yhat"] - points["y"]
    points["ape"] = points["error"].abs() / points["y"].abs() * 100
    OUTPUT.mkdir(parents=True, exist_ok=True)
    points.to_csv(OUTPUT / f"{label}-backtest.csv", index=False, encoding="utf-8-sig")
    return {
        "window": label,
        "history_rows": int(len(prepared)),
        "history_start": str((prepared["ds"].min() + pd.Timedelta(days=1)).date()),
        "history_end": str((prepared["ds"].max() + pd.Timedelta(days=1)).date()),
        "backtest_rows": int(len(points)),
        "backtest_start": str(points["stat_date"].min().date()),
        "backtest_end": str(points["stat_date"].max().date()),
        "mape": float(mape),
        "rmse": float(rmse),
        "mean_error": float(points["error"].mean()),
        "under_prediction_days": int((points["error"] < 0).sum()),
        "elapsed_seconds": round(elapsed, 3),
        "feature_names": regressors,
    }


def main() -> None:
    engine = load_engine()
    all_data = load_data()
    windows = [365, 730, 1095, 1460]
    comparisons = [
        evaluate(engine, all_data.tail(days).copy(), f"recent-{days}-days")
        for days in windows
        if len(all_data) >= days
    ]
    comparisons.append(evaluate(engine, all_data.copy(), "all-history-1743-days"))
    OUTPUT.mkdir(parents=True, exist_ok=True)
    (OUTPUT / "comparison.json").write_text(
        json.dumps(comparisons, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(json.dumps(comparisons, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
