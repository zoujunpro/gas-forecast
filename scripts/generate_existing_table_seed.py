#!/usr/bin/env python3
import uuid
from pathlib import Path

import pandas as pd

SOURCE_ROOT = Path("/Users/zoujun/work/workspace/shiyou/winner-agent/data/processed_data1")
TARGET = Path("forecast-bussiness-dao/src/main/resources/sql/02_seed_existing_tables.sql")

AREA_MAP = {
    "北京": "华北区域",
    "天津": "华北区域",
    "河北": "华北区域",
    "山西": "华北区域",
    "山东": "华东区域",
    "河南": "华中区域",
    "陕西": "西北区域",
}

CUSTOMER_TYPES = {
    "city_gas": "城燃",
    "industry": "工业",
    "power": "电厂",
}

UUID_NAMESPACE = uuid.UUID("3a2d9c90-cb57-4d5e-91d7-d3dc7810e9b1")


def stable_uuid(*parts):
    return str(uuid.uuid5(UUID_NAMESPACE, "::".join(str(part) for part in parts)))


def q(value):
    if value is None or str(value) == "nan":
        return "null"
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


def d(value):
    if value is None or pd.isna(value):
        return "null"
    return str(float(value))


def emit_insert(table, columns, rows):
    if not rows:
        return []
    lines = [f"insert into {table} ({', '.join(columns)}) values"]
    lines.append(",\n".join("    (" + ", ".join(row[col] for col in columns) + ")" for row in rows) + ";")
    return lines


