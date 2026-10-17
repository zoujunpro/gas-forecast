"""会话入口的 LangGraph 主图。"""

from __future__ import annotations

from dataclasses import dataclass
import logging
import re
from typing import Any

from langgraph.graph import END, START, StateGraph
from langgraph.checkpoint.base import BaseCheckpointSaver
from langgraph.runtime import Runtime

from gas_forecast_agent.capabilities import CapabilityDefinition, CapabilityRegistry, CapabilityStatus
from gas_forecast_agent.conversation import ConversationResponder
from gas_forecast_agent.entities import EntityResolutionStatus, EntityResolver
from gas_forecast_agent.execution import CapabilityExecutor, ExceptionDecider, ExecutionMonitorGraph, ToolResultInterpreter
from gas_forecast_agent.intent import IntentMatchStatus, IntentRecognizer, RecognizedIntent, SpeechAct
from gas_forecast_agent.mcp import McpClientError, StreamableHttpMcpClient
from gas_forecast_agent.presentation import PresentationPlanner

from .state import MainFlowState

logger = logging.getLogger(__name__)


class MainFlowError(RuntimeError):
    """主流程无法安全继续时抛出的错误。"""


@dataclass(frozen=True)
class MainFlowContext:
    """单次调用依赖；包含用户 MCP 客户端，因此绝不能进入图状态或检查点。"""

    mcp: StreamableHttpMcpClient
    recognizer: IntentRecognizer
    capabilities: CapabilityRegistry
    entity_resolver: EntityResolver
    executor: CapabilityExecutor
    exception_decider: ExceptionDecider | None
    checkpointer: BaseCheckpointSaver | None
    conversation_responder: ConversationResponder


