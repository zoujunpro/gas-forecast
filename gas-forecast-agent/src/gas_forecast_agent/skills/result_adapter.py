"""将工具原始结果拆成模型上下文和确定性兜底文案。"""

from __future__ import annotations

from decimal import Decimal
from typing import Any


class ToolResultAdapter:
    """完整结果留给前端，只向模型暴露完成下一步决策所需的信息。"""

    SAMPLE_SIZE = 5
    MAX_LIST_ITEMS = 8
    MAX_TEXT_LENGTH = 500

    @classmethod
    def for_model(cls, result: dict[str, Any]) -> dict[str, Any]:
        envelope = {
            key: result.get(key)
            for key in (
                "success", "status", "resultType", "errorCode", "errorType",
                "message", "retryable",
            )
            if result.get(key) is not None
        }
        data = result.get("data")
        envelope["data"] = cls._data_for_model(
            str(result.get("resultType") or "").upper(), data if isinstance(data, dict) else {}
        )
        return envelope

    @classmethod
    def trace_for_model(cls, trace: list[dict[str, Any]]) -> list[dict[str, Any]]:
        return [
            {
                "tool": step.get("tool"),
                "arguments": cls._compact(step.get("arguments")),
                "result": cls.for_model(step.get("result") or {}),
                "attempts": step.get("attempts"),
            }
            for step in trace
        ]

    @classmethod
    def fallback_message(cls, result: dict[str, Any]) -> str:
        data = result.get("data") if isinstance(result.get("data"), dict) else {}
        result_type = str(result.get("resultType") or "").upper()
        if result_type == "PAGE":
            total = data.get("total")
            if total is None:
                records = data.get("records")
                total = len(records) if isinstance(records, list) else 0
            return f"查询完成，共找到{total}条记录，完整结果已展示。"
        return str(result.get("message") or "工具执行成功，结果已展示。")

    @classmethod
    def presentation_message(cls, result: dict[str, Any], model_message: str) -> str:
        """模型可以润色短说明，但不能用长列表或序列化对象替代前端组件。"""
        text = model_message.strip()
        if str(result.get("resultType") or "").upper() != "PAGE":
            return text
        suspicious = (
            len(text) > 300
            or text.startswith(("{", "["))
            or text.count("\n") > 4
            or text.count("•") > 3
        )
        return cls.fallback_message(result) if suspicious else text

    @classmethod
    def _data_for_model(cls, result_type: str, data: dict[str, Any]) -> dict[str, Any]:
        if result_type == "PAGE" or isinstance(data.get("records"), list):
            records = data.get("records") if isinstance(data.get("records"), list) else []
            summary = {
                key: data.get(key)
                for key in ("total", "page", "size", "fetchedCount", "allFetched")
                if data.get(key) is not None
            }
            summary.update({
                "recordCount": len(records),
                "sample": cls._compact(records[: cls.SAMPLE_SIZE]),
                "truncated": len(records) > cls.SAMPLE_SIZE,
            })
            return summary

        # 预测点只给模型统计值和首尾样例，完整点位继续由前端绘图。
        points = data.get("points")
        if isinstance(points, list):
            values = [
                cls._number(point.get("forecastValue", point.get("value")))
                for point in points if isinstance(point, dict)
            ]
            values = [value for value in values if value is not None]
            summary = {key: cls._compact(value) for key, value in data.items() if key != "points"}
            summary["pointSummary"] = {
                "count": len(points),
                "minimum": min(values) if values else None,
                "maximum": max(values) if values else None,
                "average": sum(values, Decimal("0")) / len(values) if values else None,
                "first": cls._compact(points[:3]),
                "last": cls._compact(points[-3:]),
                "truncated": len(points) > 6,
            }
            return summary
        return cls._compact(data)

    @classmethod
    def _compact(cls, value: Any, depth: int = 0) -> Any:
        if depth >= 5:
            return "[内容已压缩]"
        if isinstance(value, dict):
            return {str(key): cls._compact(item, depth + 1) for key, item in value.items()}
        if isinstance(value, list):
            compacted = [cls._compact(item, depth + 1) for item in value[: cls.MAX_LIST_ITEMS]]
            if len(value) > cls.MAX_LIST_ITEMS:
                compacted.append({"truncatedItems": len(value) - cls.MAX_LIST_ITEMS})
            return compacted
        if isinstance(value, str) and len(value) > cls.MAX_TEXT_LENGTH:
            return value[: cls.MAX_TEXT_LENGTH] + "…"
        return value

    @staticmethod
    def _number(value: Any) -> Decimal | None:
        try:
            return Decimal(str(value)) if value is not None else None
        except (ValueError, TypeError):
            return None
