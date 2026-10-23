"""根据统一预测任务的真实结果生成受控展示结构。"""

from __future__ import annotations

import json
from decimal import Decimal
from typing import Any


class PresentationPlanner:
    """图表字段由程序固定，模型不能修改业务数值。"""

    @classmethod
    def plan(cls, tool_name: str | None, result: dict[str, Any]) -> dict[str, Any]:
        data = result.get("data") if isinstance(result.get("data"), dict) else {}
        if tool_name == "forecast_result_query":
            forecast = cls._forecast_result(data)
            if forecast:
                return {
                    "presentation": {"type": "FORECAST_CHART", "chartType": "LINE", "title": "模型回测与预测结果", "xField": "date", "yFields": ["value"], "dataSource": "forecastResult.points"},
                    "forecast_result": forecast,
                }
        if str(result.get("resultType") or "").upper() == "PAGE" or isinstance(data.get("records"), list):
            return {"presentation": {"type": "LIST", "dataSource": "executionResult.data.records"}}
        if str(result.get("resultType") or "").upper() in {"TASK", "PROGRESS"} and str(result.get("status") or "").upper() not in {"NEED_CONFIRMATION", "NEED_CLARIFICATION"}:
            return {"presentation": {"type": "PROGRESS", "dataSource": "progress"}, "progress": cls._progress(data)}
        return {"presentation": {"type": "TEXT"}}

    @staticmethod
    def _json(value: Any) -> dict[str, Any]:
        if isinstance(value, dict):
            return value
        if not isinstance(value, str):
            return {}
        try:
            parsed = json.loads(value)
            return parsed if isinstance(parsed, dict) else {}
        except json.JSONDecodeError:
            return {}

    @classmethod
    def _forecast_result(cls, data: dict[str, Any]) -> dict[str, Any] | None:
        record = data.get("record") if isinstance(data.get("record"), dict) else {}
        raw = cls._json(data.get("forecast"))
        raw_points = raw.get("points") if isinstance(raw.get("points"), list) else []
        points = []
        for item in raw_points:
            if not isinstance(item, dict):
                continue
            date = item.get("date") or item.get("forecastDate") or item.get("forecast_date")
            value = item.get("value", item.get("forecastValue", item.get("forecast_value")))
            if date is not None and value is not None:
                points.append({"date": str(date), "value": value})
        if not points:
            return None
        values = [Decimal(str(item["value"])) for item in points]
        return {
            "forecastNo": record.get("forecastNo"), "customerName": record.get("customerName"),
            "average": sum(values, Decimal("0")) / Decimal(len(values)), "minimum": min(values), "maximum": max(values),
            "training": cls._json(data.get("training")), "backtest": cls._json(data.get("backtest")), "points": points,
        }

    @staticmethod
    def _progress(data: dict[str, Any]) -> dict[str, Any]:
        status = str(data.get("status") or "PENDING").upper()
        failed, done = status == "FAILED", status == "SUCCESS"
        steps = [
            {"index": 1, "name": "准备销量数据", "status": "SUCCESS", "detail": "任务已创建"},
            {"index": 2, "name": "模型训练与回测", "status": "FAILED" if failed else "SUCCESS" if done else "RUNNING", "detail": status},
            {"index": 3, "name": "生成未来预测", "status": "SUCCESS" if done else "WAITING", "detail": "等待模型执行完成"},
        ]
        return {"title": "预测任务执行情况", "currentStep": 3 if done else 2, "totalSteps": 3, "steps": steps}
