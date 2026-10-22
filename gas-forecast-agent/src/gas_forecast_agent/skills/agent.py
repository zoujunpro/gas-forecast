"""在 Skill 能力边界内执行统一的参数校验、工具调用和结果处理。"""

from __future__ import annotations

from dataclasses import dataclass, field
import ast
import asyncio
import hashlib
import json
import re
import time
from typing import Any

from jsonschema import Draft202012Validator

from gas_forecast_agent.config import Settings
from gas_forecast_agent.llm import build_llm_client
from gas_forecast_agent.mcp import McpClientError, StreamableHttpMcpClient
from gas_forecast_agent.schemas import (
    ClarificationInputType,
    ClarificationOption,
    ClarificationReason,
    ResolutionIssue,
)

from .models import SkillDefinition, SkillIntent
from .result_adapter import ToolResultAdapter
from .semantics import ContextRelation, DialogueAct, RequestSemantics, TargetSelection


WRITE_TOOLS = {"forecast_start", "training_start"}
ASYNC_TOOLS = WRITE_TOOLS
BUSINESS_CONTEXT_KEYS = (
    "regionCode", "regionName", "industryCode", "industryName",
    "customerCode", "customerName",
)
ENTITY_ARGUMENT_KEYS = (
    "customerCode", "customerName", "customerCodes", "customerNames",
    "regionCode", "regionName", "regionCodes", "regionNames",
    "industryCode", "industryName", "industryCodes", "industryNames",
)
DIRECT_RESULT_TOOLS = {
    "training_result_query",
    "forecast_result_query",
    "task_status_get", "task_steps_get",
}
STRUCTURED_LIST_TOOLS = {"customer_list", "region_list", "industry_list"}
@dataclass
class SkillRunResult:
    status: str
    message: str
    parameters: dict[str, Any]
    available_tools: list[str]
    executed_tool: str | None = None
    execution_result: dict[str, Any] | None = None
    pending_tool: str | None = None
    tool_trace: list[dict[str, Any]] = field(default_factory=list)
    resolution_issues: list[ResolutionIssue] = field(default_factory=list)


