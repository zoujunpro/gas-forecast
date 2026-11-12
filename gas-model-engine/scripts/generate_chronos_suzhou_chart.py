from __future__ import annotations

import subprocess
from pathlib import Path

import numpy as np
import pandas as pd
from chronos import Chronos2Pipeline


ROOT = Path(__file__).resolve().parents[2]
MODEL = ROOT / "gas-model-engine/.models/amazon-chronos-2"
OUTPUT = ROOT / "gas-model-engine/output"
VALIDATION_DIR = ROOT / "outputs/suzhou-city-gas-validation"


def mape(actual: np.ndarray, predicted: np.ndarray) -> float:
    mask = np.abs(actual) > 1e-8
    return float(np.mean(np.abs((actual[mask] - predicted[mask]) / actual[mask])) * 100)


def main() -> None:
    sql = (
        "SELECT s.stat_date,CAST(s.gas_sales AS DECIMAL(18,6)),"
        "w.temp_avg,w.temp_max,w.temp_min,w.humidity,w.precipitation,w.wind_speed,"
        "COALESCE(w.hdd,GREATEST(18-w.temp_avg,0)) "
        "FROM data_daily_sales_tb s LEFT JOIN data_region_weather_daily_tb w "
        "ON w.stat_date=s.stat_date AND w.region_code='REG000012' AND w.weather_type='ACTUAL' "
        "WHERE s.customer_code='CUS000240' ORDER BY s.stat_date"
    )
    query = subprocess.run(
        ["docker", "exec", "mysql-dev", "mysql", "-uroot", "-pmysql2026", "--batch", "--raw", "--skip-column-names", "gas_data", "-e", sql],
        check=True,
        capture_output=True,
        text=True,
    ).stdout
    database_rows = [line.split("\t") for line in query.splitlines() if line.strip()]
    weather_columns = ["temp_avg", "temp_max", "temp_min", "humidity", "precipitation", "wind_speed", "hdd"]
    history = pd.DataFrame(database_rows, columns=["timestamp", "target", *weather_columns])
    history["timestamp"] = pd.to_datetime(history["timestamp"])
    for column in ["target", *weather_columns]:
        history[column] = pd.to_numeric(history[column], errors="coerce")
    history[weather_columns] = history[weather_columns].interpolate(limit_direction="both").ffill().bfill()
    day = history["timestamp"].dt.dayofweek
    month = history["timestamp"].dt.month
    history["dow_sin"] = np.sin(2 * np.pi * day / 7)
    history["dow_cos"] = np.cos(2 * np.pi * day / 7)
    history["month_sin"] = np.sin(2 * np.pi * month / 12)
    history["month_cos"] = np.cos(2 * np.pi * month / 12)
    history["is_weekend"] = (day >= 5).astype(float)
    calendar_columns = ["dow_sin", "dow_cos", "month_sin", "month_cos", "is_weekend"]
    cny_dates = pd.to_datetime(["2022-02-01", "2023-01-22", "2024-02-10", "2025-01-29", "2026-02-17"])
    national_dates = pd.to_datetime([f"{year}-10-01" for year in range(2022, 2027)])
    history["days_to_cny"] = np.min(np.abs((history["timestamp"].to_numpy()[:, None] - cny_dates.to_numpy()[None, :]).astype("timedelta64[D]").astype(int)), axis=1).astype(float)
    history["days_to_national_day"] = np.min(np.abs((history["timestamp"].to_numpy()[:, None] - national_dates.to_numpy()[None, :]).astype("timedelta64[D]").astype(int)), axis=1).astype(float)
    history["is_holiday_window"] = 0.0
    for year in range(2022, 2027):
        windows = [
            (pd.Timestamp(f"{year}-01-01"), 0, 2),
            (pd.Timestamp(f"{year}-05-01"), -2, 4),
            (pd.Timestamp(f"{year}-10-01"), -2, 8),
        ]
        cny = cny_dates[cny_dates.year == year]
        if len(cny):
            windows.append((cny[0], -10, 15))
        for center, lower, upper in windows:
            history.loc[history["timestamp"].between(center + pd.Timedelta(days=lower), center + pd.Timedelta(days=upper)), "is_holiday_window"] = 1.0
    holiday_columns = ["days_to_cny", "days_to_national_day", "is_holiday_window"]
    covariates = [*weather_columns, *calendar_columns, *holiday_columns]
    history["id"] = "苏州燃气"

    old = pd.read_csv(VALIDATION_DIR / "holdout-predictions.csv", encoding="utf-8-sig")
    old = old.rename(columns={"date": "timestamp", "old_prediction": "legacy_v1"})[["timestamp", "legacy_v1"]]
    old["timestamp"] = pd.to_datetime(old["timestamp"])
    v3 = pd.read_csv(OUTPUT / "suzhou-v3-database-holdout.csv", encoding="utf-8-sig")
    v3 = v3.rename(columns={"yhat": "legacy_v3"})[["timestamp", "legacy_v3"]]
    v3["timestamp"] = pd.to_datetime(v3["timestamp"])
    holdout_start, holdout_end = old["timestamp"].min(), old["timestamp"].max()
    first = int(history.index[history["timestamp"] == holdout_start][0])
    last = int(history.index[history["timestamp"] == holdout_end][0]) + 1

    pipeline = Chronos2Pipeline.from_pretrained(str(MODEL), device_map="cpu")
    rows: list[pd.DataFrame] = []
    horizon = 10
    for start in range(first, last, horizon):
        context = history.iloc[:start].copy()
        actual = history.iloc[start : start + horizon].copy()
        basic = pipeline.predict_df(
            context[["id", "timestamp", "target"]],
            prediction_length=len(actual),
            quantile_levels=[0.1, 0.5, 0.9],
            id_column="id",
            timestamp_column="timestamp",
            target="target",
        )
        enriched = pipeline.predict_df(
            context[["id", "timestamp", "target", *covariates]],
            future_df=actual[["id", "timestamp", *covariates]],
            prediction_length=len(actual),
            quantile_levels=[0.1, 0.5, 0.9],
            id_column="id",
            timestamp_column="timestamp",
            target="target",
        )
        weather_only = pipeline.predict_df(
            context[["id", "timestamp", "target", *weather_columns]],
            future_df=actual[["id", "timestamp", *weather_columns]],
            prediction_length=len(actual), quantile_levels=[0.5],
            id_column="id", timestamp_column="timestamp", target="target",
        )
        calendar_only = pipeline.predict_df(
            context[["id", "timestamp", "target", *calendar_columns]],
            future_df=actual[["id", "timestamp", *calendar_columns]],
            prediction_length=len(actual), quantile_levels=[0.5],
            id_column="id", timestamp_column="timestamp", target="target",
        )
        holiday_only = pipeline.predict_df(
            context[["id", "timestamp", "target", *holiday_columns]],
            future_df=actual[["id", "timestamp", *holiday_columns]],
            prediction_length=len(actual), quantile_levels=[0.5],
            id_column="id", timestamp_column="timestamp", target="target",
        )
        point_column = "predictions" if "predictions" in basic else "0.5"
        part = actual[["timestamp", "target"]].rename(columns={"target": "actual"})
        part["chronos"] = basic[point_column].to_numpy(dtype=float)
        part["chronos_covariates"] = enriched[point_column].to_numpy(dtype=float)
        part["chronos_weather"] = weather_only[point_column].to_numpy(dtype=float)
        part["chronos_calendar"] = calendar_only[point_column].to_numpy(dtype=float)
        part["chronos_holiday"] = holiday_only[point_column].to_numpy(dtype=float)
        rows.append(part)

    result = pd.concat(rows, ignore_index=True).merge(old, on="timestamp", how="left").merge(v3, on="timestamp", how="left")
    chronos_mape = mape(result["actual"].to_numpy(), result["chronos"].to_numpy())
    covariates_mape = mape(result["actual"].to_numpy(), result["chronos_covariates"].to_numpy())
    weather_mape = mape(result["actual"].to_numpy(), result["chronos_weather"].to_numpy())
    calendar_mape = mape(result["actual"].to_numpy(), result["chronos_calendar"].to_numpy())
    holiday_mape = mape(result["actual"].to_numpy(), result["chronos_holiday"].to_numpy())
    feature_variants = {
        "天气+日历": ("chronos_covariates", covariates_mape),
        "仅天气": ("chronos_weather", weather_mape),
        "仅日历": ("chronos_calendar", calendar_mape),
        "仅节假日": ("chronos_holiday", holiday_mape),
    }
    best_feature_name, (best_feature_column, best_feature_mape) = min(feature_variants.items(), key=lambda item: item[1][1])
    v1_mape = mape(result["actual"].to_numpy(), result["legacy_v1"].to_numpy())
    v3_mape = mape(result["actual"].to_numpy(), result["legacy_v3"].to_numpy())

    OUTPUT.mkdir(parents=True, exist_ok=True)
    result.to_csv(OUTPUT / "chronos-suzhou-backtest-points.csv", index=False, encoding="utf-8-sig")

    width, height = 1500, 900
    left, right, top, plot_bottom = 105, 1440, 115, 570
    values_all = np.concatenate([result["actual"], result["chronos"], result["legacy_v3"]])
    ymin, ymax = float(values_all.min()), float(values_all.max())
    pad = (ymax - ymin) * 0.08
    ymin, ymax = ymin - pad, ymax + pad
    xs = np.linspace(left, right, len(result))
    sy = lambda value: plot_bottom - (float(value) - ymin) / (ymax - ymin) * (plot_bottom - top)
    points = lambda column: " ".join(f"{x:.1f},{sy(y):.1f}" for x, y in zip(xs, result[column]))
    svg = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        '<rect width="100%" height="100%" fill="white"/>',
        '<style>text{font-family:"PingFang SC","Microsoft YaHei",Arial,sans-serif;fill:#172033}.title{font-size:30px;font-weight:700}.sub{font-size:22px;font-weight:600}.label{font-size:17px}.small{font-size:14px}.grid{stroke:#d9dee7;stroke-width:1}.axis{stroke:#667085;stroke-width:1.2}</style>',
        '<text x="750" y="48" text-anchor="middle" class="title">苏州燃气 · Chronos-2 时序基础模型回测对比</text>',
        '<text x="105" y="88" class="sub">最近50天滚动回测：实际值与预测值</text>',
    ]
    for i in range(6):
        y = top + i * (plot_bottom - top) / 5
        value = ymax - i * (ymax - ymin) / 5
        svg += [f'<line x1="{left}" y1="{y:.1f}" x2="{right}" y2="{y:.1f}" class="grid"/>', f'<text x="{left-12}" y="{y+5:.1f}" text-anchor="end" class="small">{value:.0f}</text>']
    svg += [
        f'<line x1="{left}" y1="{top}" x2="{left}" y2="{plot_bottom}" class="axis"/>',
        f'<line x1="{left}" y1="{plot_bottom}" x2="{right}" y2="{plot_bottom}" class="axis"/>',
        f'<polyline points="{points("actual")}" fill="none" stroke="#172033" stroke-width="4"/>',
        f'<polyline points="{points("chronos")}" fill="none" stroke="#2F75B5" stroke-width="3.5"/>',
        f'<polyline points="{points("legacy_v3")}" fill="none" stroke="#E67E22" stroke-width="2.8" opacity="0.9"/>',
        '<line x1="980" y1="82" x2="1025" y2="82" stroke="#172033" stroke-width="4"/><text x="1035" y="88" class="label">实际值</text>',
        '<line x1="1110" y1="82" x2="1155" y2="82" stroke="#2F75B5" stroke-width="4"/><text x="1165" y="88" class="label">Chronos-2</text>',
        '<line x1="1300" y1="82" x2="1345" y2="82" stroke="#E67E22" stroke-width="4"/><text x="1355" y="88" class="label">V3</text>',
    ]
    for idx in [0, 9, 19, 29, 39, 49]:
        svg.append(f'<text x="{xs[idx]:.1f}" y="{plot_bottom+28}" text-anchor="middle" class="small">{result.iloc[idx]["timestamp"]:%m-%d}</text>')
    bar_top, bar_bottom = 680, 830
    max_metric = max(chronos_mape, v3_mape) * 1.2
    svg.append('<text x="105" y="640" class="sub">同一50天窗口 MAPE 对比（越低越好）</text>')
    for x, label, value, color in [(480, "Chronos-2", chronos_mape, "#2F75B5"), (1020, "指定脚本 V3", v3_mape, "#E67E22")]:
        bar_height = value / max_metric * (bar_bottom - bar_top)
        y = bar_bottom - bar_height
        svg += [f'<rect x="{x-115}" y="{y:.1f}" width="230" height="{bar_height:.1f}" fill="{color}"/>', f'<text x="{x}" y="{y-14:.1f}" text-anchor="middle" class="sub">{value:.2f}%</text>', f'<text x="{x}" y="862" text-anchor="middle" class="label">{label}</text>']
    svg.append('</svg>')
    path = OUTPUT / "chronos-suzhou-backtest-comparison.svg"
    path.write_text("\n".join(svg), encoding="utf-8")
    print(path)
    print(f"Chronos-2 MAPE={chronos_mape:.4f}%")
    print(f"Chronos-2 + 特征 MAPE={covariates_mape:.4f}%")
    print(f"Chronos-2 + 天气 MAPE={weather_mape:.4f}%")
    print(f"Chronos-2 + 日历 MAPE={calendar_mape:.4f}%")
    print(f"Chronos-2 + 节假日 MAPE={holiday_mape:.4f}%")
    print(f"最佳特征组合={best_feature_name} ({best_feature_mape:.4f}%)")
    print(f"旧 Pipeline V1 MAPE={v1_mape:.4f}%")
    print(f"指定脚本 V3 MAPE={v3_mape:.4f}%")


if __name__ == "__main__":
    main()
