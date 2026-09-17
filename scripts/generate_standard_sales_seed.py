#!/usr/bin/env python3
from __future__ import annotations

import calendar
import math
import re
import uuid
from datetime import date
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path
from xml.etree import ElementTree as ET
from zipfile import ZipFile

SOURCE_XLSX = Path("/Users/zoujun/Documents/原始燃气数据/A13灵活查询(分月)20200101-20201201.xlsx")
TARGET_SQL = Path("gas-forecast-dao/src/main/resources/sql/05_seed_standard_sales.sql")
WORKSHEET_NAME = "分省分行业"
SOURCE_FILE_ID = "A13-20200101-20201201"
UNIT = "万方"

UUID_NAMESPACE = uuid.UUID("8a456a4e-91e1-4e32-9b74-f1e26946f8f1")
NS = {"a": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
REL_NS = {"pr": "http://schemas.openxmlformats.org/package/2006/relationships"}
RID = "{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id"


def stable_code(*parts: object) -> str:
    return str(uuid.uuid5(UUID_NAMESPACE, "::".join(str(part) for part in parts)))


def sql_quote(value: object | None) -> str:
    if value is None:
        return "null"
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


def sql_decimal(value: Decimal) -> str:
    return sql_quote(value.quantize(Decimal("0.0001"), rounding=ROUND_HALF_UP))


def column_index(cell_ref: str) -> int:
    letters = "".join(ch for ch in cell_ref if ch.isalpha())
    index = 0
    for ch in letters:
        index = index * 26 + ord(ch.upper()) - 64
    return index - 1


def read_shared_strings(zip_file: ZipFile) -> list[str]:
    root = ET.fromstring(zip_file.read("xl/sharedStrings.xml"))
    values: list[str] = []
    for item in root.findall("a:si", NS):
        values.append("".join(text.text or "" for text in item.findall(".//a:t", NS)))
    return values


def cell_value(cell: ET.Element, shared_strings: list[str]) -> str:
    cell_type = cell.attrib.get("t")
    if cell_type == "inlineStr":
        return "".join(text.text or "" for text in cell.findall(".//a:t", NS))
    value = cell.find("a:v", NS)
    if value is None:
        return ""
    raw = value.text or ""
    if cell_type == "s":
        return shared_strings[int(raw)]
    return raw


def worksheet_path(zip_file: ZipFile, sheet_name: str) -> str:
    workbook = ET.fromstring(zip_file.read("xl/workbook.xml"))
    rels = ET.fromstring(zip_file.read("xl/_rels/workbook.xml.rels"))
    rid_to_target = {
        rel.attrib["Id"]: rel.attrib["Target"]
        for rel in rels.findall("pr:Relationship", REL_NS)
    }
    for sheet in workbook.findall(".//a:sheet", NS):
        if sheet.attrib.get("name") == sheet_name:
            target = rid_to_target[sheet.attrib[RID]]
            return target[1:] if target.startswith("/") else f"xl/{target}"
    raise ValueError(f"Worksheet not found: {sheet_name}")


def read_sheet_rows(zip_file: ZipFile, path: str, shared_strings: list[str]) -> list[list[str]]:
    root = ET.fromstring(zip_file.read(path))
    rows: list[list[str]] = []
    for row in root.findall(".//a:sheetData/a:row", NS):
        values = [""] * 15
        for cell in row.findall("a:c", NS):
            index = column_index(cell.attrib.get("r", "A"))
            if index < len(values):
                values[index] = cell_value(cell, shared_strings).strip()
        rows.append(values)
    return rows


def parse_month(header: str) -> date:
    match = re.search(r"(?:(20\d{2})年)?(\d{1,2})月", header)
    if not match:
        raise ValueError(f"Cannot parse month header: {header}")
    year = int(match.group(1) or "2020")
    month = int(match.group(2))
    return date(year, month, 1)


def read_monthly_records() -> list[dict[str, object]]:
    with ZipFile(SOURCE_XLSX) as zip_file:
        shared_strings = read_shared_strings(zip_file)
        rows = read_sheet_rows(zip_file, worksheet_path(zip_file, WORKSHEET_NAME), shared_strings)

    month_dates = [parse_month(header) for header in rows[2][3:15]]
    records: list[dict[str, object]] = []
    current_province = ""
    for row in rows[3:]:
        province, industry = row[0], row[1]
        if province and not industry:
            current_province = province
            continue
        if province:
            current_province = province
        if (
            not current_province
            or not industry
            or "合计" in current_province
            or "合计" in industry
            or "小计" in industry
        ):
            continue

        monthly_values = [Decimal(value or "0") for value in row[3:15]]
        if not any(value != 0 for value in monthly_values):
            continue

        region_code = stable_code("region", current_province)
        industry_code = stable_code("industry", industry)
        customer_code = stable_code("customer", current_province, industry)
        customer_name = f"{current_province}{industry}标准客户"
        for stat_date, gas_sales in zip(month_dates, monthly_values):
            records.append(
                {
                    "stat_date": stat_date,
                    "region_name": current_province,
                    "region_code": region_code,
                    "industry_name": industry,
                    "industry_code": industry_code,
                    "customer_name": customer_name,
                    "customer_code": customer_code,
                    "gas_sales": gas_sales,
                }
            )
    return records


def daily_values(monthly_value: Decimal, stat_date: date, region: str, industry: str) -> list[Decimal]:
    days = calendar.monthrange(stat_date.year, stat_date.month)[1]
    weights: list[Decimal] = []
    phase = (uuid.uuid5(UUID_NAMESPACE, f"{region}:{industry}:{stat_date:%Y-%m}").int % 628) / 100
    for day in range(1, days + 1):
        current = date(stat_date.year, stat_date.month, day)
        weekend = Decimal("0.94") if current.weekday() >= 5 else Decimal("1.02")
        wave = Decimal(str(1 + 0.08 * math.sin(day / days * math.tau + phase)))
        weights.append((weekend * wave).quantize(Decimal("0.000001")))

    total_weight = sum(weights)
    values: list[Decimal] = []
    running = Decimal("0")
    for index, weight in enumerate(weights, 1):
        if index == days:
            value = monthly_value - running
        else:
            value = (monthly_value * weight / total_weight).quantize(Decimal("0.0001"), rounding=ROUND_HALF_UP)
            running += value
        values.append(value)
    return values


def emit_insert(table: str, columns: list[str], rows: list[dict[str, str]], chunk_size: int = 500) -> list[str]:
    lines: list[str] = []
    for start in range(0, len(rows), chunk_size):
        chunk = rows[start : start + chunk_size]
        lines.append(f"insert into {table} ({', '.join(columns)}) values")
        lines.append(",\n".join("    (" + ", ".join(row[column] for column in columns) + ")" for row in chunk) + ";")
        lines.append("")
    return lines


def create_table_sql() -> list[str]:
    common_columns = """
    id bigint primary key auto_increment,
    stat_date varchar(32) not null,
    region_name varchar(64) not null,
    region_code varchar(64) not null,
    industry_name varchar(64) not null,
    industry_code varchar(64) not null,
    customer_name varchar(128) not null,
    customer_code varchar(64) not null,
    gas_sales varchar(32) not null,
    unit varchar(16) not null,
    file_id varchar(128) not null,
    created_at timestamp not null default current_timestamp,
    key idx_{table}_date (stat_date),
    key idx_{table}_region (region_code),
    key idx_{table}_industry (industry_code),
    key idx_{table}_customer (customer_code)
"""
    return [
        "create table if not exists data_monthly_sales_tb ("
        + common_columns.format(table="monthly_sales")
        + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;",
        "",
        "create table if not exists data_daily_sales_tb ("
        + common_columns.format(table="daily_sales")
        + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;",
        "",
        "alter table data_monthly_sales_tb modify id bigint not null auto_increment;",
        "alter table data_daily_sales_tb modify id bigint not null auto_increment;",
        "",
    ]


def main() -> None:
    monthly_records = read_monthly_records()
    monthly_rows: list[dict[str, str]] = []
    daily_rows: list[dict[str, str]] = []

    for record in monthly_records:
        stat_date = record["stat_date"]
        row = {
            "stat_date": sql_quote(stat_date.strftime("%Y-%m")),
            "region_name": sql_quote(record["region_name"]),
            "region_code": sql_quote(record["region_code"]),
            "industry_name": sql_quote(record["industry_name"]),
            "industry_code": sql_quote(record["industry_code"]),
            "customer_name": sql_quote(record["customer_name"]),
            "customer_code": sql_quote(record["customer_code"]),
            "gas_sales": sql_decimal(record["gas_sales"]),
            "unit": sql_quote(UNIT),
            "file_id": sql_quote(SOURCE_FILE_ID),
            "created_at": "now()",
        }
        monthly_rows.append(row)

        for day, value in enumerate(
            daily_values(record["gas_sales"], stat_date, record["region_name"], record["industry_name"]),
            1,
        ):
            daily_rows.append(
                {
                    **row,
                    "stat_date": sql_quote(date(stat_date.year, stat_date.month, day).isoformat()),
                    "gas_sales": sql_decimal(value),
                }
            )

    columns = [
        "stat_date",
        "region_name",
        "region_code",
        "industry_name",
        "industry_code",
        "customer_name",
        "customer_code",
        "gas_sales",
        "unit",
        "file_id",
        "created_at",
    ]
    lines = [
        "use gas_data;",
        "",
        "-- Generated from A13 monthly flexible query data. Re-runnable for this file_id.",
        *create_table_sql(),
        f"delete from data_daily_sales_tb where file_id = {sql_quote(SOURCE_FILE_ID)};",
        f"delete from data_monthly_sales_tb where file_id = {sql_quote(SOURCE_FILE_ID)};",
        "",
    ]
    lines.extend(emit_insert("data_monthly_sales_tb", columns, monthly_rows))
    lines.extend(emit_insert("data_daily_sales_tb", columns, daily_rows))

    TARGET_SQL.write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")
    print(f"monthly_rows={len(monthly_rows)}")
    print(f"daily_rows={len(daily_rows)}")
    print(TARGET_SQL)


if __name__ == "__main__":
    main()
