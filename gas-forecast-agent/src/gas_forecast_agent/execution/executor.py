"""将已确认的业务能力转换为一次明确的 MCP 工具调用。"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Awaitable, Callable

from gas_forecast_agent.capabilities import CapabilityDefinition


ToolCaller = Callable[[str, dict[str, Any]], Awaitable[dict[str, Any]]]


@dataclass(frozen=True)
class CapabilityExecution:
    """一次能力执行的可审计结果。"""

    tool_name: str
    arguments: dict[str, Any]
    result: dict[str, Any]


class CapabilityExecutor:
    """选择能力的最终业务工具，并只传递该工具允许的参数。

    实体搜索工具在前置实体解析阶段使用，不属于最终业务动作。预测能力只调用
    forecast_start；是否先训练由 Java 业务服务决定，Python 不重复查询模型表。
    """

    _TOOL_BY_CODE = {
        "REGION_LIST": "region_list",
        "INDUSTRY_LIST": "industry_list",
        "CUSTOMER_LIST": "customer_list",
        "CUSTOMER_DETAIL": "customer_search",
        "TASK_STATUS": "task_status_get",
        "TASK_STEPS": "task_steps_get",
        "TASK_RESUME": "task_resume",
        "TASK_RETRY": "task_retry",
    }

    _ARGUMENTS = {
        "region_list": {"keyword", "pageNo", "pageSize", "fetchAll"},
        "industry_list": {"keyword", "pageNo", "pageSize", "fetchAll"},
        "customer_list": {"keyword", "regionCode", "industryCode", "pageNo", "pageSize", "fetchAll"},
        "customer_search": {"conversationNo", "customerCode", "customerName", "regionCode", "regionName", "industryCode", "industryName", "limit"},
        "training_start": {"conversationNo", "agentCode", "customerCode", "customerName", "regionCode", "regionName", "industryCode", "industryName", "bulk", "confirmed", "trainMode", "trainingPeriods", "trainingStartDate", "trainingEndDate", "forceRetrain", "idempotencyKey"},
        "training_status_get": {"conversationNo", "trainBatchNo"},
        "training_result_get": {"conversationNo", "trainBatchNo"},
        "training_latest_get": {"customerCode", "agentCode"},
        "training_history_get": {"customerCode", "agentCode", "status", "pageNo", "pageSize"},
        "forecast_start": {"conversationNo", "agentCode", "customerCode", "customerName", "regionCode", "regionName", "industryCode", "industryName", "bulk", "confirmed", "trainMode", "trainingPeriods", "trainingStartDate", "trainingEndDate", "forceRetrain", "autoTrain", "trainingPreference", "idempotencyKey", "forecastStartDate", "forecastEndDate", "forecastHorizon", "forecastFrequency"},
        "forecast_status_get": {"conversationNo", "forecastBatchNo"},
        "forecast_result_get": {"conversationNo", "forecastBatchNo", "includePoints"},
        "forecast_latest_get": {"customerCode", "agentCode", "includePoints"},
        "forecast_history_get": {"customerCode", "agentCode", "status", "pageNo", "pageSize"},
        "task_status_get": {"taskNo", "includeRecovery"},
        "task_steps_get": {"taskNo", "includeBusinessSteps", "includeAttempts"},
        "task_resume": {"taskNo", "resumeFromStep", "confirmed"},
        "task_retry": {"taskNo", "retryStage", "confirmed"},
    }

    async def execute(self, capability: CapabilityDefinition, parameters: dict[str, Any], call: ToolCaller) -> CapabilityExecution:
        tool = self._final_tool(capability)
        normalized = self._normalize(tool, parameters)
        allowed = self._ARGUMENTS.get(tool)
        if allowed is None:
            raise ValueError(f"能力{capability.code}没有配置可执行工具参数：{tool}")
        arguments = {key: value for key, value in normalized.items() if key in allowed and self._has_value(value)}
        result = await call(tool, arguments)
        if (tool in {"region_list", "industry_list", "customer_list"} and parameters.get("fetchAll") is True
                and result.get("success") is False and "fetchAll" in str(result.get("message") or "")):
            return await self._execute_all_pages(tool, {key: value for key, value in arguments.items() if key != "fetchAll"}, call)
        return CapabilityExecution(tool, arguments, result)

    async def _execute_all_pages(self, tool: str, arguments: dict[str, Any], call: ToolCaller) -> CapabilityExecution:
        """用户明确要求全部时自动翻页；单页最多100条，总量设置安全上限。"""
        page_no, page_size, maximum = 1, 100, 10000
        records: list[Any] = []
        total = 0
        first_result: dict[str, Any] | None = None
        while len(records) < maximum:
            page_arguments = {**arguments, "pageNo": page_no, "pageSize": page_size}
            result = await call(tool, page_arguments)
            if first_result is None:
                first_result = dict(result)
            if result.get("success") is False:
                return CapabilityExecution(tool, page_arguments, result)
            data = result.get("data") if isinstance(result.get("data"), dict) else {}
            page_records = data.get("records") if isinstance(data.get("records"), list) else []
            total = int(data.get("total") or len(page_records))
            records.extend(page_records)
            if not page_records or len(records) >= total:
                break
            page_no += 1
        envelope = first_result or {"success": True, "status": "COMPLETED", "message": "列表查询成功"}
        data = dict(envelope.get("data") or {})
        data.update({"records": records, "total": total, "page": 1, "size": len(records),
                     "allFetched": len(records) >= total, "fetchedCount": len(records)})
        envelope.update({"resultType": "PAGE", "data": data})
        return CapabilityExecution(tool, {**arguments, "fetchAll": True}, envelope)

    def _final_tool(self, capability: CapabilityDefinition) -> str:
        explicit = self._TOOL_BY_CODE.get(capability.code)
        if explicit:
            return explicit
        suffixes = {
            "TRAINING_START": "training_start", "TRAINING_STATUS": "training_status_get",
            "TRAINING_RESULT": "training_result_get", "TRAINING_LATEST": "training_latest_get",
            "TRAINING_HISTORY": "training_history_get", "FORECAST_START": "forecast_start",
            "FORECAST_STATUS": "forecast_status_get", "FORECAST_RESULT": "forecast_result_get",
            "FORECAST_LATEST": "forecast_latest_get", "FORECAST_HISTORY": "forecast_history_get",
        }
        for suffix, tool in suffixes.items():
            if capability.code.endswith(suffix) and tool in capability.tools:
                return tool
        # 月度历史、冬季结果等能力编码不是通用后缀，使用配置中最后一个非实体查询工具。
        candidates = [name for name in capability.tools if name not in {"customer_search", "region_search", "industry_search"}]
        if not candidates:
            raise ValueError(f"能力{capability.code}没有最终业务工具")
        return candidates[0]

    @staticmethod
    def _normalize(tool: str, parameters: dict[str, Any]) -> dict[str, Any]:
        values = dict(parameters)
        if tool in {"region_list", "industry_list"}:
            values["keyword"] = values.get("regionName") or values.get("industryName") or values.get("keyword")
        elif tool == "customer_list":
            values["keyword"] = values.get("customerName") or values.get("keyword")
        preference = values.get("trainingPreference")
        if tool == "forecast_start":
            if preference == "FORCE_RETRAIN":
                values.update(forceRetrain=True, autoTrain=True)
            elif preference == "USE_EXISTING_ONLY":
                values.update(forceRetrain=False, autoTrain=False)
            elif preference == "AUTO":
                values.update(forceRetrain=False, autoTrain=True)
            else:
                values.update(forceRetrain=False, autoTrain=False, trainingPreference="UNSPECIFIED")
        if values.get("trainingStartDate") and values.get("trainingEndDate"):
            values["trainMode"] = "RANGE"
        return values

    @staticmethod
    def _has_value(value: object) -> bool:
        return value is not None and value != "" and value != []
