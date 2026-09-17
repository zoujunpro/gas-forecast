#!/usr/bin/env python3
import csv
import json
import uuid
from pathlib import Path


SOURCE_ROOT = Path("/Users/zoujun/work/workspace/shiyou/winner-agent/output")
TARGET = Path("forecast-bussiness-dao/src/main/resources/sql/01_seed_winner_agent.sql")

CHART_NAMES = {
    "北京": "beijing_forecast.png",
    "天津": "tianjin_forecast.png",
    "山东": "shandong_forecast.png",
    "山西": "shanxi_forecast.png",
    "河北": "hebei_forecast.png",
    "河南": "henan_forecast.png",
    "陕西": "shaanxi_forecast.png",
}

AREA_MAP = {
    "北京": "华北区域",
    "天津": "华北区域",
    "河北": "华北区域",
    "山西": "华北区域",
    "山东": "华东区域",
    "河南": "华中区域",
    "陕西": "西北区域",
}

CUSTOMER_TEMPLATES = [
    ("城燃客户", "city_gas", 0.46),
    ("工业客户", "industry", 0.34),
    ("电厂客户", "power", 0.20),
]

UUID_NAMESPACE = uuid.UUID("3a2d9c90-cb57-4d5e-91d7-d3dc7810e9b1")


def sql_string(value):
    if value is None:
        return "null"
    text = str(value)
    if text == "":
        return "null"
    return "'" + text.replace("\\", "\\\\").replace("'", "''") + "'"


def sql_decimal(value):
    if value is None or str(value).strip() == "":
        return "null"
    return str(value).strip()


def sql_int(value):
    if value is None or str(value).strip() == "":
        return "null"
    return str(int(float(str(value).strip())))


def stable_uuid(*parts):
    return str(uuid.uuid5(UUID_NAMESPACE, "::".join(str(part) for part in parts)))


def read_csv(path):
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def emit_insert(table, columns, rows):
    if not rows:
        return []
    lines = [f"insert into {table} ({', '.join(columns)}) values"]
    values = []
    for row in rows:
        values.append("    (" + ", ".join(row[column] for column in columns) + ")")
    lines.append(",\n".join(values) + ";")
    return lines


