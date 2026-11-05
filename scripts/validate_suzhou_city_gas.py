from __future__ import annotations

import importlib.util
import json
import subprocess
import sys
import time
from pathlib import Path

import numpy as np
import pandas as pd


ROOT = Path(__file__).resolve().parents[1]
ENGINE_ROOT = ROOT / "gas-model-engine"
sys.path.insert(0, str(ENGINE_ROOT / "src"))

from gas_model_engine import AlgorithmDataset, ForecastRequest, create_default_forecast_pipeline
from gas_model_engine.evaluation import calculate_metrics


LEGACY_SOURCE = Path(
    "/Users/zoujun/Documents/石油/江苏发电/城市燃气/predict_jiangsu_城燃_v3.py"
)
OUTPUT = ROOT / "outputs" / "suzhou-city-gas-validation"
CUSTOMER_CODE = "CUS000240"
REGION_CODE = "REG000012"
HORIZON = 50


def mysql_rows(sql: str) -> list[list[str]]:
    command = [
        "docker", "exec", "mysql-dev", "mysql", "-uroot", "-pmysql2026",
        "--batch", "--raw", "--skip-column-names", "gas_data", "-e", sql,
    ]
    output = subprocess.run(command, check=True, capture_output=True, text=True).stdout
    return [line.split("\t") for line in output.splitlines() if line.strip()]


def load_data() -> pd.DataFrame:
    sales = mysql_rows(
        "SELECT stat_date,CAST(gas_sales AS DECIMAL(18,6)) FROM data_daily_sales_tb "
        f"WHERE customer_code='{CUSTOMER_CODE}' ORDER BY stat_date"
    )
    weather = mysql_rows(
        "SELECT stat_date,temp_avg,temp_max,temp_min,humidity,precipitation,wind_speed,"
        "COALESCE(hdd,GREATEST(18-temp_avg,0)) FROM data_region_weather_daily_tb "
        f"WHERE region_code='{REGION_CODE}' AND weather_type='ACTUAL' ORDER BY stat_date"
    )
    sales_frame = pd.DataFrame(sales, columns=["ds", "y"])
    weather_frame = pd.DataFrame(
        weather,
        columns=["ds", "temp", "tempmax", "tempmin", "humidity", "precip", "windspeed", "hdd"],
    )
    frame = sales_frame.merge(weather_frame, on="ds", how="left")
    frame["ds"] = pd.to_datetime(frame["ds"])
    for column in frame.columns.difference(["ds"]):
        frame[column] = pd.to_numeric(frame[column], errors="coerce")
    numeric = frame.columns.difference(["ds", "y"])
    empty_columns = [column for column in numeric if not frame[column].notna().any()]
    frame = frame.drop(columns=empty_columns)
    numeric = frame.columns.difference(["ds", "y"])
    frame[numeric] = frame[numeric].interpolate(limit_direction="both").ffill().bfill()
    return frame.sort_values("ds").reset_index(drop=True)


def load_legacy():
    spec = importlib.util.spec_from_file_location("legacy_city_gas", LEGACY_SOURCE)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"无法加载旧代码: {LEGACY_SOURCE}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def prepare_legacy(engine, frame):
    prepared = frame.copy()
    regs = [column for column in prepared if column not in ("ds", "y")]
    prepared, engineered = engine.add_engineered_features(prepared, regs)
    regs = list(dict.fromkeys([*regs, *engineered]))
    prepared["ds"] = prepared["ds"] - pd.Timedelta(days=engine.DS_SHIFT_DAYS)
    return prepared, regs


