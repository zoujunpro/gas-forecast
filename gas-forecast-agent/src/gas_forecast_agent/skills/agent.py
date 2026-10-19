"""在 Skill 约束下让模型逐步选择 MCP 工具。"""

from __future__ import annotations

from dataclasses import dataclass, field
import asyncio
import hashlib
import json
import re
import time
from typing import Any

from gas_forecast_agent.config import Settings
from gas_forecast_agent.llm import build_llm_client
from gas_forecast_agent.mcp import McpClientError, StreamableHttpMcpClient

from .models import SkillDefinition
from .semantics import RequestSemantics


WRITE_TOOLS = {"forecast_start", "training_start", "task_resume", "task_retry"}
ASYNC_TOOLS = WRITE_TOOLS
DIRECT_RESULT_TOOLS = {
    "training_result_get", "training_latest_get", "training_history_get", "training_status_get",
    "forecast_result_get", "forecast_latest_get", "forecast_history_get", "forecast_status_get",
    "task_status_get", "task_steps_get",
}


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


class SkillAgent:
    """模型提出下一步；控制器校验白名单、确认和停止条件。"""

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
        tool_catalog = await mcp.list_tools()
        tools = {item["name"]: item for item in tool_catalog if item.get("name") in skill.allowed_tools}
        parameters = self._initial_parameters(message, memory, metadata, conversation_no, skill.name, semantics)
        trace: list[dict[str, Any]] = []
        if parameters.get("_clarificationQuestion"):
            return SkillRunResult(
                status="NEED_CLARIFICATION",
                message=str(parameters["_clarificationQuestion"]),
                parameters=parameters,
                available_tools=list(tools),
                tool_trace=trace,
            )
        started_at = time.monotonic()
        tool_counts: dict[str, int] = {}
        write_calls = 0

        for _ in range(self._settings.agent_max_steps):
            if time.monotonic() - started_at >= self._settings.agent_max_duration_seconds:
                return self._budget_exceeded("处理时间达到安全上限", parameters, tools, trace)
            decision = await self._decide(skill, message, history, parameters, trace, list(tools.values()))
            if decision["type"] == "message":
                if not trace and skill.name in {"customer-gas-forecast", "model-training-management"} and decision.get("status", "COMPLETED") == "COMPLETED":
                    return SkillRunResult(
                        status="FAILED",
                        message="本次请求没有执行任何业务工具，未创建训练或预测任务。请补充必要信息后重试。",
                        parameters=parameters,
                        available_tools=list(tools),
                        tool_trace=trace,
                    )
                last = trace[-1] if trace else None
                return SkillRunResult(
                    status=decision.get("status", "COMPLETED"),
                    message=decision["message"],
                    parameters=parameters,
                    available_tools=list(tools),
                    executed_tool=last["tool"] if last else None,
                    execution_result=last["result"] if last else None,
                    tool_trace=trace,
                )

            name = decision["name"]
            current_arguments = decision.get("arguments", {})
            arguments = {**parameters, **current_arguments}
            self._apply_locked_parameters(parameters, arguments)
            self._apply_current_entity_arguments(current_arguments, arguments)
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
            arguments = self._validated_arguments(tools[name], arguments)
            argument_error = self._argument_validation_error(tools[name], arguments)
            if argument_error:
                return SkillRunResult(
                    status="NEED_CLARIFICATION",
                    message=argument_error,
                    parameters=parameters,
                    available_tools=list(tools),
                    tool_trace=trace,
                )
            missing = self._missing_required_arguments(tools[name], arguments)
            # confirmed 是交互式确认字段，不应被当成普通业务参数向用户索取。
            business_missing = [name for name in missing if name != "confirmed"]
            if business_missing:
                return SkillRunResult(
                    status="NEED_INFORMATION",
                    message=self._missing_parameters_message(tools[name], business_missing, arguments),
                    parameters=parameters,
                    available_tools=list(tools),
                    tool_trace=trace,
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
                )
            if missing:
                return SkillRunResult(
                    status="NEED_INFORMATION",
                    message=self._missing_parameters_message(tools[name], missing, arguments),
                    parameters=parameters,
                    available_tools=list(tools),
                    tool_trace=trace,
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
                return SkillRunResult(
                    status=outcome,
                    message=str(result.get("message") or "工具执行失败。"),
                    parameters=parameters,
                    available_tools=list(tools),
                    executed_tool=name,
                    execution_result=result,
                    pending_tool=name if outcome == "NEED_CONFIRMATION" else None,
                    tool_trace=trace,
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
            # 这些查询工具已经返回可直接展示的结构化业务数据，PresentationPlanner
            # 会负责卡片和图表。再次请求模型只会增加延迟，并可能把真实数值解释错。
            if name in DIRECT_RESULT_TOOLS:
                return SkillRunResult(
                    status=str(result.get("status") or "COMPLETED"),
                    message=str(result.get("message") or "查询完成。"),
                    parameters=parameters,
                    available_tools=list(tools),
                    executed_tool=name,
                    execution_result=result,
                    tool_trace=trace,
                )
            if (self._settings.skill_routing_mode == "rules" or not self._settings.llm_api_key) and name not in {
                "customer_search", "region_search", "industry_search",
            }:
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
        deterministic_query = self._explicit_read_query(skill.name, message, parameters)
        if deterministic_query:
            return deterministic_query
        if self._settings.skill_routing_mode == "rules" or not self._settings.llm_api_key or not self._settings.llm_model:
            return self._rule_decision(skill.name, message, parameters)
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
            and skill.name in {"customer-gas-forecast", "model-training-management"}
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
                      "本轮出现新的业务实体时，用工具定义中的实体字段表达，并覆盖会话中的旧实体；"
                      "不要把同一实体的编码和名称混合成一个字段，也不要假设编码格式。"
                      "目标完成时直接回答。直接回复时必须只输出 JSON："
                      '{"status":"COMPLETED|NEED_INFORMATION|NEED_CLARIFICATION|NOT_FOUND|FAILED","message":"给用户的话"}。'
                      "不得重复写操作，不得持续轮询异步任务。"
                ),
            },
            *history[-8:],
            {
                "role": "user",
                "content": json.dumps(
                    {"message": message, "knownParameters": parameters, "toolTrace": trace},
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
    def _explicit_read_query(skill_name: str, message: str, parameters: dict[str, Any]) -> dict[str, Any] | None:
        text = re.sub(r"\s+", "", message).lower()
        if skill_name == "model-training-management":
            result_terms = ("训练信息", "训练结果", "训练指标", "训练效果", "训练详情", "所用的训练", "使用的训练")
            if any(term in text for term in result_terms) and parameters.get("trainBatchNo"):
                return {"type": "tool", "name": "training_result_get", "arguments": parameters}
            status_terms = ("训练进度", "训练状态", "训练到哪", "训练好了吗", "训练完成了吗", "训练结束了吗")
            if any(term in text for term in status_terms) and parameters.get("trainBatchNo"):
                return {"type": "tool", "name": "training_status_get", "arguments": parameters}
        return None

    @staticmethod
    def _rule_decision(skill_name: str, message: str, parameters: dict[str, Any]) -> dict[str, Any]:
        text = re.sub(r"\s+", "", message).lower()
        if parameters.get("_pendingTool") and parameters.get("confirmed") is True:
            return {"type": "tool", "name": parameters["_pendingTool"], "arguments": parameters}
        if skill_name == "customer-gas-forecast":
            if parameters.get("customerName") and not parameters.get("customerCode"):
                return {"type": "tool", "name": "customer_search", "arguments": parameters}
            if "最新" in text:
                name = "forecast_latest_get"
            elif "历史" in text:
                name = "forecast_history_get"
            elif any(word in text for word in ("进度", "状态", "到哪")):
                name = "task_status_get" if parameters.get("taskNo") and not parameters.get("forecastBatchNo") else "forecast_status_get"
            elif "结果" in text:
                name = "forecast_result_get"
            elif parameters.get("forecastBatchNo"):
                name = "forecast_result_get"
            else:
                name = "forecast_start"
        elif skill_name == "model-training-management":
            if any(word in text for word in ("恢复", "继续")):
                name = "task_resume"
            elif "重试" in text:
                name = "task_retry"
            elif "历史" in text:
                name = "training_history_get"
            elif "最新" in text or "训练过吗" in text:
                name = "training_latest_get"
            elif any(word in text for word in ("进度", "状态", "到哪", "好了吗", "完成了吗", "结束了吗")):
                name = "training_status_get"
            elif any(word in text for word in ("结果", "指标", "效果", "信息", "详情", "所用的训练", "使用的训练")):
                name = "training_result_get"
            else:
                name = "training_start"
        else:
            if not any(word in text for word in ("查询", "查看", "哪些", "列表", "状态", "步骤", "客户", "行业", "地区")):
                return {"type": "message", "message": "你好，我可以帮你查询业务数据、训练模型或进行用气预测。"}
            if "客户" in text:
                if parameters.get("regionName") and not parameters.get("regionCode"):
                    return {"type": "tool", "name": "region_search", "arguments": parameters}
                if parameters.get("industryName") and not parameters.get("industryCode"):
                    return {"type": "tool", "name": "industry_search", "arguments": parameters}
                name = "customer_search" if any(word in text for word in ("详情", "单个客户")) else "customer_list"
            elif "任务" in text and "步骤" in text:
                name = "task_steps_get"
            elif "任务" in text and any(word in text for word in ("状态", "进度")):
                name = "task_status_get"
            elif "地区" in text:
                name = "region_list"
            elif "行业" in text and "客户" not in text:
                name = "industry_list"
            else:
                name = "customer_list"
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
        normalized_message = re.sub(r"\s+", "", message).lower()
        explicit_confirmation = normalized_message in {"确认", "确定", "继续", "是", "好的", "可以", "同意"}
        keys = (
            "customerCode", "customerName", "regionCode", "regionName", "industryCode", "industryName",
            "taskNo", "trainBatchNo", "forecastBatchNo", "forecastHorizon",
        )
        values = {key: memory[key] for key in keys if memory.get(key) not in (None, "")}
        remembered = memory.get("currentParameters")
        if isinstance(remembered, dict):
            values.update({
                key: value for key, value in remembered.items()
                if value not in (None, "") and key != "confirmed"
            })
        values.update({key: value for key, value in metadata.items() if value not in (None, "") and key != "confirmed"})
        values["conversationNo"] = conversation_no
        locked_parameters = set(values.get("_lockedParameters") or [])
        pending = memory.get("pendingAction")
        has_pending_action = isinstance(pending, dict) and bool(pending.get("tool"))
        if explicit_confirmation and has_pending_action and isinstance(pending.get("parameters"), dict):
            snapshot = {
                key: value for key, value in pending["parameters"].items()
                if value not in (None, "") and key != "confirmed" and not key.startswith("_")
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
        horizon = re.search(r"(?:未来|预测)\s*(\d{1,3})\s*(?:天|日)", message)
        if horizon and skill_name == "customer-gas-forecast":
            values["forecastHorizon"] = int(horizon.group(1))
            locked_parameters.add("forecastHorizon")
        # 从“客户编码 ABC123”或“客户ABC123”提取显式编码，不依赖任何具体编码前缀。
        customer_code = re.search(r"客户(?:编码)?\s*[：:]?\s*([A-Za-z][A-Za-z0-9_-]{2,63})", message, re.I)
        if customer_code:
            values["customerCode"] = customer_code.group(1)
            values.pop("customerName", None)
            locked_parameters.add("customerCode")
        customer_text = re.sub(
            r"^(?:请|麻烦)?(?:帮我|帮|给我|给|我要|我想|查询|查看|查一下|查下)?"
            r"(?:让模型)?(?:重新训练|重训|训练|重新建模|建模|重新学习|学习|开始跑一下|跑一下)?",
            "",
            message.strip(),
        )
        customer_text = re.sub(r"^只用最近\d+(?:天|日|期|个月|月|旬)数据(?:来)?训练", "", customer_text)
        customer_text = re.sub(r"^使用\d{4}-\d{2}-\d{2}到\d{4}-\d{2}-\d{2}的数据(?:来)?训练", "", customer_text)
        customer = re.search(
            r"([\u4e00-\u9fa5A-Za-z0-9（）()·]{2,30}?(?:天然气|燃气|集团|公司))"
            r"(?=的|模型|未来|最新|历史|预测|重新训练|训练|需要|，|,|。|\s|$)",
            customer_text,
        )
        if customer:
            values["customerName"] = customer.group(1)
            values.pop("customerCode", None)
        # 规则模式只抽取用户说出的名称，不维护省份清单，也不在 Agent 中翻译主数据编码。
        # 标准名称、别名和编码统一由 Java 的 region_search / industry_search 解析。
        region_name = SkillAgent._extract_region_name(message)
        if region_name:
            values["regionName"] = region_name
            values.pop("regionCode", None)
            # 这是从本轮原文中按明确边界抽出的名称。模型可以补充缺失参数，
            # 但不能把“江苏”扩写成“江苏城燃”等无法匹配主数据的内容。
            locked_parameters.add("regionName")
        if any(word in message for word in ("城市燃气", "城镇燃气", "城燃")):
            values["industryName"] = next(
                word for word in ("城市燃气", "城镇燃气", "城燃") if word in message
            )
            values.pop("industryCode", None)
            locked_parameters.add("industryName")
        elif "化工" in message:
            values["industryName"] = "化工"
            values.pop("industryCode", None)
            locked_parameters.add("industryName")
        recent = re.search(r"最近\s*(\d{1,5})\s*(?:天|日|期|个月|月|旬)", message)
        if recent and skill_name == "model-training-management":
            values["trainMode"] = "RECENT"
            values["trainingPeriods"] = int(recent.group(1))
        dates = re.search(r"(\d{4}-\d{2}-\d{2})\s*(?:到|至|~|～)\s*(\d{4}-\d{2}-\d{2})", message)
        if dates and skill_name == "model-training-management":
            values["trainMode"] = "RANGE"
            values["trainingStartDate"] = dates.group(1)
            values["trainingEndDate"] = dates.group(2)
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
        if semantics is not None:
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
            values.update(accepted_semantic_values)
            locked_parameters.update(accepted_semantic_values)
            if semantics.needs_clarification:
                values["_clarificationQuestion"] = semantics.clarification_question or (
                    "请确认训练策略：是无条件创建新的训练批次，还是仅在没有可用模型时训练？"
                )
            if semantics.evidence:
                values["_semanticEvidence"] = semantics.evidence
        if skill_name == "model-training-management" and not values.get("customerName") and any(
            values.get(key) for key in ("regionCode", "regionName", "industryCode", "industryName")
        ):
            values["bulk"] = True
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
                    # 明确进入单客户 Schema 分支；批量预测只允许由用户范围表达触发。
                    values.setdefault("bulk", False)
        if "全部" in message or "所有" in message:
            values["fetchAll"] = True
        if locked_parameters:
            values["_lockedParameters"] = sorted(locked_parameters)
        return values

    @staticmethod
    def _extract_region_name(message: str) -> str | None:
        """抽取地区原名；标准化与真实性校验交给 Java 主数据工具。"""
        text = re.sub(r"\s+", "", message)
        text = re.sub(
            r"^(?:请|麻烦)?(?:帮我|帮|给我|查询|查看|查找|检索|统计|列出|获取)*(?:一下|下)?",
            "",
            text,
        )
        with_suffix = re.match(
            r"(?P<name>[\u4e00-\u9fa5]{2,12}?)(?:特别行政区|壮族自治区|回族自治区|维吾尔自治区|自治区|省|市|地区)",
            text,
        )
        if with_suffix:
            return with_suffix.group("name")
        # 没有行政区后缀时，只抽取紧邻明确行业表达的范围，避免把普通句子误识别为地区。
        before_industry = re.match(
            r"(?P<name>[\u4e00-\u9fa5]{2,12}?)(?=城市燃气|城镇燃气|城燃|化工)",
            text,
        )
        return before_industry.group("name") if before_industry else None

    @staticmethod
    def _apply_locked_parameters(parameters: dict[str, Any], arguments: dict[str, Any]) -> None:
        """本轮用户明确给出的关键参数优先于模型生成的工具参数。"""
        for key in parameters.get("_lockedParameters") or []:
            if key in parameters:
                arguments[key] = parameters[key]

    @staticmethod
    def _confirmation_message(tool: dict[str, Any], arguments: dict[str, Any]) -> str | None:
        if arguments.get("confirmed") is True:
            return None
        schema = tool.get("inputSchema") if isinstance(tool.get("inputSchema"), dict) else {}
        properties, required = SkillAgent._schema_fields(schema)
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
            return f"即将执行“{tool.get('title') or tool.get('name')}”，该操作会写入业务数据或改变任务状态，是否确认继续？"
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
        properties, _ = SkillAgent._schema_fields(schema)
        if not properties:
            return values
        return {key: value for key, value in values.items() if key in properties}

    @staticmethod
    def _missing_required_arguments(tool: dict[str, Any], arguments: dict[str, Any]) -> list[str]:
        schema = tool.get("inputSchema") if isinstance(tool.get("inputSchema"), dict) else {}
        return SkillAgent._missing_for_schema(schema, arguments)

    @staticmethod
    def _missing_for_schema(schema: dict[str, Any], arguments: dict[str, Any]) -> list[str]:
        missing = [name for name in schema.get("required") or [] if arguments.get(name) in (None, "", [])]
        for child in schema.get("allOf") or []:
            if not isinstance(child, dict):
                continue
            condition = child.get("if")
            if not isinstance(condition, dict) or SkillAgent._schema_condition_matches(condition, arguments):
                target = child.get("then") if isinstance(child.get("then"), dict) else child
                missing.extend(SkillAgent._missing_for_schema(target, arguments))
        any_of = schema.get("anyOf") or []
        if any_of and not any(
            isinstance(branch, dict) and not SkillAgent._missing_for_schema(branch, arguments)
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
        schema = tool.get("inputSchema") if isinstance(tool.get("inputSchema"), dict) else {}
        properties, _ = SkillAgent._schema_fields(schema)
        labels = []
        for name in missing:
            if " 或 " in name or "/" in name:
                alternatives = re.split(r"\s+或\s+|/", name)
                labels.append("或".join(SkillAgent._parameter_label(item, properties) for item in alternatives))
                continue
            labels.append(SkillAgent._parameter_label(name, properties))
        known = [key for key, value in arguments.items() if value not in (None, "", []) and not key.startswith("_")]
        suffix = f" 已识别参数：{', '.join(known)}。" if known else ""
        return f"还需要提供：{'、'.join(labels)}。{suffix}"

    @staticmethod
    def _parameter_label(name: str, properties: dict[str, Any]) -> str:
        rule = properties.get(name)
        description = rule.get("description") if isinstance(rule, dict) else None
        return f"{name}（{description}）" if description else name

    @staticmethod
    def _argument_validation_error(tool: dict[str, Any], arguments: dict[str, Any]) -> str | None:
        schema = tool.get("inputSchema") if isinstance(tool.get("inputSchema"), dict) else {}
        properties, _ = SkillAgent._schema_fields(schema)
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
        return None

    @staticmethod
    def _message_decision(content: str | None) -> dict[str, Any]:
        message = (content or "请补充必要信息。").strip()
        try:
            parsed = json.loads(message)
            allowed = {"COMPLETED", "NEED_INFORMATION", "NEED_CLARIFICATION", "NOT_FOUND", "FAILED"}
            if isinstance(parsed, dict) and parsed.get("status") in allowed and str(parsed.get("message") or "").strip():
                return {"type": "message", "status": parsed["status"], "message": str(parsed["message"]).strip()}
        except (json.JSONDecodeError, TypeError):
            pass
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
                child_properties, child_required = SkillAgent._schema_fields(child)
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
        if name in {"training_result_get", "training_latest_get"}:
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
        for key in ("taskNo", "trainBatchNo", "forecastBatchNo"):
            if data.get(key):
                parameters[key] = data[key]
