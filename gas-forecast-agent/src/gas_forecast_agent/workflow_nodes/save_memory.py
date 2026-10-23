"""save_memory 节点与跨轮记忆字段筛选逻辑。"""

from __future__ import annotations

from typing import Any

from gas_forecast_agent.skills import SkillIntent
from gas_forecast_agent.workflow_nodes.interactions import _interaction_details
from gas_forecast_agent.workflow_nodes.state import AgentGraphState


def save_memory(state: AgentGraphState) -> dict[str, Any]:
    """
    计算下一轮仍然需要的结构化会话记忆补丁。

    输入：
        state["result"]：execute_action 产生的参数、工具结果和待确认动作。
        state["skill"]：本轮实际使用的 Skill。

    处理：
        1. 删除 confirmed、turnId 和下划线开头的单轮控制字段。
        2. 保存当前 Skill、可复用业务参数、最后一次工具结果。
        3. 如果本轮等待用户确认，保存待执行工具及其参数快照。
        4. 返回 set/remove 补丁，由 Java 在保存助手消息的同一事务中更新
           ai_conversation_tb.memory_json。

    输出：
        memory_patch：仅描述本轮需要新增、覆盖或删除的记忆字段；不写数据库。
    """
    request = state["request"]
    result = state["result"]
    parameters = persistent_parameters(result.parameters)
    pending_parameters = pending_action_parameters(result.parameters)
    semantics = state["semantics"]
    interaction = _interaction_details(request, result, semantics)
    pending_interaction = None
    if interaction is not None:
        pending_interaction = {
            **interaction,
            "originalMessage": request.message,
            "semantics": semantics.model_dump(mode="json", by_alias=True),
            "skill": state["skill"].name,
            "parameters": pending_parameters,
            "pendingTool": result.pending_tool,
            "issues": [
                issue.model_dump(mode="json", by_alias=True)
                for issue in result.resolution_issues
            ],
        }
    if semantics.intent == SkillIntent.CHAT:
        # 普通聊天和未知请求会结束旧的待处理交互，但不覆盖最后一个业务 Skill、
        # 参数、工具结果或业务焦点，避免一句“你好”抹掉后续可引用的预测上下文。
        values: dict[str, Any] = {}
        remove = ["pendingInteraction", "pendingAction"]
    else:
        values = {
            "currentSkill": state["skill"].name,
            "currentParameters": parameters,
            "lastTool": result.executed_tool,
            "lastExecution": result.execution_result,
        }
        remove: list[str] = []
        if pending_interaction is not None:
            values["pendingInteraction"] = pending_interaction
        else:
            remove.append("pendingInteraction")
        pending_action = (
                {
                    "tool": result.pending_tool,
                    "parameters": pending_parameters,
                    "semantics": semantics.model_dump(mode="json", by_alias=True),
                    "interactionId": interaction["interactionId"] if interaction else None,
                    "sourceTurnId": request.turn_id,
                }
                if result.status.upper() == "NEED_CONFIRMATION" and result.pending_tool
                else None
            )
        if pending_action is not None:
            values["pendingAction"] = pending_action
        else:
            remove.append("pendingAction")
    business_context = current_business_context(result.parameters)
    user_supplied_context = semantics.has_explicit_entities
    if business_context and (
        user_supplied_context
        or result.executed_tool is not None
        or result.parameters.get("confirmed") is True
    ):
        # 当前业务焦点是可变会话记忆，不是会话表的绑定维度。页面候选值只有在
        # 用户明确表达、确认，或业务工具实际使用后才升级为已确认焦点。
        values["currentBusinessContext"] = business_context

    return {"memory_patch": {"set": values, "remove": remove}}




def persistent_parameters(parameters: dict[str, Any]) -> dict[str, Any]:
    """保留跨轮操作参数；地区、行业和客户由currentBusinessContext单独保存。"""
    context_keys = {
        "regionCode", "regionName", "industryCode", "industryName",
        "customerCode", "customerName",
    }
    return {
        key: value
        for key, value in parameters.items()
        if not key.startswith("_")
        and key not in {"confirmed", "turnId"}
        and key not in context_keys
    }


def pending_action_parameters(parameters: dict[str, Any]) -> dict[str, Any]:
    """保存待用户确认动作的完整业务快照，确认后必须按该快照继续。"""
    resumable_controls = {
        "_entityBatchResolved",
        "_validatedEntityParameters",
    }
    return {
        key: value
        for key, value in parameters.items()
        if (not key.startswith("_") or key in resumable_controls)
        and key not in {"confirmed", "turnId"}
    }


def current_business_context(parameters: dict[str, Any]) -> dict[str, Any]:
    """从本轮最终参数中提取会话最近一次确认或实际使用的业务焦点。"""
    keys = (
        "regionCode", "regionName", "industryCode", "industryName",
        "customerCode", "customerName",
    )
    return {
        key: parameters[key]
        for key in keys
        if parameters.get(key) not in (None, "")
    }