def run_legacy(engine, frame):
    prepared, regs = prepare_legacy(engine, frame)
    train = prepared.iloc[:-HORIZON].copy()
    holdout = prepared.iloc[-HORIZON:].copy()
    params = {
        "seasonality_mode": "additive",
        "changepoint_prior_scale": 0.05,
        "seasonality_prior_scale": 10.0,
        "holidays_prior_scale": 20.0,
    }
    eval_end = train["ds"].max()
    eval_start = eval_end - pd.Timedelta(days=149)
    versions = {}
    started = time.perf_counter()
    for version in ("v1", "v3"):
        points, rmse, mape = engine.rolling_backtest(
            prepared.iloc[:-HORIZON], regs, HORIZON, "D", 90, HORIZON,
            eval_start, eval_end, params, 4, version,
        )
        versions[version] = {"mape": float(mape), "rmse": float(rmse), "points": points}
    winner = min(versions, key=lambda key: versions[key]["mape"])
    future_regs = holdout[["ds", *regs]].copy()
    forecast_fn = engine.run_single_forecast_v3 if winner == "v3" else engine.run_single_forecast_v1
    result, _, _ = forecast_fn(train, regs, HORIZON, "D", params, future_regs, 4)
    future = result[result["ds"].isin(holdout["ds"])].copy()
    future["stat_date"] = future["ds"] + pd.Timedelta(days=engine.DS_SHIFT_DAYS)
    elapsed = time.perf_counter() - started
    prediction = future["yhat"].to_numpy(dtype=float)
    return {
        "winner": winner,
        "versions": versions,
        "prediction": prediction,
        "lower": future["yhat_lower"].to_numpy(dtype=float),
        "upper": future["yhat_upper"].to_numpy(dtype=float),
        "elapsed": elapsed,
    }


def dataset(frame, target=True):
    features = frame.drop(columns=["ds", "y"]).reset_index(drop=True)
    return AlgorithmDataset(
        features=features,
        dates=pd.DatetimeIndex(frame["ds"]),
        target=pd.Series(frame["y"].to_numpy()) if target else None,
        metadata={"industry_name": "城燃", "region_name": "江苏", "customer_name": "苏州燃气"},
    )


def run_new(frame):
    train = frame.iloc[:-HORIZON].copy()
    holdout = frame.iloc[-HORIZON:].copy()
    started = time.perf_counter()
    result = create_default_forecast_pipeline().run(
        ForecastRequest("short-term", dataset(train), dataset(holdout, target=False))
    )
    elapsed = time.perf_counter() - started
    selected = result.selected_candidate_ids[0]
    prediction = result.prediction.final_prediction
    return {
        "selected": selected,
        "prediction": prediction.values,
        "lower": prediction.lower_bounds,
        "upper": prediction.upper_bounds,
        "elapsed": elapsed,
        "backtest_metrics": result.backtest.candidate_metrics,
        "backtest_metadata": result.backtest.metadata,
        "failures": result.prediction.failed_candidates,
        "selected_oof": result.backtest.member_oof_predictions[selected],
    }


def aligned_legacy_backtest(engine, frame, modern):
    """在新Pipeline的Eval日期上重跑旧V1/V3，形成严格同窗对比。"""
    source = frame.iloc[:-HORIZON].copy()
    prepared, regs = prepare_legacy(engine, source)
    metadata = modern["backtest_metadata"]
    first = int(metadata["initial_train_size"])
    step = int(metadata["step_size"])
    size = int(metadata["validation_size"])
    eval_numbers = set(metadata["selection_eval_folds"])
    params = {
        "seasonality_mode": "additive",
        "changepoint_prior_scale": 0.05,
        "seasonality_prior_scale": 10.0,
        "holidays_prior_scale": 20.0,
    }
    output = {}
    for version in ("v1", "v3"):
        values = np.full(len(source), np.nan)
        forecast_fn = (
            engine.run_single_forecast_v3 if version == "v3" else engine.run_single_forecast_v1
        )
        for fold_number in eval_numbers:
            start = first + (fold_number - 1) * step
            end = min(start + size, len(prepared))
            train = prepared.iloc[:start].copy()
            validation = prepared.iloc[start:end].copy()
            result, _, _ = forecast_fn(
                train,
                regs,
                len(validation),
                "D",
                params,
                validation[["ds", *regs]],
                4,
            )
            future = result[result["ds"].isin(validation["ds"])]
            values[start:end] = future["yhat"].to_numpy(dtype=float)
        mask = np.isfinite(values)
        output[version] = {
            "metrics": calculate_metrics(source.loc[mask, "y"], values[mask]),
            "predictions": values,
            "mask": mask,
        }
    modern_oof = np.asarray(modern["selected_oof"], dtype=float)
    modern_mask = np.isfinite(modern_oof)
    eval_mask = np.zeros(len(source), dtype=bool)
    for fold_number in eval_numbers:
        start = first + (fold_number - 1) * step
        eval_mask[start : start + size] = True
    modern_mask &= eval_mask
    modern_metrics = calculate_metrics(source.loc[modern_mask, "y"], modern_oof[modern_mask])
    return output, modern_metrics