class AgentMainFlow:
    """负责理解和路由，不在主图内实现训练、预测等具体业务流程。"""

    def __init__(self) -> None:
        builder = StateGraph(MainFlowState, context_schema=MainFlowContext)
        builder.add_node("load_memory", self._load_memory)
        builder.add_node("recognize_intent", self._recognize_intent)
        builder.add_node("validate_reference", self._validate_reference)
        builder.add_node("match_capability", self._match_capability)
        builder.add_node("resolve_entities", self._resolve_entities)
        builder.add_node("check_confirmation", self._check_confirmation)
        builder.add_node("execute_capability", self._execute_capability)
        builder.add_node("save_memory", self._save_memory)
        builder.add_node("prepare_route", self._prepare_route)
        builder.add_edge(START, "load_memory")
        builder.add_edge("load_memory", "recognize_intent")
        builder.add_edge("recognize_intent", "validate_reference")
        builder.add_edge("validate_reference", "match_capability")
        builder.add_edge("match_capability", "resolve_entities")
        builder.add_edge("resolve_entities", "check_confirmation")
        builder.add_edge("check_confirmation", "execute_capability")
        builder.add_edge("execute_capability", "save_memory")
        builder.add_edge("save_memory", "prepare_route")
        builder.add_edge("prepare_route", END)
        self._graph = builder.compile()

    async def invoke(
        self,
        initial_state: MainFlowState,
        *,
        mcp: StreamableHttpMcpClient,
        recognizer: IntentRecognizer,
        capabilities: CapabilityRegistry,
        entity_resolver: EntityResolver,
        executor: CapabilityExecutor | None = None,
        exception_decider: ExceptionDecider | None = None,
        checkpointer: BaseCheckpointSaver | None = None,
        conversation_responder: ConversationResponder,
    ) -> MainFlowState:
        """执行一次主流程。持久业务记忆由 Java MCP 负责，不使用进程内会话缓存。"""
        return await self._graph.ainvoke(
            initial_state,
            context=MainFlowContext(mcp=mcp, recognizer=recognizer, capabilities=capabilities,
                                    entity_resolver=entity_resolver, executor=executor or CapabilityExecutor(),
                                    exception_decider=exception_decider, checkpointer=checkpointer,
                                    conversation_responder=conversation_responder),
        )

    async def _load_memory(self, state: MainFlowState, runtime: Runtime[MainFlowContext]) -> dict[str, Any]:
        result = await self._call(runtime.context.mcp, "memory_get", {"conversationNo": state["conversation_no"]})
        return {"memory": result.get("data") or {}}

    async def _recognize_intent(self, state: MainFlowState, runtime: Runtime[MainFlowContext]) -> dict[str, Any]:
        bound_context = state.get("bound_context", {})
        recognized = await runtime.context.recognizer.recognize(
            state["message"], {**state.get("memory", {}), **bound_context,
                               "recentHistory": state.get("history", [])}, state.get("requested_agent")
        )
        # 智能体页面传入的业务范围是用户已经选定的事实，优先级高于自然语言抽取。
        allowed = {"regionCode", "regionName", "industryCode", "industryName",
                   "customerCode", "customerName", "forecastHorizon", "forecastStartDate", "forecastEndDate"}
        recognized.parameters.update({key: value for key, value in bound_context.items()
                                      if key in allowed and value not in (None, "")})
        return {"intent": recognized.model_dump(mode="json")}

    async def _validate_reference(self, state: MainFlowState, runtime: Runtime[MainFlowContext]) -> dict[str, Any]:
        """查询业务表确认批次/任务真实类型，并纠正模型的查询路由。"""
        intent = RecognizedIntent.model_validate(state["intent"])
        reference = self._business_reference(state["message"], intent.parameters)
        if not reference or intent.action not in {"STATUS", "RESULT"}:
            return {}
        expected = self._expected_reference_type(intent)
        try:
            resolved = await self._call(runtime.context.mcp, "business_reference_resolve", {
                "reference": reference, "expectedType": expected,
            })
            data = resolved.get("data") if isinstance(resolved.get("data"), dict) else {}
        except Exception as exc:
            logger.warning("event=business_reference_validation_degraded reference=%s expected_type=%s reason=%s",
                           reference, expected, exc)
            data = self._reference_from_prefix(reference)
        if data.get("found") is False:
            intent.status = IntentMatchStatus.AMBIGUOUS
            intent.clarification_question = f"没有查询到编号 {reference}，请检查编号是否正确，或者告诉我查询哪个客户的最近记录。"
            intent.parameters = {}
            return {"intent": intent.model_dump(mode="json")}
        actual = str(data.get("actualType") or "UNKNOWN")
        action = self._reference_query_action(state["message"], intent.action)
        code = self._reference_capability(actual, action, str(data.get("agentCode") or "short-term"))
        if not code:
            return {}
        old_code = intent.capability_code
        capability = runtime.context.capabilities.get(code)
        intent.capability_code = code
        intent.domain, intent.action = capability.intent.domain, capability.intent.action
        intent.resource, intent.scenario = capability.intent.resource, capability.intent.scenario
        parameters = {key: value for key, value in intent.parameters.items()
                      if key not in {"taskNo", "trainBatchNo", "forecastBatchNo"}}
        parameters[{"TRAINING_BATCH": "trainBatchNo", "FORECAST_BATCH": "forecastBatchNo", "TASK": "taskNo"}[actual]] = reference
        intent.parameters = parameters
        if old_code != code or expected != actual:
            logger.warning("event=intent_corrected reason=business_reference_type model_capability=%s final_capability=%s reference=%s expected_type=%s actual_type=%s",
                           old_code or "-", code, reference, expected, actual)
        return {"intent": intent.model_dump(mode="json")}

    @staticmethod
    def _business_reference(message: str, parameters: dict[str, Any]) -> str | None:
        match = re.search(r"\b((?:TRBATCH|TRAIN|FORECAST|TASK)[-_A-Za-z0-9]+)\b", message or "", re.I)
        if match:
            return match.group(1)
        values = [parameters.get(key) for key in ("trainBatchNo", "forecastBatchNo", "taskNo") if parameters.get(key)]
        return str(values[0]) if len(values) == 1 else None

    @staticmethod
    def _expected_reference_type(intent: RecognizedIntent) -> str:
        return {"TRAINING": "TRAINING_BATCH", "FORECAST": "FORECAST_BATCH", "TASK": "TASK"}.get(intent.domain or "", "UNKNOWN")

    @staticmethod
    def _reference_query_action(message: str, model_action: str | None) -> str:
        """查询动作由明确业务词校验；其余自然表达仍采用模型判断。"""
        text = re.sub(r"\s+", "", message or "").lower()
        result_markers = ("结果", "回测", "指标", "效果", "最优模型", "最佳模型", "mape", "wmape", "smape", "rmse", "mae", "r²", "r2", "预测值", "趋势")
        status_markers = ("状态", "进度", "执行到哪", "完成了吗", "是否完成", "跑完了吗", "成功了吗")
        if any(marker in text for marker in result_markers):
            return "RESULT"
        if any(marker in text for marker in status_markers):
            return "STATUS"
        return model_action or "STATUS"

    @staticmethod
    def _reference_from_prefix(reference: str) -> dict[str, Any]:
        upper = reference.upper()
        actual = "TRAINING_BATCH" if upper.startswith(("TRBATCH-", "TRAIN-")) else \
            "FORECAST_BATCH" if upper.startswith("FORECAST-") else "TASK" if upper.startswith("TASK-") else "UNKNOWN"
        return {"found": actual != "UNKNOWN", "actualType": actual, "agentCode": "short-term"}

    @staticmethod
    def _reference_capability(actual_type: str, action: str, agent_code: str) -> str | None:
        if actual_type == "TASK":
            return "TASK_STATUS" if action == "STATUS" else None
        scenario = {"short-term": "SHORT_TERM", "monthly-sales": "MONTHLY_SALES", "winter-supply": "WINTER_SUPPLY"}.get(agent_code, "SHORT_TERM")
        domain = "TRAINING" if actual_type == "TRAINING_BATCH" else "FORECAST" if actual_type == "FORECAST_BATCH" else None
        if not domain:
            return None
        code = f"{scenario}_{domain}_{action}"
        return {"WINTER_SUPPLY_FORECAST_RESULT": "WINTER_SUPPLY_RESULT"}.get(code, code)

    async def _match_capability(self, state: MainFlowState, runtime: Runtime[MainFlowContext]) -> dict[str, Any]:
        intent = RecognizedIntent.model_validate(state["intent"])
        if intent.status != IntentMatchStatus.MATCHED:
            return {
                "capability_code": intent.capability_code,
                "capability_status": None,
                "workflow": None,
                "parameters": intent.parameters,
                "missing_parameters": [],
                "available_tools": [],
                "status": "CANCELLED" if intent.status == IntentMatchStatus.CANCELLED else "NEED_CLARIFICATION",
            }
        if intent.action == "START" and intent.speech_act not in {SpeechAct.COMMAND, SpeechAct.CONFIRM}:
            return {
                "capability_code": intent.capability_code,
                "capability_status": None,
                "workflow": None,
                "parameters": intent.parameters,
                "missing_parameters": [],
                "available_tools": [],
                "status": "NEED_CLARIFICATION",
            }
        capability = self._find_capability(runtime.context.capabilities, intent)
        parameters = dict(capability.defaults) if capability else {}
        allowed = set(capability.required_parameters + capability.optional_parameters) if capability else set(intent.parameters)
        allowed.update(parameters)
        if capability:
            if "customer_search" in capability.tools:
                allowed.update({"customerCode", "customerName"})
            if "region_search" in capability.tools:
                allowed.update({"regionCode", "regionName"})
            if "industry_search" in capability.tools:
                allowed.update({"industryCode", "industryName"})
            if any("Date" in name or "Horizon" in name or "Periods" in name for name in allowed):
                allowed.add("timeExpression")
            if capability.confirmations:
                allowed.add("confirmed")
        memory = state.get("memory", {})
        remembered = memory.get("currentParameters") if isinstance(memory.get("currentParameters"), dict) else {}
        unspecified_customer = bool(re.search(r"(一个|某个|任意)(客户|用户)", state["message"]))
        explicit_customer_name = intent.parameters.get("customerName")
        for name in allowed:
            if unspecified_customer and name in {"customerCode", "customerName"}:
                continue
            if explicit_customer_name and name == "customerCode":
                # 本轮出现新客户名称时必须重新解析，不能套用记忆中的旧客户编码。
                continue
            if self._has_value(memory.get(name)):
                parameters[name] = memory[name]
            elif self._has_value(remembered.get(name)):
                parameters[name] = remembered[name]
        current_parameters = {key: value for key, value in intent.parameters.items() if key in allowed}
        if unspecified_customer:
            current_parameters.pop("customerCode", None)
            current_parameters.pop("customerName", None)
        if capability and capability.code == "CUSTOMER_LIST":
            # 列表条件只能来自本轮原文；历史客户仅用于“这个客户”等单实体追问。
            customer_name = current_parameters.get("customerName") or parameters.get("customerName")
            if not customer_name or str(customer_name) not in state["message"]:
                parameters.pop("customerCode", None)
                parameters.pop("customerName", None)
                current_parameters.pop("customerCode", None)
                current_parameters.pop("customerName", None)
        parameters.update(current_parameters)
        if capability and capability.intent.action == "LIST" and re.search(r"(全部|所有|完整|都给我|给我全部|给我所有)", state["message"]):
            parameters["fetchAll"] = True
        parameters["conversationNo"] = state["conversation_no"]
        if capability is None:
            return {
                "capability_code": None,
                "capability_status": None,
                "workflow": None,
                "parameters": parameters,
                "missing_parameters": [],
                "available_tools": [],
                "status": "UNSUPPORTED",
            }
        missing = [name for name in capability.required_parameters if not self._has_value(parameters.get(name))]
        return {
            "capability_code": capability.code,
            "capability_status": capability.status.value,
            "workflow": capability.workflow,
            "parameters": parameters,
            "missing_parameters": missing,
            "available_tools": capability.tools,
            "status": self._status(capability, missing),
        }

    async def _resolve_entities(self, state: MainFlowState, runtime: Runtime[MainFlowContext]) -> dict[str, Any]:
        if state.get("capability_status") != CapabilityStatus.AVAILABLE.value or not state.get("capability_code"):
            return {}
        capability = runtime.context.capabilities.get(state["capability_code"])
        outcome = await runtime.context.entity_resolver.resolve(capability, state.get("parameters", {}), runtime.context.mcp)
        payload = outcome.model_dump(mode="json")
        if outcome.status == EntityResolutionStatus.RESOLVED:
            missing = [name for name in capability.required_parameters if not self._has_value(outcome.parameters.get(name))]
            return {
                "parameters": outcome.parameters,
                "entity_resolution": payload,
                "missing_parameters": missing,
                "status": "NEED_INFORMATION" if missing else "READY",
            }
        return {
            "parameters": outcome.parameters,
            "entity_resolution": payload,
            "status": outcome.status.value,
        }

    async def _check_confirmation(self, state: MainFlowState, runtime: Runtime[MainFlowContext]) -> dict[str, Any]:
        if state.get("status") != "READY" or not state.get("capability_code"):
            return {}
        capability = runtime.context.capabilities.get(state["capability_code"])
        if not capability.confirmations or state.get("parameters", {}).get("confirmed") is True:
            return {}
        for rule in capability.confirmations:
            if self._confirmation_matches(rule.condition, state.get("parameters", {})):
                return {"status": "NEED_CONFIRMATION", "confirmation_message": rule.message}
        return {}

    async def _execute_capability(self, state: MainFlowState, runtime: Runtime[MainFlowContext]) -> dict[str, Any]:
        if state.get("status") != "READY" or not state.get("capability_code"):
            return {}
        capability = runtime.context.capabilities.get(state["capability_code"])
        if capability.response_mode == "LLM":
            try:
                answer = await runtime.context.conversation_responder.respond(state["message"], capability, state.get("history", []))
            except (RuntimeError, ValueError) as exc:
                raise MainFlowError(str(exc)) from exc
            return {"execution_result": {"status": "COMPLETED", "message": answer}, "status": "COMPLETED"}
        try:
            execution = await runtime.context.executor.execute(
                capability, state.get("parameters", {}),
                lambda name, arguments: self._call_result(runtime.context.mcp, name, arguments),
            )
        except (McpClientError, ValueError) as exc:
            raise MainFlowError(str(exc)) from exc
        interpreted = ToolResultInterpreter.normalize(execution.result)
        business_status = str(interpreted.get("status") or "COMPLETED")
        task_no = self._task_no(interpreted)
        monitored = await ExecutionMonitorGraph(runtime.context.checkpointer if task_no else None).invoke(
            {"conversation_no": state["conversation_no"], "tool_result": interpreted},
            mcp=runtime.context.mcp, decider=runtime.context.exception_decider, thread_id=task_no,
        )
        display_result = dict(interpreted)
        display_result["displayMessage"] = await runtime.context.conversation_responder.respond_with_result(
            state["message"], capability, interpreted
        )
        presentation = PresentationPlanner.plan(execution.tool_name, display_result)
        proposed = interpreted.get("data", {}).get("proposedParameters", {}) if isinstance(interpreted.get("data"), dict) else {}
        parameters = {**state.get("parameters", {}), **proposed} if isinstance(proposed, dict) else state.get("parameters", {})
        return {"executed_tool": execution.tool_name, "execution_result": display_result, "status": business_status,
                "parameters": parameters,
                **presentation,
                "next_action": monitored.get("next_action"), "next_check_seconds": monitored.get("next_check_seconds"),
                "decision_reason": monitored.get("reason")}

    async def _save_memory(self, state: MainFlowState, runtime: Runtime[MainFlowContext]) -> dict[str, Any]:
        if state.get("capability_code"):
            capability = runtime.context.capabilities.get(state["capability_code"])
            if capability.context_policy == "NONE":
                return {}
        values = {
            "currentIntent": state["intent"],
            "currentCapability": state.get("capability_code"),
            "currentWorkflow": state.get("workflow"),
            "currentParameters": state.get("parameters", {}),
            "missingParameters": state.get("missing_parameters", []),
            "entityResolution": state.get("entity_resolution", {}),
            "lastExecution": self._execution_summary(state),
        }
        if state.get("status") == "NEED_CONFIRMATION":
            values["pendingAction"] = {"capabilityCode": state.get("capability_code"),
                                       "parameters": state.get("parameters", {}),
                                       "message": state.get("confirmation_message")}
        elif state.get("executed_tool") or state.get("status") == "CANCELLED":
            values["pendingAction"] = None
        values.update(
            {
                key: value
                for key, value in state.get("parameters", {}).items()
                if key != "conversationNo" and self._has_value(value)
            }
        )
        await self._call(
            runtime.context.mcp,
            "memory_update",
            {"conversationNo": state["conversation_no"], "operation": "UPSERT", "values": values},
        )
        return {}

    async def _prepare_route(self, state: MainFlowState, runtime: Runtime[MainFlowContext]) -> dict[str, Any]:
        status = state["status"]
        if state.get("execution_result"):
            message = str(state["execution_result"].get("displayMessage") or state["execution_result"].get("message") or f"已执行{state.get('capability_code')}。")
        elif status == "NEED_CONFIRMATION":
            message = state.get("confirmation_message") or "该操作会创建业务任务，是否继续？"
        elif status == "READY":
            message = f"已识别为“{state['capability_code']}”，参数校验通过。"
        elif status == "NEED_INFORMATION":
            entity_message = (state.get("entity_resolution") or {}).get("message")
            message = entity_message or f"已识别为“{state['capability_code']}”，还需要提供：{', '.join(state['missing_parameters'])}。"
        elif status == "CAPABILITY_UNAVAILABLE":
            message = f"已识别为“{state['capability_code']}”，但该能力当前状态为{state['capability_status']}，暂不执行。"
        elif status == "NEED_CLARIFICATION":
            message = (state.get("intent") or {}).get("clarification_question") or "请进一步说明您要办理的业务。"
        elif status == "CANCELLED":
            message = "已取消当前待处理操作。"
        elif status == "NEED_SELECTION":
            message = (state.get("entity_resolution") or {}).get("message") or "找到多个候选，请选择。"
        elif status == "NOT_FOUND":
            message = (state.get("entity_resolution") or {}).get("message") or "没有找到对应实体，请补充信息。"
        elif status == "INVALID_TIME":
            message = (state.get("entity_resolution") or {}).get("message") or "时间范围不合法。"
        else:
            message = "已理解用户输入，但当前能力目录中没有可执行的匹配项，需要进一步确认业务类型。"
        if not state.get("execution_result") and status in {
            "NEED_CONFIRMATION", "NEED_INFORMATION", "NEED_SELECTION", "NEED_CLARIFICATION",
            "NOT_FOUND", "INVALID_TIME", "CAPABILITY_UNAVAILABLE", "CANCELLED",
        }:
            facts = {
                "status": status,
                "userMessage": state.get("message"),
                "capabilityCode": state.get("capability_code"),
                "parameters": state.get("parameters", {}),
                "missingParameters": state.get("missing_parameters", []),
                "confirmationMessage": state.get("confirmation_message"),
                "entityResolution": state.get("entity_resolution"),
            }
            message = await runtime.context.conversation_responder.respond_for_status(facts, message)
        return {"response_message": message}

    @staticmethod
    def _confirmation_matches(condition: str, parameters: dict[str, Any]) -> bool:
        expression = condition.strip()
        if expression == "always":
            return True
        if "==" not in expression:
            return False
        name, expected = (part.strip() for part in expression.split("==", 1))
        expected_value: Any = {"true": True, "false": False, "null": None}.get(expected.lower(), expected.strip("'\""))
        return parameters.get(name) == expected_value

    @staticmethod
    def _execution_summary(state: MainFlowState) -> dict[str, Any]:
        """只保存执行定位信息，避免把预测点或历史分页结果写入会话记忆。"""
        result = state.get("execution_result") or {}
        data = result.get("data") if isinstance(result.get("data"), dict) else {}
        return {
            "tool": state.get("executed_tool"),
            "status": result.get("status"),
            "message": result.get("message"),
            "taskNo": data.get("taskNo"),
            "trainBatchNo": data.get("trainBatchNo"),
            "forecastBatchNo": data.get("forecastBatchNo"),
        } if result else {}

    @staticmethod
    def _task_no(result: dict[str, Any]) -> str | None:
        data = result.get("data") if isinstance(result.get("data"), dict) else {}
        if data.get("taskNo"):
            return str(data["taskNo"])
        tasks = data.get("tasks") if isinstance(data.get("tasks"), list) else []
        task_numbers = [str(item["taskNo"]) for item in tasks if isinstance(item, dict) and item.get("taskNo")]
        return task_numbers[0] if len(task_numbers) == 1 else None

    @staticmethod
    def _find_capability(registry: CapabilityRegistry, intent: RecognizedIntent) -> CapabilityDefinition | None:
        if intent.capability_code:
            try:
                return registry.get(intent.capability_code)
            except KeyError:
                return None
        matches = [
            item
            for item in registry.list(include_unavailable=True)
            if item.intent.domain == intent.domain
            and item.intent.action == intent.action
            and item.intent.resource == intent.resource
            and item.intent.scenario == intent.scenario
        ]
        if len(matches) > 1:
            raise MainFlowError(f"意图匹配到多个能力：{[item.code for item in matches]}")
        return matches[0] if matches else None

    @staticmethod
    def _status(capability: CapabilityDefinition, missing: list[str]) -> str:
        if capability.status != CapabilityStatus.AVAILABLE:
            return "CAPABILITY_UNAVAILABLE"
        if missing:
            return "NEED_INFORMATION"
        return "READY"

    @staticmethod
    def _has_value(value: object) -> bool:
        return value is not None and value != "" and value != []

    @staticmethod
    async def _call(client: StreamableHttpMcpClient, name: str, arguments: dict[str, Any]) -> dict[str, Any]:
        try:
            raw = await client.call_tool(name, arguments)
        except McpClientError as exc:
            raise MainFlowError(str(exc)) from exc
        structured = raw.get("structuredContent") or raw.get("structured_content")
        if not isinstance(structured, dict):
            raise MainFlowError(f"MCP工具{name}未返回structuredContent")
        if raw.get("isError") or structured.get("success") is False:
            reason = (structured.get("data") or {}).get("reason") if isinstance(structured.get("data"), dict) else None
            detail = reason or structured.get("message") or "未知业务错误"
            raise MainFlowError(f"MCP工具{name}失败：{detail}")
        return structured

    @staticmethod
    async def _call_result(client: StreamableHttpMcpClient, name: str, arguments: dict[str, Any]) -> dict[str, Any]:
        """保留业务失败信封，交由异常图决定下一步，而不是直接转换成HTTP 502。"""
        try:
            raw = await client.call_tool(name, arguments)
        except McpClientError as exc:
            return {"success": False, "status": "FAILED", "errorCode": "MCP_TRANSPORT_ERROR", "message": str(exc),
                    "retryable": True, "data": {"reason": str(exc), "nextAction": "QUERY_STATUS",
                                                       "allowedActions": ["QUERY_STATUS", "ASK_USER", "FAIL"]}}
        structured = raw.get("structuredContent") or raw.get("structured_content")
        if not isinstance(structured, dict):
            return {"success": False, "status": "FAILED", "errorCode": "INVALID_MCP_RESPONSE",
                    "message": f"MCP工具{name}未返回structuredContent", "retryable": False,
                    "data": {"nextAction": "FAIL", "allowedActions": ["FAIL"]}}
        return structured
