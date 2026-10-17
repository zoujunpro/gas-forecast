"""把所有MCP工具信封归一成稳定的Agent流程状态。"""

from __future__ import annotations

from typing import Any


class ToolResultInterpreter:
    """只根据结构化字段判断状态，绝不让大模型猜测业务结果。"""

    _FLOW_STATUSES = {"NEED_INFORMATION", "NEED_SELECTION", "NEED_CONFIRMATION", "NOT_FOUND", "CANCELLED", "FAILED"}

    @classmethod
    def normalize(cls, result: dict[str, Any]) -> dict[str, Any]:
        normalized = dict(result)
        original_status = str(result.get("status") or "UNKNOWN")
        result_type = str(result.get("resultType") or cls._infer_type(result)).upper()
        data = result.get("data") if isinstance(result.get("data"), dict) else {}
        normalized["resultType"] = result_type
        normalized["businessStatus"] = original_status
        if result.get("success") is False:
            normalized["status"] = "FAILED"
        elif original_status in cls._FLOW_STATUSES:
            normalized["status"] = original_status
        elif result_type == "PAGE" and cls._page_total(data) == 0:
            normalized["status"] = "EMPTY_RESULT"
        elif result_type == "ENTITY":
            normalized["status"] = {"MULTIPLE": "NEED_SELECTION", "NOT_FOUND": "NOT_FOUND"}.get(
                str(data.get("status") or "").upper(), original_status
            )
        else:
            normalized["status"] = original_status
        return normalized

    @staticmethod
    def _page_total(data: dict[str, Any]) -> int:
        total = data.get("total")
        if isinstance(total, (int, float)):
            return int(total)
        records = data.get("records")
        return len(records) if isinstance(records, list) else 0

    @staticmethod
    def _infer_type(result: dict[str, Any]) -> str:
        data = result.get("data") if isinstance(result.get("data"), dict) else {}
        if isinstance(data.get("records"), list): return "PAGE"
        if data.get("candidates") is not None: return "ENTITY"
        return "ERROR" if result.get("success") is False else "OBJECT"