def main():
    provinces = sorted([path.name for path in SOURCE_ROOT.iterdir() if path.is_dir()])
    output = [
        "use gas_data;",
        "",
        "set names utf8mb4;",
        "",
        "delete from gas_backtest_detail;",
        "delete from gas_feature_rank;",
        "delete from gas_model_rank;",
        "delete from gas_customer_forecast_point;",
        "delete from gas_forecast_point;",
        "delete from gas_forecast_summary;",
        "delete from gas_customer;",
        "delete from gas_province;",
        "delete from gas_area;",
        "",
    ]

    area_rows = []
    province_rows = []
    customer_rows = []
    summary_rows = []
    forecast_rows = []
    customer_forecast_rows = []
    model_rows = []
    feature_rows = []
    backtest_rows = []

    areas = sorted(set(AREA_MAP[province] for province in provinces))
    area_codes = {area: stable_uuid("area", area) for area in areas}
    province_codes = {province: stable_uuid("province", province) for province in provinces}
    customer_codes = {}

    for area in areas:
        area_rows.append({
            "area_code": sql_string(area_codes[area]),
            "area_name": sql_string(area),
        })

    for province in provinces:
        area_name = AREA_MAP[province]
        province_rows.append({
            "province_code": sql_string(province_codes[province]),
            "province_name": sql_string(province),
            "area_code": sql_string(area_codes[area_name]),
        })
        for label, customer_type, ratio in CUSTOMER_TEMPLATES:
            customer_name = f"{province}{label}"
            customer_code = stable_uuid("customer", province, customer_type)
            customer_codes[(province, customer_type)] = customer_code
            customer_rows.append({
                "customer_code": sql_string(customer_code),
                "customer_name": sql_string(customer_name),
                "area_code": sql_string(area_codes[area_name]),
                "province_code": sql_string(province_codes[province]),
                "customer_type": sql_string(customer_type),
                "split_ratio": sql_decimal(ratio),
            })

    for province in provinces:
        province_dir = SOURCE_ROOT / province
        area_name = AREA_MAP[province]
        plan = json.loads((province_dir / "best_plan.json").read_text(encoding="utf-8"))
        report = (province_dir / "report.md").read_text(encoding="utf-8")
        summary_rows.append({
            "province_code": sql_string(province_codes[province]),
            "province": sql_string(province),
            "best_model": sql_string(plan.get("best_model")),
            "backtest_mape": sql_decimal(plan.get("backtest_MAPE")),
            "backtest_wmape": sql_decimal(plan.get("backtest_WMAPE")),
            "backtest_rmse": sql_decimal(plan.get("backtest_RMSE")),
            "feature_count": sql_int(plan.get("feature_count")),
            "forecast_horizon": sql_int(plan.get("forecast_horizon")),
            "weather_source": sql_string(plan.get("weather_source")),
            "report_text": sql_string(report),
            "chart_path": sql_string(f"./assets/{CHART_NAMES.get(province, '')}"),
        })

        for row in read_csv(province_dir / "forecast_15旬.csv"):
            forecast_rows.append({
                "province_code": sql_string(province_codes[province]),
                "province": sql_string(province),
                "forecast_date": sql_string(row["date"]),
                "tenday_label": sql_string(row["tenday_label"]),
                "avg_temp": sql_decimal(row["avg_temp"]),
                "max_temp": sql_decimal(row["max_temp"]),
                "min_temp": sql_decimal(row["min_temp"]),
                "hdd": sql_decimal(row["HDD"]),
                "extreme_cold_days": sql_int(row["extreme_cold_days"]),
                "weather_source": sql_string(row["weather_source"]),
                "prediction": sql_decimal(row["prediction"]),
                "lower_value": sql_decimal(row["lower"]),
                "upper_value": sql_decimal(row["upper"]),
            })
            for _, customer_type, ratio in CUSTOMER_TEMPLATES:
                customer_forecast_rows.append({
                    "area_code": sql_string(area_codes[area_name]),
                    "province_code": sql_string(province_codes[province]),
                    "customer_code": sql_string(customer_codes[(province, customer_type)]),
                    "forecast_date": sql_string(row["date"]),
                    "tenday_label": sql_string(row["tenday_label"]),
                    "prediction": sql_decimal(float(row["prediction"]) * ratio),
                    "lower_value": sql_decimal(float(row["lower"]) * ratio),
                    "upper_value": sql_decimal(float(row["upper"]) * ratio),
                })

        for row in read_csv(province_dir / "model_rank.csv"):
            model_rows.append({
                "province": sql_string(province),
                "model_name": sql_string(row["model"]),
                "model_type": sql_string(row["type"]),
                "mape": sql_decimal(row["MAPE"]),
                "wmape": sql_decimal(row["WMAPE"]),
                "smape": sql_decimal(row["sMAPE"]),
                "rmse": sql_decimal(row["RMSE"]),
                "mae": sql_decimal(row["MAE"]),
                "r2": sql_decimal(row["R2"]),
                "constituents": sql_string(row["constituents"]),
            })

        for row in read_csv(province_dir / "feature_rank.csv"):
            feature_rows.append({
                "province": sql_string(province),
                "feature_name": sql_string(row["feature"]),
                "score": sql_decimal(row["score"]),
                "tree_importance": sql_decimal(row["tree_importance"]),
                "mutual_info": sql_decimal(row["mutual_info"]),
            })

        detail_file = province_dir / f"{province}_近三年回滚预测明细.csv"
        if detail_file.exists():
            for row in read_csv(detail_file):
                backtest_rows.append({
                    "province": sql_string(province),
                    "model_name": sql_string(row["model"]),
                    "season": sql_string(row["season"]),
                    "test_date": sql_string(row["date"]),
                    "actual": sql_decimal(row["actual"]),
                    "prediction": sql_decimal(row["prediction"]),
                    "absolute_error": sql_decimal(row["absolute_error"]),
                    "ape_pct": sql_decimal(row["APE_pct"]),
                })

    output.extend(emit_insert("gas_area", ["area_code", "area_name"], area_rows))
    output.append("")
    output.extend(emit_insert("gas_province", ["province_code", "province_name", "area_code"], province_rows))
    output.append("")
    output.extend(emit_insert("gas_customer", [
        "customer_code", "customer_name", "area_code", "province_code", "customer_type", "split_ratio"
    ], customer_rows))
    output.append("")
    output.extend(emit_insert("gas_forecast_summary", [
        "province_code", "province", "best_model", "backtest_mape", "backtest_wmape", "backtest_rmse",
        "feature_count", "forecast_horizon", "weather_source", "report_text", "chart_path"
    ], summary_rows))
    output.append("")
    output.extend(emit_insert("gas_forecast_point", [
        "province_code", "province", "forecast_date", "tenday_label", "avg_temp", "max_temp", "min_temp", "hdd",
        "extreme_cold_days", "weather_source", "prediction", "lower_value", "upper_value"
    ], forecast_rows))
    output.append("")
    output.extend(emit_insert("gas_customer_forecast_point", [
        "area_code", "province_code", "customer_code", "forecast_date", "tenday_label", "prediction", "lower_value", "upper_value"
    ], customer_forecast_rows))
    output.append("")
    output.extend(emit_insert("gas_model_rank", [
        "province", "model_name", "model_type", "mape", "wmape", "smape", "rmse", "mae", "r2", "constituents"
    ], model_rows))
    output.append("")
    output.extend(emit_insert("gas_feature_rank", [
        "province", "feature_name", "score", "tree_importance", "mutual_info"
    ], feature_rows))
    output.append("")
    output.extend(emit_insert("gas_backtest_detail", [
        "province", "model_name", "season", "test_date", "actual", "prediction", "absolute_error", "ape_pct"
    ], backtest_rows))
    output.append("")

    TARGET.write_text("\n".join(output), encoding="utf-8")


if __name__ == "__main__":
    main()