class ActionExecutor:
    """所有 Skill 共用的动作执行器；Skill 本身只声明能力和工具白名单。"""

    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    async def run(
        self,
        *,
        conversation_no: str,
        skill: SkillDefinition,
        message: str,
        history: list[dict[str, str]],
        memory: dict[str, Any],
        metadata: dict[str, Any],
        mcp: StreamableHttpMcpClient,
        semantics: RequestSemantics | None = None,
    ) -> SkillRunResult:
        # 知识问答 Skill 没有业务工具，不应因为 Java MCP 暂时不可用而无法回答。
        tool_catalog = await mcp.list_tools() if skill.allowed_tools else []
        tools = {item["name"]: item for item in tool_catalog if item.get("name") in skill.allowed_tools}
        parameters = self._initial_parameters(message, memory, metadata, conversation_no, skill.name, semantics)
        trace: list[dict[str, Any]] = []
        if semantics and semantics.dialogue_act == DialogueAct.REJECT_PENDING and memory.get("pendingAction"):
            return SkillRunResult(
                status="COMPLETED",
                message="已取消本次待确认操作。",
                parameters=parameters,
                available_tools=list(tools),
                tool_trace=trace,
            )
        if parameters.get("_clarificationQuestion"):
            return SkillRunResult(
                status="NEED_CLARIFICATION",
                message=str(parameters["_clarificationQuestion"]),
                parameters=parameters,
                available_tools=list(tools),
                pending_tool=parameters.get("_clarificationPendingTool"),
                tool_trace=trace,
                resolution_issues=[self._scope_issue(str(parameters["_clarificationQuestion"]))],
            )
        started_at = time.monotonic()
        tool_counts: dict[str, int] = {}
        write_calls = 0

        for _ in range(self._settings.agent_max_steps):
            elapsed = time.monotonic() - started_at
            if elapsed >= self._settings.agent_max_duration_seconds:
                return self._budget_exceeded("处理时间达到安全上限", parameters, tools, trace)
            try:
                # SDK自身可能自动重试，单纯配置HTTP超时仍可能把一次模型调用拖到数倍。
                # wait_for确保所有规划步骤合计不会突破Agent总预算。
                remaining = self._settings.agent_max_duration_seconds - elapsed
                decision = await asyncio.wait_for(
                    self._decide(skill, message, history, parameters, trace, list(tools.values())),
                    timeout=remaining,
                )
            except Exception:
                # 查询工具已经返回完整页面数据时，后续模型只负责组织说明文字。
                # 总结失败不能吞掉真实业务结果，更不能让前端一直等待。
                last_result = trace[-1]["result"] if trace else {}
                if self._is_successful_page(last_result):
                    return self._page_fallback_result(parameters, tools, trace)
                raise
            if decision["type"] == "message":
                decision_status = decision.get("status", "COMPLETED")
                if skill.intent == SkillIntent.CHAT:
                    # 普通聊天永远是直接回复，不能被规划模型变成缺参表单。
                    decision_status = "COMPLETED"
                if (
                    not trace
                    and skill.intent in {SkillIntent.TRAIN, SkillIntent.FORECAST}
                    and decision_status == "COMPLETED"
                ):
                    return SkillRunResult(
                        status="FAILED",
                        message="本次请求没有执行任何业务工具，未创建训练或预测任务。请补充必要信息后重试。",
                        parameters=parameters,
                        available_tools=list(tools),
                        tool_trace=trace,
                    )
                last = trace[-1] if trace else None
                if last and self._is_successful_page(last["result"]) and decision_status != "COMPLETED":
                    return self._page_fallback_result(parameters, tools, trace)
                decision_message = decision["message"]
                if last and self._is_successful_page(last["result"]):
                    decision_message = ToolResultAdapter.presentation_message(last["result"], decision_message)
                return SkillRunResult(
                    status=decision_status,
                    message=decision_message,
                    parameters=parameters,
                    available_tools=list(tools),
                    executed_tool=last["tool"] if last else None,
                    execution_result=last["result"] if last else None,
                    tool_trace=trace,
                    resolution_issues=self._message_issues(
                        decision_status, decision_message
                    ),
                )

            name = decision["name"]
            current_arguments = decision.get("arguments", {})
            arguments = {**parameters, **current_arguments}
            self._apply_locked_parameters(parameters, arguments)
            self._apply_current_entity_arguments(current_arguments, arguments)
            self._apply_trusted_entity_arguments(parameters, arguments)
            # confirmed 只能来自用户明确回复，模型生成的同名参数不能绕过确认。
            if parameters.get("confirmed") is True:
                arguments["confirmed"] = True
            else:
                arguments.pop("confirmed", None)
            if name not in tools:
                return SkillRunResult(
                    status="FAILED",
                    message=f"当前 Skill 不允许调用工具 {name}。",
                    parameters=parameters,
                    available_tools=list(tools),
                    tool_trace=trace,
                )
            arguments, adaptation_error = self._adapt_tool_parameters(tools[name], arguments)
            if adaptation_error:
                return SkillRunResult(
                    status="NEED_CLARIFICATION",
                    message=adaptation_error,
                    parameters=parameters,
                    available_tools=list(tools),
                    tool_trace=trace,
                    resolution_issues=[ResolutionIssue(
                        reason=ClarificationReason.PARAMETER_CONFLICT,
                        field="parameters",
                        message=adaptation_error,
                    )],
                )
            arguments = self._validated_arguments(tools[name], arguments)
            argument_error = self._argument_validation_error(tools[name], arguments)
            if argument_error:
                return SkillRunResult(
                    status="NEED_CLARIFICATION",
                    message=argument_error,
                    parameters=parameters,
                    available_tools=list(tools),
                    tool_trace=trace,
                    resolution_issues=[ResolutionIssue(
                        reason=ClarificationReason.PARAMETER_CONFLICT,
                        field="parameters",
                        message=argument_error,
                    )],
                )
            missing = self._missing_required_arguments(tools[name], arguments)
            # confirmed 是交互式确认字段，不应被当成普通业务参数向用户索取。
            business_missing = [name for name in missing if name != "confirmed"]
            # 预测周期虽然是 MCP 的可选默认值，但用户从页面或对话发起一次新
            # 预测时必须明确选择，避免系统悄悄套用 30 天。结束日期已明确给出
            # 时可由服务端换算周期，无需重复追问。
            if (
                name == "forecast_start"
                and arguments.get("forecastHorizon") in (None, "")
                and arguments.get("forecastEndDate") in (None, "")
                and "forecastHorizon" not in business_missing
            ):
                business_missing.append("forecastHorizon")
            if business_missing:
                return SkillRunResult(
                    status="NEED_INFORMATION",
                    message=self._missing_parameters_message(tools[name], business_missing, arguments),
                    parameters=parameters,
                    available_tools=list(tools),
                    tool_trace=trace,
                    resolution_issues=self._missing_issues(tools[name], business_missing, arguments),
                )
            confirmation = self._confirmation_message(tools[name], arguments)
            if confirmation:
                return SkillRunResult(
                    status="NEED_CONFIRMATION",
                    message=confirmation,
                    parameters=arguments,
                    available_tools=list(tools),
                    pending_tool=name,
                    tool_trace=trace,
                    resolution_issues=[self._confirmation_issue(confirmation)],
                )
            if missing:
                return SkillRunResult(
                    status="NEED_INFORMATION",
                    message=self._missing_parameters_message(tools[name], missing, arguments),
                    parameters=parameters,
                    available_tools=list(tools),
                    tool_trace=trace,
                    resolution_issues=self._missing_issues(tools[name], missing, arguments),
                )
            if any(step["tool"] == name and step["arguments"] == arguments for step in trace):
                return SkillRunResult(
                    status="FAILED",
                    message=f"检测到重复调用工具 {name}，已停止执行。",
                    parameters=parameters,
                    available_tools=list(tools),
                    tool_trace=trace,
                )
            tool_counts[name] = tool_counts.get(name, 0) + 1
            if tool_counts[name] > self._settings.agent_max_same_tool_calls:
                return self._budget_exceeded(f"工具 {name} 的调用次数达到安全上限", parameters, tools, trace)
            if name in WRITE_TOOLS:
                write_calls += 1
                if write_calls > self._settings.agent_max_write_calls:
                    return self._budget_exceeded("写操作调用次数达到安全上限", parameters, tools, trace)
            result, attempts = await self._call_with_retry(mcp, tools[name], name, arguments)
            result = self._normalize_empty_query_result(name, result, arguments)
            trace.append({"tool": name, "arguments": arguments, "result": result, "attempts": attempts})
            self._merge_result(parameters, result)
            outcome = self._result_status(result)
            if outcome in {
                "NEED_INFORMATION", "NEED_CONFIRMATION", "NEED_CLARIFICATION", "NEED_SELECTION",
                "EMPTY_RESULT", "NOT_FOUND", "CANCELLED", "UNKNOWN", "SYSTEM_ERROR",
            }:
                issues = self._tool_result_issues(name, outcome, arguments, result)
                returned_status = outcome
                return SkillRunResult(
                    status=returned_status,
                    message=str(result.get("message") or "工具执行失败。"),
                    parameters=parameters,
                    available_tools=list(tools),
                    executed_tool=name,
                    execution_result=result,
                    pending_tool=name if outcome == "NEED_CONFIRMATION" else None,
                    tool_trace=trace,
                    resolution_issues=issues,
                )
            if outcome == "FAILED" and not self._can_model_decide(result):
                return SkillRunResult(
                    status=outcome,
                    message=str(result.get("message") or "工具执行失败。"),
                    parameters=parameters,
                    available_tools=list(tools),
                    executed_tool=name,
                    execution_result=result,
                    tool_trace=trace,
                )
            if name in ASYNC_TOOLS:
                return SkillRunResult(
                    status=str(result.get("status") or "EXECUTING"),
                    message=str(result.get("message") or "任务已提交。"),
                    parameters=parameters,
                    available_tools=list(tools),
                    executed_tool=name,
                    execution_result=result,
                    tool_trace=trace,
                )
            # 列表工具已返回结构化 records 时，用户的查询目标已经完成。
            # 直接交给 PresentationPlanner 渲染，不能再让模型重复选择同一工具。
            result_data = result.get("data") if isinstance(result.get("data"), dict) else {}
            if name in STRUCTURED_LIST_TOOLS and isinstance(result_data.get("records"), list):
                return SkillRunResult(
                    status=str(result.get("status") or "COMPLETED"),
                    message=ToolResultAdapter.fallback_message(result),
                    parameters=parameters,
                    available_tools=list(tools),
                    executed_tool=name,
                    execution_result=result,
                    tool_trace=trace,
                )
            # 这些查询工具已经返回可直接展示的结构化业务数据，PresentationPlanner
            # 会负责卡片和图表。再次请求模型只会增加延迟，并可能把真实数值解释错。
            if name in DIRECT_RESULT_TOOLS and skill.intent != SkillIntent.ANALYZE:
                return SkillRunResult(
                    status=str(result.get("status") or "COMPLETED"),
                    message=str(result.get("message") or "查询完成。"),
                    parameters=parameters,
                    available_tools=list(tools),
                    executed_tool=name,
                    execution_result=result,
                    tool_trace=trace,
                )
            if self._settings.skill_routing_mode == "rules" or not self._settings.llm_api_key:
                return SkillRunResult(
                    status=str(result.get("status") or "COMPLETED"),
                    message=str(result.get("message") or "查询完成。"),
                    parameters=parameters,
                    available_tools=list(tools),
                    executed_tool=name,
                    execution_result=result,
                    tool_trace=trace,
                )

        return SkillRunResult(
            status="FAILED",
            message="规划轮次达到安全上限，请缩小问题范围或补充更明确的条件后重试。",
            parameters=parameters,
            available_tools=list(tools),
            tool_trace=trace,
        )

    @staticmethod
    def _budget_exceeded(
        reason: str,
        parameters: dict[str, Any],
        tools: dict[str, dict[str, Any]],
        trace: list[dict[str, Any]],
    ) -> SkillRunResult:
        last = trace[-1] if trace else None
        return SkillRunResult(
            status="FAILED",
            message=f"{reason}，已停止执行，避免循环调用。请缩小问题范围或补充信息后重试。",
            parameters=parameters,
            available_tools=list(tools),
            executed_tool=last["tool"] if last else None,
            execution_result=last["result"] if last else None,
            tool_trace=trace,
        )

    @staticmethod
    def _is_successful_page(result: dict[str, Any]) -> bool:
        return (
            str(result.get("status") or "").upper() == "COMPLETED"
            and str(result.get("resultType") or "").upper() == "PAGE"
        )

    @staticmethod
    def _page_fallback_result(
        parameters: dict[str, Any],
        tools: dict[str, dict[str, Any]],
        trace: list[dict[str, Any]],
    ) -> SkillRunResult:
        last = trace[-1]
        return SkillRunResult(
            status="COMPLETED",
            message=ToolResultAdapter.fallback_message(last["result"]),
            parameters=parameters,
            available_tools=list(tools),
            executed_tool=last["tool"],
            execution_result=last["result"],
            tool_trace=trace,
        )

    async def _decide(
        self,
        skill: SkillDefinition,
        message: str,
        history: list[dict[str, str]],
        parameters: dict[str, Any],
        trace: list[dict[str, Any]],
        tools: list[dict[str, Any]],
    ) -> dict[str, Any]:
        # 明确的只读查询不交给模型在“状态/结果/发起任务”之间自由猜测。
        # 特别是“刚才预测所用的训练信息”必须查询训练结果，绝不能创建预测任务。
        deterministic_query = self._explicit_read_query(skill.intent, message, parameters)
        if deterministic_query:
            return deterministic_query
        # 用户已经确认的待执行动作必须按服务端保存的快照继续，不能再次交给
        # 规划模型解释，否则“确认/继续”可能被误判成缺少业务参数。
        if parameters.get("_pendingTool") and parameters.get("confirmed") is True:
            return {"type": "tool", "name": parameters["_pendingTool"], "arguments": parameters}
        # 训练和预测各只有一个固定写工具；不再由模型选择工具。Schema 校验会
        # 在真正调用前把缺失的实体、时间范围等转成结构化澄清。
        if skill.intent == SkillIntent.TRAIN:
            return {"type": "tool", "name": "training_start", "arguments": parameters}
        if skill.intent == SkillIntent.FORECAST:
            return {"type": "tool", "name": "forecast_start", "arguments": parameters}
        # 分析只围绕指定业务范围的最新预测，不使用预测批次号作为用户输入条件。
        if skill.intent == SkillIntent.ANALYZE and not trace:
            if any(parameters.get(key) for key in ("customerCode", "customerName", "regionCode", "regionName", "industryCode", "industryName")):
                return {"type": "tool", "name": "forecast_result_query", "arguments": parameters}
            return {
                "type": "message",
                "status": "NEED_INFORMATION",
                "message": "请说明要分析的预测范围：客户、地区或行业。",
            }
        # 实体真实性校验已经在 understand_request 完成。Skill 的规划器只在
        # 白名单内选择真正完成业务目标的工具，不能再把实体解析工具插入计划。
        # 地区、行业实体仅是筛选条件，不能据此推断或默认客户查询工具。
        if self._settings.skill_routing_mode == "rules" or not self._settings.llm_api_key or not self._settings.llm_model:
            return self._rule_decision(skill.intent, message, parameters)
        openai_tools = [
            {
                "type": "function",
                "function": {
                    "name": tool["name"],
                    "description": tool.get("description") or tool["name"],
                    "parameters": tool.get("inputSchema") or {"type": "object", "properties": {}},
                },
            }
            for tool in tools
        ]
        action_skill_first_step = (
            not trace
            and skill.intent in {SkillIntent.TRAIN, SkillIntent.FORECAST}
        )
        client = build_llm_client(self._settings)
        request_messages = [
            {
                "role": "system",
                "content": (
                    skill.model_instructions()
                    + "\n\n围绕用户最终目标逐步规划，本轮只决定一个下一步：调用一个工具，或直接回复用户。"
                      "不要一次生成完整执行清单；下一步必须依据已返回的真实工具结果决定。"
                      "工具名称和参数必须来自给定定义。信息不足时直接提问；"
                      "工具选择以用户原话表达的业务目标为准；实体和参数只用于过滤结果，"
                      "不能因为实体为空而改选其他目录工具。若无法从用户原话和工具描述安全确定工具，"
                      "必须澄清，禁止默认customer_list。"
                      "业务实体已经在前置理解阶段完成校验，只能使用 knownParameters 中的可信实体，"
                      "不得自行补造、改写或解析实体编码。"
                      "本节点只负责将用户明确表达的业务目标匹配到工具声明的业务用途，"
                      "不得补造用户没有表达的目标、范围、对象、层级、前提或替代需求。"
                      "当且仅当用户目标无法唯一匹配任何工具用途，或多个工具用途同样匹配时，才发起澄清。"
                      "一旦命中唯一工具，必须调用该工具；即使参数为空也不得再推断业务歧义。"
                      "工具调用所需参数是否缺失、格式是否正确，只能由后续JSON Schema校验决定；"
                      "Schema未要求的参数不能作为澄清理由。"
                      "目标完成时直接回答。直接回复时必须只输出 JSON："
                      '{"status":"COMPLETED|NEED_INFORMATION|NEED_CLARIFICATION|NOT_FOUND|FAILED","message":"给用户的话"}。'
                      "不得重复写操作，不得持续轮询异步任务。"
                ),
            },
            *history[-8:],
            {
                "role": "user",
                "content": json.dumps(
                    {
                        "message": message,
                        "businessIntent": {
                            "intent": skill.intent.value,
                            "userGoal": message,
                        },
                        "knownParameters": parameters,
                        "toolTrace": ToolResultAdapter.trace_for_model(trace),
                    },
                    ensure_ascii=False,
                    default=str,
                ),
            },
        ]
        request_options = {
            "model": self._settings.llm_model,
            "temperature": 0,
            "tools": openai_tools or None,
            # Agent 按“选择一个工具 -> 执行 -> 根据真实结果再决策”的方式运行。
            # 显式关闭模型的并行工具调用，避免一次返回多个调用。
            "parallel_tool_calls": False if openai_tools else None,
            # 动作型 Skill 的第一步必须落到真实工具。模型仍负责选择具体工具，
            # 但不能用一段文本冒充已经发起训练或预测。
            "tool_choice": "required" if openai_tools and action_skill_first_step else "auto" if openai_tools else None,
        }
        response = await client.chat.completions.create(
            **request_options,
            messages=request_messages,
        )
        answer = response.choices[0].message
        calls = answer.tool_calls or []
        if len(calls) > 1:
            # 某些兼容模型可能忽略 parallel_tool_calls=False。不能假设第一个调用
            # 就是正确起点：不执行任何候选，追加纠错要求后重新规划一次。
            response = await client.chat.completions.create(
                **request_options,
                messages=[
                    *request_messages,
                    {
                        "role": "user",
                        "content": "上一次决策同时选择了多个工具，不能执行。请重新判断依赖关系，只选择当前最先需要执行的一个工具。",
                    },
                ],
            )
            answer = response.choices[0].message
            calls = answer.tool_calls or []
            if len(calls) > 1:
                return {
                    "type": "message",
                    "status": "FAILED",
                    "message": "模型未能确定唯一的下一步工具，本轮未执行任何业务操作。请换一种更明确的说法后重试。",
                }
        if not calls:
            return self._message_decision(answer.content)
        call = calls[0]
        return {
            "type": "tool",
            "name": call.function.name,
            "arguments": json.loads(call.function.arguments or "{}"),
        }

    @staticmethod
    def _explicit_read_query(
        intent: SkillIntent,
        message: str,
        parameters: dict[str, Any],
    ) -> dict[str, Any] | None:
        text = re.sub(r"\s+", "", message).lower()
        if intent == SkillIntent.QUERY:
            scope_keys = ("customerCode", "regionCode", "industryCode")
            result_terms = ("训练信息", "训练结果", "训练指标", "训练效果", "训练详情", "所用的训练", "使用的训练")
            if any(term in text for term in result_terms) and any(parameters.get(key) for key in scope_keys):
                return {"type": "tool", "name": "training_result_query", "arguments": parameters}
            forecast_terms = ("预测进度", "预测结果", "预测值", "预测信息", "预测情况", "预测完成")
            if any(term in text for term in forecast_terms) and any(parameters.get(key) for key in scope_keys):
                return {"type": "tool", "name": "forecast_result_query", "arguments": parameters}
        return None

    @staticmethod
    def _rule_decision(
        intent: SkillIntent,
        message: str,
        parameters: dict[str, Any],
    ) -> dict[str, Any]:
        """仅供无模型的局部测试使用；每个分支仍严格遵守 Skill 能力边界。"""
        text = re.sub(r"\s+", "", message).lower()
        if parameters.get("_pendingTool") and parameters.get("confirmed") is True:
            return {"type": "tool", "name": parameters["_pendingTool"], "arguments": parameters}
        if intent == SkillIntent.FORECAST:
            name = "forecast_start"
        elif intent == SkillIntent.TRAIN:
            name = "training_start"
        elif intent == SkillIntent.ANALYZE:
            name = "forecast_result_query"
        elif intent == SkillIntent.CHAT:
            return {
                "type": "message",
                "status": "COMPLETED",
                "message": "你好，我可以陪你聊聊，也可以帮你查询数据、训练模型或进行用气预测。",
            }
        else:
            if any(word in text for word in ("预测进度", "预测结果", "预测值", "预测信息", "预测完成")):
                return {"type": "tool", "name": "forecast_result_query", "arguments": parameters}
            if not any(word in text for word in ("查询", "查看", "哪些", "列表", "状态", "步骤", "客户", "行业", "地区", "省份", "省", "市", "区域", "行政区")):
                return {"type": "message", "message": "你好，我可以帮你查询业务数据、训练模型或进行用气预测。"}
            if "客户" in text:
                name = "customer_list"
            elif "任务" in text and "步骤" in text:
                name = "task_steps_get"
            elif "任务" in text and any(word in text for word in ("状态", "进度")):
                name = "task_status_get"
            elif any(word in text for word in ("地区", "省份", "省", "市", "区域", "行政区")):
                name = "region_list"
            elif "行业" in text and "客户" not in text:
                name = "industry_list"
            else:
                return {
                    "type": "message",
                    "status": "NEED_CLARIFICATION",
                    "message": "请说明要查询的对象：地区、省份、行业、客户，还是训练/预测任务信息？",
                }
        return {"type": "tool", "name": name, "arguments": parameters}

    @staticmethod
    def _initial_parameters(
        message: str,
        memory: dict[str, Any],
        metadata: dict[str, Any],
        conversation_no: str,
        skill_name: str,
        semantics: RequestSemantics | None = None,
    ) -> dict[str, Any]:
        if skill_name == "general-chat":
            # 非业务对话只能使用当前原话和聊天历史，不能继承客户、预测周期、
            # pendingAction 等业务状态，否则一次问候也可能被旧预测参数污染。
            values: dict[str, Any] = {"conversationNo": conversation_no}
            if semantics and semantics.intent is not None:
                values["_intent"] = semantics.intent.value
            return values
        normalized_message = re.sub(r"\s+", "", message).lower()
        explicit_confirmation = bool(
            semantics and semantics.dialogue_act == DialogueAct.CONFIRM_PENDING
        )
        clarification_reply = str(metadata.get("inputType") or "").upper() == "CLARIFICATION_REPLY"
        clarification_values = metadata.get("clarificationResolvedParameters")
        if not isinstance(clarification_values, dict):
            clarification_values = {}
        resumable_controls = {"_entityBatchResolved", "_validatedEntityParameters"}
        relation = (
            semantics.primary_request.context_relation
            if semantics and semantics.primary_request
            else ContextRelation.NEW
        )
        inherit_context = (
            relation in {ContextRelation.CONTINUE, ContextRelation.MODIFY}
            and not clarification_reply
            and not explicit_confirmation
        )
        keys = (
            "customerCode", "customerName", "regionCode", "regionName", "industryCode", "industryName",
            "taskNo", "trainBatchNo", "forecastBatchNo", "forecastHorizon",
        )
        # NEW 请求只能使用本轮条件；历史和页面上下文仅用于语义理解，不能
        # 自动成为 MCP 过滤参数。CONTINUE/MODIFY 才可继承最近兼容上下文。
        values = (
            {key: memory[key] for key in keys if memory.get(key) not in (None, "")}
            if inherit_context else {}
        )
        remembered_context = ActionExecutor._business_context_values(
            memory.get("currentBusinessContext")
        ) if inherit_context else {}
        values.update(remembered_context)
        remembered = memory.get("currentParameters")
        if inherit_context and isinstance(remembered, dict):
            values.update({
                key: value for key, value in remembered.items()
                # 下划线开头的是单轮控制字段（澄清、锁定、待执行工具等），
                # 绝不能跨轮恢复，否则一次追问会永久劫持后续所有请求。
                if value not in (None, "")
                and key != "confirmed"
                and key not in BUSINESS_CONTEXT_KEYS
                and not key.startswith("_")
            })
        current_context = ActionExecutor._business_context_values(
            metadata.get("currentBusinessContext")
        ) if inherit_context else {}
        values.update(current_context)
        values.update({
            key: value for key, value in metadata.items()
            if value not in (None, "")
            and key not in {"confirmed", "currentBusinessContext"}
            and key not in BUSINESS_CONTEXT_KEYS
            and not key.startswith("_")
        })
        values["conversationNo"] = conversation_no
        if semantics is not None:
            if semantics.intent is not None:
                values["_intent"] = semantics.intent.value
            if relation == ContextRelation.MODIFY and semantics.primary_request:
                groups = {
                    "region": ("regionCode", "regionName", "regionCodes", "regionNames"),
                    "industry": ("industryCode", "industryName", "industryCodes", "industryNames"),
                    "customer": ("customerCode", "customerName", "customerCodes", "customerNames"),
                }
                for group in semantics.primary_request.clear_parameters:
                    for key in groups.get(group, ()):
                        values.pop(key, None)
        locked_parameters = set(values.get("_lockedParameters") or [])
        resolved_entities = metadata.get("resolvedEntityParameters")
        if isinstance(resolved_entities, dict):
            # 这些值只可能来自 understand_request 的实体主数据校验，优先于
            # 本轮名称抽取，且锁定标准编码避免后续语义合并把它覆盖回名称。
            values.update({key: value for key, value in resolved_entities.items() if value not in (None, "", [])})
            locked_parameters.update(
                key for key in resolved_entities if not key.startswith("_")
            )
        pending = memory.get("pendingAction")
        has_pending_action = isinstance(pending, dict) and bool(pending.get("tool"))
        pending_interaction = memory.get("pendingInteraction")
        if clarification_reply and isinstance(pending_interaction, dict):
            snapshot = pending_interaction.get("parameters")
            if isinstance(snapshot, dict):
                values.update({
                    key: value
                    for key, value in snapshot.items()
                    if value not in (None, "") and key != "confirmed"
                    and (not key.startswith("_") or key in resumable_controls)
                })
        if skill_name == "customer-gas-forecast" and not explicit_confirmation:
            # 训练策略属于本次预测操作，不能从上一轮继承。新请求默认优先
            # 复用已有结果，仅在没有可用结果时自动训练；本轮明确语义随后覆盖。
            values.update(
                forceRetrain=False,
                autoTrain=True,
                trainingPreference="AUTO",
            )
        if explicit_confirmation and has_pending_action and isinstance(pending.get("parameters"), dict):
            snapshot = {
                key: value for key, value in pending["parameters"].items()
                if value not in (None, "") and key != "confirmed"
                and (not key.startswith("_") or key in resumable_controls)
            }
            values.update(snapshot)
            values["conversationNo"] = conversation_no
            locked_parameters.update(key for key in snapshot if not key.startswith("_"))
        if not (explicit_confirmation and has_pending_action and values.get("idempotencyKey")):
            seed = str(metadata.get("turnId") or f"{conversation_no}:{skill_name}:{message.strip()}")
            values["idempotencyKey"] = hashlib.sha256(seed.encode("utf-8")).hexdigest()[:32]
        # 待确认动作只在用户给出纯确认回复时恢复。用户输入新的业务要求时，
        # 必须重新规划，不能让上一轮 pendingTool 把新请求带回旧参数。
        if explicit_confirmation and has_pending_action:
            values["_pendingTool"] = pending["tool"]
        if explicit_confirmation and has_pending_action:
            values["confirmed"] = True
        for key, pattern in {
            # 不能使用 \b：中文“的”等字符在 Python Unicode 正则中也属于 word，
            # 会让编号在最后一个连字符处回退截断。
            "taskNo": r"(?<![A-Za-z0-9_-])(TASK[-_A-Za-z0-9]+)(?![A-Za-z0-9_-])",
            "trainBatchNo": r"(?<![A-Za-z0-9_-])((?:TRBATCH|TRAIN)[-_A-Za-z0-9]+)(?![A-Za-z0-9_-])",
            "forecastBatchNo": r"(?<![A-Za-z0-9_-])(FORECAST[-_A-Za-z0-9]+)(?![A-Za-z0-9_-])",
        }.items():
            match = re.search(pattern, message, re.I)
            if match:
                values[key] = match.group(1)
                # 用户明确输入的业务编号属于强标识，模型不得截断或改写。
                locked_parameters.add(key)
        # 实体、指代和时间范围统一由 understand_request 的语义层处理；Skill
        # 执行阶段只消费结构化结果，不再从原句用正则重新猜测客户或日期。
        if skill_name == "model-training-management" and any(word in message for word in ("重新训练", "重训", "重新建模")):
            values["forceRetrain"] = True
        retrain_requested = bool(
            re.search(
                r"(?:(?:重新|重做|重跑|再次|新一轮)(?:发起|开始|执行|进行|做|跑)?(?:一次)?(?:模型)?(?:训练|建模|学习)"
                r"|(?:训练|建模|学习)(?:也|同样)?(?:要|需要|得)?(?:重新|重做|重跑|再次|新一轮))",
                normalized_message,
            )
        )
        use_new_training_result = any(word in normalized_message for word in (
            "用新训练的结果", "使用新训练的结果", "用新训练结果", "使用新训练结果",
            "重新训练后预测", "先重新训练再预测", "重新训练再预测", "重训后预测",
        ))
        if skill_name == "customer-gas-forecast" and (retrain_requested or use_new_training_result):
            # 用户明确要求本轮预测必须基于新模型时，不能回退到默认的已有模型复用策略。
            values["forceRetrain"] = True
            values["autoTrain"] = True
            values["trainingPreference"] = "FORCE_RETRAIN"
            locked_parameters.update(("forceRetrain", "autoTrain", "trainingPreference"))
        # “继续/确认”等纯确认回复只负责批准上一轮已经解析完成的动作。
        # 此时 pendingAction 中的参数快照是唯一可信输入，不能再把确认词当成
        # 新业务请求交给语义模型，否则模型很容易因为文本里没有客户和周期而
        # 生成一次错误的补充提问。
        explicit_business_context = bool(
            semantics and semantics.has_explicit_entities
        )
        if semantics is not None and not (explicit_confirmation and has_pending_action):
            semantic_values = semantics.parameter_overrides()
            for code_key, name_key in (
                ("customerCode", "customerName"), ("regionCode", "regionName"), ("industryCode", "industryName")
            ):
                if code_key in semantic_values and code_key not in locked_parameters:
                    values.pop(name_key, None)
                elif name_key in semantic_values and name_key not in locked_parameters:
                    values.pop(code_key, None)
            # 模型语义只补充或修正非锁定参数。本轮原文中已经明确识别出的
            # 编码、地区、行业、预测天数等参数必须保持不变。
            accepted_semantic_values = {
                key: value for key, value in semantic_values.items() if key not in locked_parameters
            }
            # 多实体必须保持为集合，不能因为前面的兼容规则恰好抽到第一个名称，
            # 就悄悄降级成单实体执行。
            for plural_key, singular_keys in (
                ("customerNames", ("customerCode", "customerName")),
                ("regionNames", ("regionCode", "regionName")),
                ("industryNames", ("industryCode", "industryName")),
            ):
                if plural_key in accepted_semantic_values:
                    for singular_key in singular_keys:
                        values.pop(singular_key, None)
            current_customer_name = values.get("customerName") or accepted_semantic_values.get("customerName")
            if skill_name == "customer-gas-forecast" and current_customer_name:
                # 只有规则从本轮明确范围表达中锁定的地区/行业才可作为客户
                # 搜索约束；拒绝模型从“扬州中燃”之类客户名中重叠拆出的实体。
                for key in ("regionCode", "regionName", "industryCode", "industryName"):
                    if key not in locked_parameters:
                        accepted_semantic_values.pop(key, None)
                        values.pop(key, None)
            values.update(accepted_semantic_values)
            locked_parameters.update(accepted_semantic_values)
        if (
            skill_name == "business-data-query"
            and not (semantics and semantics.customer_names)
            and not (semantics and semantics.primary_request and semantics.primary_request.references)
            and not re.search(r"当前|这个|该客户|这些客户", message)
            and re.search(r"客户(?:列表|清单|有哪些|信息|详情)?|有哪些客户", message)
            and not re.search(r"训练|预测|任务|状态|结果|历史|最新", message)
        ):
            # 浏览客户列表时，页面上恰好选中的单客户不是隐式过滤条件。
            values.pop("customerCode", None)
            values.pop("customerName", None)
        if clarification_reply:
            # 候选值由上一轮实体查询结果生成。客户端只回传不透明 token，真正
            # 的业务编码从 pendingInteraction 恢复，不能信任用户直接提交编码。
            values.update(clarification_values)
            locked_parameters.update(
                key for key in clarification_values if not key.startswith("_")
            )
            if clarification_values.get("_scopeRejected") is True:
                # “重新选择范围”必须同时清除单实体和批量实体。否则上一轮
                # regionNames/industryNames 会继续触发范围确认，用户无法真正改选。
                for key in ENTITY_ARGUMENT_KEYS:
                    values.pop(key, None)
        pending_tool = {
            "customer-gas-forecast": "forecast_start",
            "model-training-management": "training_start",
        }.get(skill_name)
        current_focus = current_context or remembered_context
        if not current_focus:
            current_focus = ActionExecutor._business_context_values(values)
        if (
            not explicit_confirmation
            and not has_pending_action
            and not clarification_reply
            and not explicit_business_context
            and pending_tool in WRITE_TOOLS
            and current_focus
            and not values.get("_clarificationQuestion")
        ):
            action_name = "预测" if pending_tool == "forecast_start" else "训练"
            values["_clarificationQuestion"] = (
                f"当前业务对象是{ActionExecutor._business_context_label(current_focus)}，"
                f"是否针对该业务对象发起{action_name}任务？"
            )
            values["_clarificationPendingTool"] = pending_tool
        range_scope = any(values.get(key) for key in (
            "regionCode", "regionName", "regionCodes", "regionNames",
            "industryCode", "industryName", "industryCodes", "industryNames",
        ))
        if (
            skill_name == "model-training-management"
            and not values.get("customerCode")
            and not values.get("customerName")
            and range_scope
        ):
            all_matched = bool(
                semantics
                and semantics.target_selection == TargetSelection.ALL_MATCHED
            ) or clarification_values.get("_scopeConfirmed") is True
            if all_matched:
                values["bulk"] = True
            elif not values.get("_clarificationQuestion"):
                values["bulk"] = False
                values["_clarificationQuestion"] = (
                    "已识别到地区或行业范围，是否对该范围内全部匹配客户发起训练？"
                )
                values["_clarificationPendingTool"] = pending_tool
        if skill_name in {"customer-gas-forecast", "model-training-management"}:
            if any(word in message for word in ("月度", "月销量", "月用气")):
                values["agentCode"] = "monthly-sales"
                values["forecastFrequency"] = "MONTHLY"
            elif any(word in message for word in ("冬季", "保供", "旬")):
                values["agentCode"] = "winter-supply"
                values["forecastFrequency"] = "TENDAY"
            else:
                values.setdefault("agentCode", "short-term")
                if skill_name == "customer-gas-forecast":
                    values.setdefault("forecastFrequency", "DAILY")
        if (
            skill_name == "business-data-query"
            and values.get("fetchAll") is True
            and any(values.get(key) for key in ("regionName", "regionCode", "industryName", "industryCode"))
        ):
            # 本轮查询的是地区/行业范围内的客户集合，旧会话中的单客户条件
            # 不属于当前请求，必须清除，避免列表被历史客户错误收窄。
            values.pop("customerCode", None)
            values.pop("customerName", None)
        if locked_parameters:
            values["_lockedParameters"] = sorted(locked_parameters)
        return values

    @staticmethod
    def _business_context_values(value: object) -> dict[str, Any]:
        """只提取地区、行业和客户字段，避免把整个页面对象混入工具参数。"""
        if not isinstance(value, dict):
            return {}
        return {
            key: value[key]
            for key in BUSINESS_CONTEXT_KEYS
            if value.get(key) not in (None, "")
        }

    @staticmethod
    def _business_context_label(context: dict[str, Any]) -> str:
        """生成面向用户的当前业务对象说明，名称优先、编码兜底。"""
        labels = []
        for label, name_key, code_key in (
            ("地区", "regionName", "regionCode"),
            ("行业", "industryName", "industryCode"),
            ("客户", "customerName", "customerCode"),
        ):
            value = context.get(name_key) or context.get(code_key)
            if value not in (None, ""):
                labels.append(f"{label}“{value}”")
        return "、".join(labels)

    @staticmethod
    def _scope_issue(message: str) -> ResolutionIssue:
        """页面焦点只是候选范围，必须与真正的写操作确认分开。"""
        return ResolutionIssue(
            reason=ClarificationReason.SCOPE_AMBIGUOUS,
            field="businessScope",
            message=message,
            input_type=ClarificationInputType.CONFIRM,
            options=[
                ClarificationOption(value="accept", label="确认使用该范围"),
                ClarificationOption(value="change", label="重新选择范围"),
            ],
            details={
                "candidateMap": {
                    "accept": {"_scopeConfirmed": True},
                    "change": {"_scopeRejected": True},
                },
            },
        )

    @staticmethod
    def _confirmation_issue(message: str) -> ResolutionIssue:
        """把有副作用动作的确认建模为独立交互，而不是普通缺参。"""
        return ResolutionIssue(
            reason=ClarificationReason.WRITE_CONFIRMATION_REQUIRED,
            field="confirmExecution",
            message=message,
            input_type=ClarificationInputType.CONFIRM,
            options=[
                ClarificationOption(value="confirm", label="确认执行"),
                ClarificationOption(value="reject", label="取消"),
            ],
        )

    @classmethod
    def _missing_issues(
        cls,
        tool: dict[str, Any],
        missing: list[str],
        arguments: dict[str, Any],
    ) -> list[ResolutionIssue]:
        """把 JSON Schema 缺失项转换成稳定、前端可渲染的问题。"""
        schema = tool.get("inputSchema") if isinstance(tool.get("inputSchema"), dict) else {}
        properties, _ = cls._schema_fields(schema)
        issues: list[ResolutionIssue] = []
        for raw_name in missing:
            if tool.get("name") == "forecast_start" and raw_name == "forecastHorizon":
                _, unit, _, options = cls._forecast_period_config(arguments)
                issues.append(ResolutionIssue(
                    reason=ClarificationReason.PARAMETER_MISSING,
                    field="forecastHorizon",
                    message=f"请选择预测周期（单位：{unit}）。",
                    input_type=ClarificationInputType.SELECT,
                    options=[
                        ClarificationOption(value=str(value), label=f"{value}{unit}")
                        for value in options
                    ],
                ))
                continue
            alternatives = [item for item in re.split(r"\s+或\s+|/", raw_name) if item]
            entity_field = next(
                (
                    name_key
                    for code_key, name_key in (
                        ("customerCode", "customerName"),
                        ("regionCode", "regionName"),
                        ("industryCode", "industryName"),
                    )
                    if code_key in alternatives or name_key in alternatives
                ),
                None,
            )
            field_name = entity_field or raw_name
            rule = properties.get(field_name)
            input_type = ClarificationInputType.TEXT
            if isinstance(rule, dict):
                if rule.get("enum"):
                    input_type = ClarificationInputType.SELECT
                elif rule.get("type") in {"integer", "number"}:
                    input_type = ClarificationInputType.NUMBER
                elif rule.get("format") == "date":
                    input_type = ClarificationInputType.DATE
            label = cls._parameter_label(field_name, properties)
            issues.append(ResolutionIssue(
                reason=(
                    ClarificationReason.ENTITY_MISSING
                    if entity_field
                    else ClarificationReason.PARAMETER_MISSING
                ),
                field=field_name,
                message=f"请提供{label}。",
                input_type=input_type,
                options=[
                    ClarificationOption(value=str(value), label=str(value))
                    for value in (rule.get("enum") if isinstance(rule, dict) else []) or []
                ],
            ))
        return issues

    @classmethod
    def _tool_result_issues(
        cls,
        tool_name: str,
        outcome: str,
        arguments: dict[str, Any],
        result: dict[str, Any],
    ) -> list[ResolutionIssue]:
        """根据程序可验证的工具状态生成澄清原因，禁止让模型自由猜测。"""
        message = str(result.get("message") or "请补充必要信息。")
        if outcome == "NEED_INFORMATION":
            return [ResolutionIssue(
                reason=ClarificationReason.PARAMETER_MISSING,
                field="parameters",
                message=message,
            )]
        if outcome == "NEED_CONFIRMATION":
            return [cls._confirmation_issue(message)]
        if outcome == "NEED_SELECTION":
            return [ResolutionIssue(
                reason=ClarificationReason.SCOPE_AMBIGUOUS,
                field="selection",
                message=message,
                input_type=ClarificationInputType.TEXT,
            )]
        if outcome == "NEED_CLARIFICATION":
            return [ResolutionIssue(
                reason=ClarificationReason.PARAMETER_CONFLICT,
                field="parameters",
                message=message,
            )]
        return []


    @staticmethod
    def _message_issues(status: str, message: str) -> list[ResolutionIssue]:
        """为模型直接追问保留结构化兜底，后续可由 RequestResolver 细化。"""
        normalized = str(status).upper()
        if normalized == "NEED_INFORMATION":
            reason = ClarificationReason.PARAMETER_MISSING
        elif normalized == "NEED_CLARIFICATION":
            reason = ClarificationReason.SCOPE_AMBIGUOUS
        else:
            return []
        return [ResolutionIssue(reason=reason, field="request", message=message)]

    @staticmethod
    def _apply_locked_parameters(parameters: dict[str, Any], arguments: dict[str, Any]) -> None:
        """本轮用户明确给出的关键参数优先于模型生成的工具参数。"""
        for key in parameters.get("_lockedParameters") or []:
            if key in parameters:
                arguments[key] = parameters[key]

    @staticmethod
    def _apply_trusted_entity_arguments(
        parameters: dict[str, Any],
        arguments: dict[str, Any],
    ) -> None:
        """实体只能来自语义抽取、页面上下文或实体工具，规划模型不得生成。"""
        for key in ENTITY_ARGUMENT_KEYS:
            if parameters.get(key) not in (None, "", []):
                arguments[key] = parameters[key]
            else:
                arguments.pop(key, None)

    @staticmethod
    def _adapt_tool_parameters(
        tool: dict[str, Any], arguments: dict[str, Any],
    ) -> tuple[dict[str, Any], str | None]:
        """将实体校验的批量结果适配为目标 MCP 工具的实际 Schema 参数。

        实体解析统一用 ``xxxCodes`` 数组表达，以便保留多实体事实；部分旧 MCP
        工具仍只接受 ``xxxCode`` 单值。本方法是唯一允许二者转换的位置，绝不
        静默取第一个或让 Schema 过滤器吞掉用户明确给出的筛选条件。
        """
        result = dict(arguments)
        schema = tool.get("inputSchema") if isinstance(tool.get("inputSchema"), dict) else {}
        properties, _ = ActionExecutor._schema_fields(schema)
        tool_name = str(tool.get("title") or tool.get("name") or "当前工具")
        for plural, singular in (
            ("customerCodes", "customerCode"),
            ("regionCodes", "regionCode"),
            ("industryCodes", "industryCode"),
        ):
            if plural not in result:
                continue
            values = result[plural]
            if not isinstance(values, list):
                return result, f"实体参数 {plural} 必须是数组，无法调用{tool_name}。"
            values = [value for value in values if value not in (None, "")]
            if plural in properties:
                result[plural] = values
                continue
            if singular not in properties:
                return result, f"{tool_name}不支持已指定的{ActionExecutor._entity_label(plural)}筛选条件，请调整查询方式。"
            if len(values) > 1:
                return result, (
                    f"{tool_name}一次只能按单个{ActionExecutor._entity_label(singular)}筛选，"
                    f"但已识别到{len(values)}个，无法自动忽略其中任何一个。"
                )
            if len(values) == 1:
                existing = result.get(singular)
                if existing not in (None, "", values[0]):
                    return result, (
                        f"{ActionExecutor._entity_label(singular)}参数冲突："
                        f"已给出“{existing}”，实体校验结果为“{values[0]}”。"
                    )
                result[singular] = values[0]
            result.pop(plural, None)

        # 名称是供语义理解和实体校验使用的内部线索；只要对应编码已经被保留或
        # 转换成目标工具支持的字段，就不应作为未支持业务条件报错。
        name_to_code = {
            "customerName": ("customerCode", "customerCodes"),
            "customerNames": ("customerCode", "customerCodes"),
            "regionName": ("regionCode", "regionCodes"),
            "regionNames": ("regionCode", "regionCodes"),
            "industryName": ("industryCode", "industryCodes"),
            "industryNames": ("industryCode", "industryCodes"),
        }
        for name, codes in name_to_code.items():
            if name in result and name not in properties and any(code in result for code in codes):
                result.pop(name, None)

        # Schema 过滤前，对仍未被工具接收的实体条件报错，而非悄悄删除。
        unsupported = [
            key for key in ENTITY_ARGUMENT_KEYS
            if key in result and result[key] not in (None, "", []) and key not in properties
        ]
        if unsupported:
            labels = "、".join(ActionExecutor._entity_label(key) for key in unsupported)
            return result, f"{tool_name}不支持已指定的{labels}筛选条件，请调整查询方式。"
        return result, None

    @staticmethod
    def _entity_label(name: str) -> str:
        if name.startswith("customer"):
            return "客户"
        if name.startswith("region"):
            return "地区"
        if name.startswith("industry"):
            return "行业"
        return name

    @staticmethod
    def _confirmation_message(tool: dict[str, Any], arguments: dict[str, Any]) -> str | None:
        annotations = tool.get("annotations") if isinstance(tool.get("annotations"), dict) else {}
        if annotations.get("readOnlyHint") is True:
            return None
        if arguments.get("confirmed") is True:
            return None
        schema = tool.get("inputSchema") if isinstance(tool.get("inputSchema"), dict) else {}
        properties, required = ActionExecutor._schema_fields(schema)
        confirmation_required = (
            "confirmed" in required
            or ("confirmed" in properties and any((
                arguments.get("bulk") is True,
                arguments.get("autoTrain") is True,
                arguments.get("forceRetrain") is True,
                arguments.get("trainingPreference") in {"AUTO", "FORCE_RETRAIN"},
            )))
        )
        if confirmation_required:
            title = tool.get("title") or tool.get("name")
            if tool.get("name") == "forecast_start":
                forecast_name, unit, default, _ = ActionExecutor._forecast_period_config(arguments)
                scope = ActionExecutor._forecast_scope(arguments)
                horizon = arguments.get("forecastHorizon")
                horizon_text = f"未来{horizon}{unit}" if horizon else f"推荐默认{default}{unit}"
                preference = arguments.get("trainingPreference")
                if arguments.get("forceRetrain") is True or preference == "FORCE_RETRAIN":
                    training_text = "重新发起训练，并使用本次新训练结果预测"
                elif preference == "USE_EXISTING_ONLY" or arguments.get("autoTrain") is False:
                    training_text = "仅使用已有训练结果，不发起新训练"
                else:
                    training_text = "优先使用已有训练结果；没有可用结果时自动训练"
                return (
                    f"即将发起{forecast_name}：\n"
                    f"- 预测范围：{scope}\n"
                    f"- 预测周期：{horizon_text}\n"
                    f"- 训练策略：{training_text}\n"
                    "该操作会创建业务任务，是否确认继续？"
                )
            return f"即将执行“{title}”，该操作会写入业务数据或改变任务状态，是否确认继续？"
        return None

    @staticmethod
    def _apply_current_entity_arguments(current: dict[str, Any], arguments: dict[str, Any]) -> None:
        """按本轮结构化字段覆盖历史实体，不解析或假设任何业务编码格式。"""
        pairs = (("customerCode", "customerName"), ("regionCode", "regionName"), ("industryCode", "industryName"))
        for code_key, name_key in pairs:
            if current.get(code_key) not in (None, ""):
                arguments.pop(name_key, None)
            elif current.get(name_key) not in (None, ""):
                arguments.pop(code_key, None)
        if any(current.get(key) not in (None, "") for key in ("customerCode", "customerName")):
            for key in ("regionCode", "regionName", "industryCode", "industryName"):
                if current.get(key) in (None, ""):
                    arguments.pop(key, None)

    @staticmethod
    def _validated_arguments(tool: dict[str, Any], arguments: dict[str, Any]) -> dict[str, Any]:
        values = {key: value for key, value in arguments.items() if not key.startswith("_")}
        schema = tool.get("inputSchema") if isinstance(tool.get("inputSchema"), dict) else {}
        properties, _ = ActionExecutor._schema_fields(schema)
        if not properties:
            return values
        normalized: dict[str, Any] = {}
        for key, value in values.items():
            if key not in properties:
                continue
            rule = properties[key] if isinstance(properties[key], dict) else {}
            # HTML select/input 回传文本；在 Schema 明确要求数值且内容完整合法时
            # 才转换，避免把任意业务编码或日期错误地强制转型。
            if isinstance(value, str) and rule.get("type") == "integer" and re.fullmatch(r"[-+]?\d+", value.strip()):
                value = int(value.strip())
            elif isinstance(value, str) and rule.get("type") == "number":
                try:
                    value = float(value.strip())
                except ValueError:
                    pass
            normalized[key] = value
        return normalized

    @staticmethod
    def _missing_required_arguments(tool: dict[str, Any], arguments: dict[str, Any]) -> list[str]:
        schema = tool.get("inputSchema") if isinstance(tool.get("inputSchema"), dict) else {}
        return ActionExecutor._missing_for_schema(schema, arguments)

    @staticmethod
    def _missing_for_schema(schema: dict[str, Any], arguments: dict[str, Any]) -> list[str]:
        missing = [name for name in schema.get("required") or [] if arguments.get(name) in (None, "", [])]
        for child in schema.get("allOf") or []:
            if not isinstance(child, dict):
                continue
            condition = child.get("if")
            if not isinstance(condition, dict) or ActionExecutor._schema_condition_matches(condition, arguments):
                target = child.get("then") if isinstance(child.get("then"), dict) else child
                missing.extend(ActionExecutor._missing_for_schema(target, arguments))
        any_of = schema.get("anyOf") or []
        if any_of and not any(
            isinstance(branch, dict) and not ActionExecutor._missing_for_schema(branch, arguments)
            for branch in any_of
        ):
            alternatives = []
            for branch in any_of:
                if isinstance(branch, dict):
                    names = branch.get("required") or []
                    if names:
                        alternatives.append("/".join(names))
            if alternatives:
                missing.append(" 或 ".join(alternatives))
        return list(dict.fromkeys(missing))

    @staticmethod
    def _schema_condition_matches(condition: dict[str, Any], arguments: dict[str, Any]) -> bool:
        if any(arguments.get(name) in (None, "", []) for name in condition.get("required") or []):
            return False
        for name, rule in (condition.get("properties") or {}).items():
            # JSON Schema 的 properties 不会让字段自动变成必填；字段缺失时条件仍可成立。
            if name in arguments and isinstance(rule, dict) and "const" in rule and arguments.get(name) != rule["const"]:
                return False
        return True

    @staticmethod
    def _missing_parameters_message(tool: dict[str, Any], missing: list[str], arguments: dict[str, Any]) -> str:
        if tool.get("name") == "forecast_start" and missing == ["forecastHorizon"]:
            forecast_name, unit, default, _ = ActionExecutor._forecast_period_config(arguments)
            return (
                f"已确认将为{ActionExecutor._forecast_scope(arguments)}发起{forecast_name}。\n"
                f"推荐预测周期为{default}{unit}，请选择本次要预测的周期。"
            )
        schema = tool.get("inputSchema") if isinstance(tool.get("inputSchema"), dict) else {}
        properties, _ = ActionExecutor._schema_fields(schema)
        labels = []
        for name in missing:
            if " 或 " in name or "/" in name:
                alternatives = re.split(r"\s+或\s+|/", name)
                labels.append("或".join(ActionExecutor._parameter_label(item, properties) for item in alternatives))
                continue
            labels.append(ActionExecutor._parameter_label(name, properties))
        known = [key for key, value in arguments.items() if value not in (None, "", []) and not key.startswith("_")]
        suffix = f" 已识别参数：{', '.join(known)}。" if known else ""
        return f"还需要提供：{'、'.join(labels)}。{suffix}"

    @staticmethod
    def _parameter_label(name: str, properties: dict[str, Any]) -> str:
        rule = properties.get(name)
        description = rule.get("description") if isinstance(rule, dict) else None
        return f"{name}（{description}）" if description else name

    @staticmethod
    def _forecast_period_config(arguments: dict[str, Any]) -> tuple[str, str, int, list[int]]:
        """按智能体类型定义周期的展示单位、推荐值和可选项。"""
        agent_code = str(arguments.get("agentCode") or "").strip().lower()
        if agent_code == "monthly-sales":
            return "月度销量预测", "个月", 12, [3, 6, 12, 18, 24]
        if agent_code == "winter-supply":
            return "冬季保供预测", "旬", 18, [6, 9, 12, 18]
        return "短期客户预测", "天", 30, [7, 15, 30, 45, 60, 90]

    @staticmethod
    def _forecast_scope(arguments: dict[str, Any]) -> str:
        values = [
            arguments.get("regionName"),
            arguments.get("industryName"),
            arguments.get("customerName") or arguments.get("customerCode"),
        ]
        return " / ".join(str(value) for value in values if value not in (None, "")) or "当前客户"

    @staticmethod
    def _argument_validation_error(tool: dict[str, Any], arguments: dict[str, Any]) -> str | None:
        schema = tool.get("inputSchema") if isinstance(tool.get("inputSchema"), dict) else {}
        properties, _ = ActionExecutor._schema_fields(schema)
        for name, value in arguments.items():
            rule = properties.get(name)
            if not isinstance(rule, dict) or value is None:
                continue
            allowed = rule.get("enum")
            if isinstance(allowed, list) and value not in allowed:
                return f"参数 {name} 的值不支持，可选值为：{', '.join(map(str, allowed))}。"
            if isinstance(value, (int, float)) and not isinstance(value, bool):
                if rule.get("minimum") is not None and value < rule["minimum"]:
                    return f"参数 {name} 不能小于 {rule['minimum']}。"
                if rule.get("maximum") is not None and value > rule["maximum"]:
                    return f"参数 {name} 不能大于 {rule['maximum']}。"
        start = arguments.get("trainingStartDate")
        end = arguments.get("trainingEndDate")
        if isinstance(start, str) and isinstance(end, str) and start > end:
            return "训练开始日期不能晚于训练结束日期，请重新确认训练范围。"
        # 手工校验负责保留面向用户的中文提示；其余 JSON Schema 规则（类型、
        # pattern、oneOf/allOf 等）在真正调用 MCP 前统一校验。required 仍交给
        # _missing_required_arguments 生成更适合交互的补参提示。
        try:
            errors = sorted(
                Draft202012Validator(schema).iter_errors(arguments),
                key=lambda error: list(error.absolute_path),
            )
        except Exception:
            # 工具目录本身若提供了不合法 Schema，不能使 Agent 崩溃；后端工具
            # 仍会做最终校验。
            errors = []
        for error in errors:
            if error.validator == "required":
                continue
            field = ".".join(str(item) for item in error.absolute_path) or "参数"
            return f"参数 {field} 不符合工具要求：{error.message}。"
        return None

    @staticmethod
    def _message_decision(content: str | None) -> dict[str, Any]:
        message = (content or "请补充必要信息。").strip()
        candidate = re.sub(r"^```(?:json|python)?\s*|\s*```$", "", message, flags=re.I).strip()
        parsed: Any = None
        try:
            parsed = json.loads(candidate)
        except (json.JSONDecodeError, TypeError):
            # 部分兼容模型会返回Python字面量格式（单引号）。literal_eval只解析
            # 字面量，不执行代码，可安全兼容这种非标准但常见的输出。
            try:
                parsed = ast.literal_eval(candidate)
            except (ValueError, SyntaxError, TypeError):
                parsed = None
        if parsed is not None:
            allowed = {"COMPLETED", "NEED_INFORMATION", "NEED_CLARIFICATION", "NOT_FOUND", "FAILED"}
            if isinstance(parsed, dict) and parsed.get("status") in allowed and str(parsed.get("message") or "").strip():
                return {"type": "message", "status": parsed["status"], "message": str(parsed["message"]).strip()}
        # 兼容暂未遵循 JSON 输出约定的模型，至少不把明显失败或追问标成 COMPLETED。
        if any(word in message for word in ("没有找到", "没有查询到", "不存在", "未找到")):
            status = "NOT_FOUND"
        elif any(word in message for word in ("冲突", "不匹配", "超出", "不支持", "请选择", "请确认您的具体需求")):
            status = "NEED_CLARIFICATION"
        elif any(word in message for word in ("请提供", "请补充", "还需要", "缺少")):
            status = "NEED_INFORMATION"
        else:
            status = "COMPLETED"
        return {"type": "message", "status": status, "message": message}

    @staticmethod
    def _schema_fields(schema: dict[str, Any]) -> tuple[dict[str, Any], set[str]]:
        """合并组合 schema 中声明的字段，避免 allOf 参数被误删。"""
        properties = dict(schema.get("properties") or {})
        required = set(schema.get("required") or [])
        for child in schema.get("allOf") or []:
            if isinstance(child, dict):
                child_properties, child_required = ActionExecutor._schema_fields(child)
                properties.update(child_properties)
                required.update(child_required)
        return properties, required

    @staticmethod
    def _result_status(result: dict[str, Any]) -> str:
        data = result.get("data") if isinstance(result.get("data"), dict) else {}
        raw = str(data.get("status") or result.get("status") or "").upper()
        aliases = {
            "INVALID_ARGUMENT": "NEED_INFORMATION",
            "CONFIRMATION_REQUIRED": "NEED_CONFIRMATION",
            "MULTIPLE": "NEED_SELECTION",
        }
        status = aliases.get(raw, raw)
        terminal = {
            "NEED_INFORMATION", "NEED_CONFIRMATION", "NEED_CLARIFICATION", "NEED_SELECTION",
            "EMPTY_RESULT", "NOT_FOUND", "CANCELLED", "UNKNOWN",
        }
        if status in terminal:
            return status
        if result.get("success") is False:
            return "FAILED"
        return status or "COMPLETED"

    @staticmethod
    def _can_model_decide(result: dict[str, Any]) -> bool:
        data = result.get("data") if isinstance(result.get("data"), dict) else {}
        actions = data.get("allowedActions") if isinstance(data.get("allowedActions"), list) else []
        return "MODEL_DECISION" in actions

    async def _call_with_retry(
        self,
        mcp: StreamableHttpMcpClient,
        tool: dict[str, Any],
        name: str,
        arguments: dict[str, Any],
    ) -> tuple[dict[str, Any], int]:
        read_only = bool((tool.get("annotations") or {}).get("readOnlyHint")) and name not in WRITE_TOOLS
        max_attempts = 1 + (self._settings.agent_max_read_retries if read_only else 0)
        for attempt in range(1, max_attempts + 1):
            result = self._validate_tool_result(await self._call(mcp, name, arguments))
            if not self._is_retryable_system_error(result) or attempt >= max_attempts:
                if name in WRITE_TOOLS and self._is_retryable_system_error(result):
                    result = {
                        **result,
                        "status": "UNKNOWN",
                        "message": "写操作提交结果未知，系统不会自动重试；请先查询任务状态，避免重复创建任务。",
                    }
                return result, attempt
            await asyncio.sleep(0.2 * attempt)
        raise RuntimeError("工具重试控制器状态异常")

    @staticmethod
    def _validate_tool_result(result: dict[str, Any]) -> dict[str, Any]:
        if not isinstance(result, dict):
            return {
                "success": False, "status": "SYSTEM_ERROR", "errorType": "SYSTEM",
                "errorCode": "INVALID_TOOL_RESPONSE", "message": "工具未返回结构化结果。", "retryable": True,
            }
        missing = [key for key in ("success", "status", "message", "retryable") if key not in result]
        if missing:
            return {
                "success": False, "status": "SYSTEM_ERROR", "errorType": "SYSTEM",
                "errorCode": "INVALID_TOOL_RESPONSE",
                "message": f"工具返回缺少字段：{', '.join(missing)}。", "retryable": True,
            }
        if not isinstance(result.get("success"), bool) or not isinstance(result.get("retryable"), bool):
            return {
                "success": False, "status": "SYSTEM_ERROR", "errorType": "SYSTEM",
                "errorCode": "INVALID_TOOL_RESPONSE", "message": "工具返回字段类型不正确。", "retryable": True,
            }
        return result

    @staticmethod
    def _normalize_empty_query_result(name: str, result: dict[str, Any], arguments: dict[str, Any]) -> dict[str, Any]:
        """训练查询返回空集合时不能伪装成查询成功。"""
        data = result.get("data") if isinstance(result.get("data"), dict) else {}
        if name == "training_result_query":
            batches = data.get("batches")
            if isinstance(batches, list) and not batches:
                batch_no = arguments.get("trainBatchNo")
                return {
                    **result,
                    "success": False,
                    "status": "NOT_FOUND",
                    "errorType": "BUSINESS",
                    "errorCode": "TRAINING_RESULT_NOT_FOUND",
                    "message": f"未找到训练批次：{batch_no}" if batch_no else "没有找到符合条件的训练结果",
                    "retryable": False,
                }
        return result

    @staticmethod
    def _is_retryable_system_error(result: dict[str, Any]) -> bool:
        error_code = str(result.get("errorCode") or "").upper()
        error_type = str(result.get("errorType") or "").upper()
        return bool(result.get("retryable")) and (
            error_type == "SYSTEM" or error_code in {"INTERNAL_ERROR", "MCP_CLIENT_ERROR", "UPSTREAM_TIMEOUT", "INVALID_TOOL_RESPONSE"}
        )

    @staticmethod
    async def _call(mcp: StreamableHttpMcpClient, name: str, arguments: dict[str, Any]) -> dict[str, Any]:
        try:
            raw = await mcp.call_tool(name, arguments)
        except McpClientError as exc:
            return {
                "success": False, "status": "SYSTEM_ERROR", "errorType": "SYSTEM",
                "errorCode": "MCP_CLIENT_ERROR", "message": str(exc), "retryable": True,
            }
        structured = raw.get("structuredContent") or raw.get("structured_content")
        if not isinstance(structured, dict):
            return {
                "success": False, "status": "SYSTEM_ERROR", "errorType": "SYSTEM",
                "errorCode": "INVALID_TOOL_RESPONSE", "message": f"工具 {name} 未返回结构化结果", "retryable": True,
            }
        return structured

    @staticmethod
    def _merge_result(parameters: dict[str, Any], result: dict[str, Any]) -> None:
        data = result.get("data") if isinstance(result.get("data"), dict) else {}
        proposed = data.get("proposedParameters") if isinstance(data.get("proposedParameters"), dict) else {}
        # 只保存服务端明确建议的下一次调用参数；最终执行仍需经过工具 Schema 和用户确认校验。
        parameters.update({key: value for key, value in proposed.items() if value not in (None, "")})
        if str(data.get("status") or "").upper() == "UNIQUE" and any(
            isinstance(proposed.get(key), list) and proposed.get(key)
            for key in ("customerCodes", "regionCodes", "industryCodes")
        ):
            parameters["_entityBatchResolved"] = True
            validated = set(parameters.get("_validatedEntityParameters") or [])
            validated.update(
                key for key in (
                    "customerCodes", "customerNames", "regionCodes", "regionNames",
                    "industryCodes", "industryNames",
                )
                if proposed.get(key) not in (None, "", [])
            )
            parameters["_validatedEntityParameters"] = sorted(validated)
        candidates = data.get("candidates") if isinstance(data.get("candidates"), list) else []
        if str(data.get("status") or "").upper() == "UNIQUE" and len(candidates) == 1:
            candidate = candidates[0]
            candidate_type = str(candidate.get("type") or "").upper()
            keys = {
                "CUSTOMER": ("customerCode", "customerName", "regionCode", "regionName", "industryCode", "industryName"),
                "REGION": ("regionCode", "regionName"),
                "INDUSTRY": ("industryCode", "industryName"),
            }.get(candidate_type, ())
            for key in keys:
                source = {"customerCode": "code", "customerName": "name"}.get(key, key)
                if candidate.get(source):
                    parameters[key] = candidate[source]
            validated = set(parameters.get("_validatedEntityParameters") or [])
            validated.update(key for key in keys if parameters.get(key) not in (None, ""))
            parameters["_validatedEntityParameters"] = sorted(validated)
        for key in ("taskNo", "trainBatchNo", "forecastBatchNo"):
            if data.get(key):
                parameters[key] = data[key]
