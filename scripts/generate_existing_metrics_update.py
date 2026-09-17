#!/usr/bin/env python3
import json
from pathlib import Path

SOURCE_ROOT = Path("/Users/zoujun/work/workspace/shiyou/winner-agent/output")
TARGET = Path("forecast-bussiness-dao/src/main/resources/sql/03_update_existing_metrics.sql")


def q(value):
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


def d(value):
    return "null" if value is None else str(value)


def main():
    lines = ["use gas_data;", ""]
    for plan_file in sorted(SOURCE_ROOT.glob("*/best_plan.json")):
        province = plan_file.parent.name
        plan = json.loads(plan_file.read_text(encoding="utf-8"))
        lines.append(
            "update model_train_batch_tb set "
            f"best_model = {q(plan.get('best_model'))}, "
            f"mape = {d(plan.get('backtest_MAPE'))}, "
            f"wmape = {d(plan.get('backtest_WMAPE'))}, "
            f"rmse = {d(plan.get('backtest_RMSE'))}, "
            "mae = null, "
            "r2 = null, "
            f"result_json = {q(json.dumps(plan, ensure_ascii=False))}, "
            "updated_at = now() "
            f"where batch_no = {q('WGTRAIN-' + province)};"
        )
    TARGET.write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