def main():
    provinces = [path.stem for path in sorted(SOURCE_ROOT.glob("*.xlsx")) if not path.name.startswith(".~")]
    areas = sorted({AREA_MAP[p] for p in provinces})
    area_codes = {name: stable_uuid("area", name) for name in areas}
    province_codes = {name: stable_uuid("province", name) for name in provinces}
    industry_codes = {name: stable_uuid("industry", name) for name in CUSTOMER_TYPES}

    sql = [
        "use gas_data;",
        "",
        "alter table base_region_tb modify id bigint not null auto_increment;",
        "alter table base_customer_tb modify id bigint not null auto_increment;",
        "alter table base_industry_tb modify id bigint not null auto_increment;",
        "alter table model_forecast_batch_tb modify id bigint unsigned not null auto_increment;",
        "alter table model_forecast_result_tb modify id bigint unsigned not null auto_increment;",
        "alter table model_predict_winter_result_tb modify id bigint unsigned not null auto_increment;",
        "alter table model_train_backtest_tb modify id bigint unsigned not null auto_increment;",
        "alter table model_train_batch_tb modify id bigint unsigned not null auto_increment;",
        "alter table base_region_tb modify region_code varchar(64) not null;",
        "alter table model_forecast_batch_tb modify region_code varchar(64) not null;",
        "alter table model_train_batch_tb modify region_code varchar(64) not null;",
        "alter table model_predict_winter_result_tb modify province_code varchar(64) not null;",
        "",
        "delete from model_train_backtest_tb where train_batch_no like 'WGTRAIN-%';",
        "delete from model_forecast_result_tb where forecast_batch_no like 'WGFC-%';",
        "delete from model_predict_winter_result_tb where batch_id in (select id from model_forecast_batch_tb where batch_no like 'WGFC-%');",
        "delete from model_forecast_batch_tb where batch_no like 'WGFC-%';",
        "delete from model_train_batch_tb where batch_no like 'WGTRAIN-%';",
        "delete from data_winter_tenday_dataset_tb where region_code in (" + ", ".join(q(province_codes[p]) for p in provinces) + ");",
        "delete from base_customer_tb where customer_code in (select customer_code collate utf8mb4_0900_ai_ci from gas_customer);",
        "delete from base_industry_tb where industry_code in (" + ", ".join(q(code) for code in industry_codes.values()) + ");",
        "delete from base_region_tb where region_code in (" + ", ".join(q(area_codes[a]) for a in areas) + ", " + ", ".join(q(province_codes[p]) for p in provinces) + ");",
        "",
    ]

    industry_rows = []
    for idx, (customer_type, name) in enumerate(CUSTOMER_TYPES.items(), 1):
        industry_rows.append({
            "industry_code": q(industry_codes[customer_type]),
            "industry_name": q(name),
            "industry_level": "1",
            "sort_no": str(idx),
            "created_at": "now()",
            "updated_at": "now()",
            "created_by": "1",
            "created_by_name": q("邹军"),
            "updated_by": "1",
            "updated_by_name": q("邹军"),
        })
    sql.extend(emit_insert("base_industry_tb", [
        "industry_code", "industry_name", "industry_level", "sort_no", "created_at", "updated_at",
        "created_by", "created_by_name", "updated_by", "updated_by_name"
    ], industry_rows))
    sql.append("")

    area_rows = []
    for idx, area in enumerate(areas, 1):
        area_rows.append({
            "region_code": q(area_codes[area]),
            "region_name": q(area),
            "region_type": q("AREA"),
            "parent_id": "null",
            "sort_no": str(idx),
            "enabled": "1",
            "remark": q("winner-agent模拟区域"),
            "created_by": q("zoujun"),
            "created_at": "now()",
            "updated_by": q("zoujun"),
            "updated_at": "now()",
            "update_by_name": q("邹军"),
            "created_by_name": q("邹军"),
        })
    sql.extend(emit_insert("base_region_tb", [
        "region_code", "region_name", "region_type", "parent_id", "sort_no", "enabled", "remark",
        "created_by", "created_at", "updated_by", "updated_at", "update_by_name", "created_by_name"
    ], area_rows))
    sql.append("")

    province_rows = []
    for idx, province in enumerate(provinces, 1):
        province_rows.append({
            "region_code": q(province_codes[province]),
            "region_name": q(province),
            "region_type": q("PROVINCE"),
            "parent_id": f"(select id from (select id from base_region_tb where region_code = {q(area_codes[AREA_MAP[province]])} limit 1) t)",
            "sort_no": str(idx),
            "enabled": "1",
            "remark": q("winner-agent模拟省份"),
            "created_by": q("zoujun"),
            "created_at": "now()",
            "updated_by": q("zoujun"),
            "updated_at": "now()",
            "update_by_name": q("邹军"),
            "created_by_name": q("邹军"),
        })
    sql.extend(emit_insert("base_region_tb", [
        "region_code", "region_name", "region_type", "parent_id", "sort_no", "enabled", "remark",
        "created_by", "created_at", "updated_by", "updated_at", "update_by_name", "created_by_name"
    ], province_rows))
    sql.append("")

    sql.append("""insert into base_customer_tb (
    customer_code, customer_name, industry_code, industry_name, region_code, region_name,
    created_at, updated_at, created_by, created_by_name, updated_by, update_by_name,
    raw_region_name, raw_industry_name
)
select c.customer_code,
       c.customer_name,
       case c.customer_type
           when 'city_gas' then '""" + industry_codes["city_gas"] + """'
           when 'industry' then '""" + industry_codes["industry"] + """'
           else '""" + industry_codes["power"] + """'
       end,
       case c.customer_type
           when 'city_gas' then '城燃'
           when 'industry' then '工业'
           else '电厂'
       end,
       c.province_code,
       p.province_name,
       now(),
       now(),
       1,
       '邹军',
       1,
       '邹军',
       p.province_name,
       case c.customer_type
           when 'city_gas' then '城燃'
           when 'industry' then '工业'
           else '电厂'
       end
from gas_customer c
join gas_province p on p.province_code = c.province_code;""")
    sql.append("")

    winter_rows = []
    for province in provinces:
        df = pd.read_excel(SOURCE_ROOT / f"{province}.xlsx")
        for _, row in df.iterrows():
            winter_rows.append({
                "region_code": q(province_codes[province]),
                "region_name": q(province),
                "stat_date": q(pd.to_datetime(row["date"]).strftime("%Y-%m-%d")),
                "gas_sales": d(row["gas_sales"]),
                "avg_temp": d(row["avg_temp"]),
                "max_temp": d(row["max_temp"]),
                "min_temp": d(row["min_temp"]),
                "hdd": d(row["HDD"]),
                "extreme_cold_days": str(int(row["extreme_cold_days"])) if not pd.isna(row["extreme_cold_days"]) else "null",
                "created_at": "now()",
                "updated_at": "now()",
            })
    for start in range(0, len(winter_rows), 500):
        sql.extend(emit_insert("data_winter_tenday_dataset_tb", [
            "region_code", "region_name", "stat_date", "gas_sales", "avg_temp", "max_temp", "min_temp",
            "hdd", "extreme_cold_days", "created_at", "updated_at"
        ], winter_rows[start:start + 500]))
        sql.append("")

    batch_rows = []
    forecast_batch_rows = []
    for province in provinces:
        train_no = f"WGTRAIN-{province}"
        forecast_no = f"WGFC-{province}"
        batch_rows.append({
            "batch_no": q(train_no),
            "agent_code": q("winner-agent"),
            "region_code": q(province_codes[province]),
            "region_name": q(province),
            "customer_code": q("ALL"),
            "customer_name": q("全部客户"),
            "industry_code": q("ALL"),
            "industry_name": q("全部行业"),
            "train_start_date": q("2016-04-01"),
            "train_end_date": q("2026-06-21"),
            "status": q("SUCCESS"),
            "best_model": q(""),
            "mape": "null",
            "wmape": "null",
            "rmse": "null",
            "mae": "null",
            "r2": "null",
            "config_json": q('{"source":"winner-agent"}'),
            "result_json": q('{"createdBy":"邹军"}'),
            "created_by": q("zoujun"),
            "created_by_name": q("邹军"),
            "started_at": "now()",
            "completed_at": "now()",
            "created_at": "now()",
            "updated_at": "now()",
        })
        forecast_batch_rows.append({
            "batch_no": q(forecast_no),
            "train_batch_no": q(train_no),
            "agent_code": q("winner-agent"),
            "region_code": q(province_codes[province]),
            "region_name": q(province),
            "customer_code": q("ALL"),
            "customer_name": q("全部客户"),
            "industry_code": q("ALL"),
            "industry_name": q("全部行业"),
            "forecast_horizon": "15",
            "forecast_start_date": "20261101",
            "forecast_end_date": q("2027-03-21"),
            "status": q("SUCCESS"),
            "request_json": q('{"source":"winner-agent"}'),
            "created_by": q("zoujun"),
            "created_at": "now()",
            "updated_at": "now()",
            "created_by_name": q("邹军"),
        })
    sql.extend(emit_insert("model_train_batch_tb", [
        "batch_no", "agent_code", "region_code", "region_name", "customer_code", "customer_name",
        "industry_code", "industry_name", "train_start_date", "train_end_date", "status", "best_model",
        "mape", "wmape", "rmse", "mae", "r2", "config_json", "result_json", "created_by",
        "created_by_name", "started_at", "completed_at", "created_at", "updated_at"
    ], batch_rows))
    sql.append("")
    sql.extend(emit_insert("model_forecast_batch_tb", [
        "batch_no", "train_batch_no", "agent_code", "region_code", "region_name", "customer_code",
        "customer_name", "industry_code", "industry_name", "forecast_horizon", "forecast_start_date",
        "forecast_end_date", "status", "request_json", "created_by", "created_at", "updated_at", "created_by_name"
    ], forecast_batch_rows))
    sql.append("")

    sql.append("""insert into model_forecast_result_tb (forecast_batch_no, forecast_date, forecast_value)
select concat('WGFC-', province), date_format(forecast_date, '%Y-%m-%d'), prediction
from gas_forecast_point;""")
    sql.append("""insert into model_predict_winter_result_tb (batch_id, province_code, forecast_date, forecast_value)
select b.id, p.province_code, p.forecast_date, p.prediction
from gas_forecast_point p
join model_forecast_batch_tb b on b.batch_no = concat('WGFC-', p.province) collate utf8mb4_0900_ai_ci;""")
    sql.append("""insert into model_train_backtest_tb (train_batch_no, train_date, actual_value, predicted_value)
select concat('WGTRAIN-', province), test_date, actual, prediction
from gas_backtest_detail;""")
    sql.append("")

    sql.extend([
        "drop table if exists gas_backtest_detail;",
        "drop table if exists gas_feature_rank;",
        "drop table if exists gas_model_rank;",
        "drop table if exists gas_customer_forecast_point;",
        "drop table if exists gas_forecast_point;",
        "drop table if exists gas_forecast_summary;",
        "drop table if exists gas_customer;",
        "drop table if exists gas_province;",
        "drop table if exists gas_area;",
        "",
    ])
    TARGET.write_text("\n".join(sql), encoding="utf-8")


if __name__ == "__main__":
    main()