def serializable(value):
    if isinstance(value, np.ndarray):
        return value.tolist()
    if isinstance(value, (np.floating, np.integer)):
        return value.item()
    if isinstance(value, pd.DataFrame):
        return value.to_dict(orient="records")
    raise TypeError(type(value).__name__)


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    frame = load_data()
    actual = frame.iloc[-HORIZON:]["y"].to_numpy(dtype=float)
    legacy_engine = load_legacy()
    legacy = run_legacy(legacy_engine, frame)
    modern = run_new(frame)
    aligned_old, aligned_new_metrics = aligned_legacy_backtest(legacy_engine, frame, modern)
    old_metrics = calculate_metrics(actual, legacy["prediction"])
    new_metrics = calculate_metrics(actual, modern["prediction"])
    points = pd.DataFrame(
        {
            "date": frame.iloc[-HORIZON:]["ds"].dt.strftime("%Y-%m-%d"),
            "actual": actual,
            "old_prediction": legacy["prediction"],
            "new_prediction": modern["prediction"],
            "old_lower": legacy["lower"],
            "old_upper": legacy["upper"],
            "new_lower": modern["lower"],
            "new_upper": modern["upper"],
        }
    )
    points.to_csv(OUTPUT / "holdout-predictions.csv", index=False, encoding="utf-8-sig")
    for version, value in legacy["versions"].items():
        value["points"].to_csv(
            OUTPUT / f"legacy-{version}-backtest.csv", index=False, encoding="utf-8-sig"
        )
    report = {
        "data": {
            "customer": "苏州燃气",
            "rows": len(frame),
            "start": str(frame["ds"].min().date()),
            "end": str(frame["ds"].max().date()),
            "train_rows": len(frame) - HORIZON,
            "holdout_rows": HORIZON,
            "holdout_start": str(frame.iloc[-HORIZON]["ds"].date()),
        },
        "legacy": {
            "selected": legacy["winner"],
            "backtest": {
                key: {"mape": value["mape"], "rmse": value["rmse"]}
                for key, value in legacy["versions"].items()
            },
            "holdout": old_metrics,
            "elapsed_seconds": legacy["elapsed"],
        },
        "new": {
            "selected": modern["selected"],
            "backtest": modern["backtest_metrics"],
            "tuning": modern["backtest_metadata"]["tuning"],
            "holdout": new_metrics,
            "elapsed_seconds": modern["elapsed"],
            "failures": modern["failures"],
        },
        "holdout_delta_new_minus_old": {
            key: new_metrics[key] - old_metrics[key] for key in old_metrics
        },
        "aligned_backtest": {
            "dates": (
                "与新Pipeline的Eval折完全一致（折数由历史长度、预测长度和计算预算自动计算）"
            ),
            "execution_plan": {
                key: modern["backtest_metadata"][key]
                for key in (
                    "minimum_train_size", "initial_train_size", "validation_size",
                    "step_size", "fold_count", "tune_folds", "selection_eval_folds",
                )
            },
            "legacy": {
                key: value["metrics"] for key, value in aligned_old.items()
            },
            "new": {
                "selected": modern["selected"],
                "metrics": aligned_new_metrics,
            },
        },
    }
    (OUTPUT / "report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2, default=serializable), encoding="utf-8"
    )
    print(json.dumps(report, ensure_ascii=False, indent=2, default=serializable))


if __name__ == "__main__":
    main()
