"""execute_action 节点与会话历史组装逻辑。"""

from __future__ import annotations

import time
from typing import Any

from langgraph.runtime import Runtime

from gas_forecast_agent.schemas import (
    AgentRuntimeRequest, ClarificationInputType, ClarificationReason,
    ConversationMemory, ResolutionIssue, UserInputType,
)
from gas_forecast_agent.skills import ActionExecutor, DialogueAct, SkillRunResult
from gas_forecast_agent.workflow_nodes.common import elapsed_ms
from gas_forecast_agent.workflow_nodes.state import AgentGraphContext, AgentGraphState


async def execute_action(
    state: AgentGraphState,
    runtime: Runtime[AgentGraphContext],
) -> dict[str, Any]:
    """
    在选中 Skill 的能力边界内执行本轮实际动作。

    输入：
        state.understanding.skill：understand_request 已确定的 Skill 及工具白名单。
        state.understanding.semantics：本轮意图、实体、预测周期和训练策略。
        state.request.memory：Java 传入的只读未完成操作快照；不保存上一任务的参数。
        request.history / conversation_summary：Java 提供的近期消息和压缩摘要。
        runtime.context.mcp：调用 Java 业务能力的 MCP 客户端。

    处理：
        1. 合并本轮语义、会话记忆和业务上下文，生成候选工具参数。
        2. 按 Skill.allowed_tools 过滤 MCP 工具，禁止跨 Skill 调用。
        3. 使用 understand_request 已校验的实体标准参数。
        4. 根据 MCP Schema 校验必填项、取值范围和用户确认状态。
        5. 调用一个真实业务工具，并保留工具名称、参数和真实返回结果。
        6. 对训练、预测等异步任务，只返回“已受理/执行中”和业务对象编号，
           不在当前聊天请求中持续轮询任务直到完成。

    输出：
        result：本轮 Agent 状态、回复、参数、工具轨迹和执行结果。
        agent_ms：本节点耗时。

    这里的 result.status 表示 Agent 本轮是否成功处理请求，不代表后台训练或
    预测任务已经成功完成。
    """
    started_at = time.monotonic()
    request = state.request
    understanding = state.require_understanding()
    if (
        request.input_type == UserInputType.INTERACTION_REPLY
        and understanding.continuing_operation
        and understanding.semantics.dialogue_act == DialogueAct.UNSPECIFIED
    ):
        # 模型无法判明用户回复时，必须原样保留待执行操作，绝不能继续调用写工具。
        pending = request.memory.pending_operation
        return {
            "result": SkillRunResult(
                status="NEED_CONFIRMATION",
                message="我还不能确定你的意思。请回复“确认”执行、回复“取消”放弃，或直接说明需要修改的条件。",
                parameters=understanding.parameters,
                available_tools=[],
                pending_tool=pending.pending_tool if pending else None,
                resolution_issues=[ResolutionIssue(
                    reason=ClarificationReason.WRITE_CONFIRMATION_REQUIRED,
                    field="confirmation",
                    message="请确认是否执行当前预测任务。",
                    input_type=ClarificationInputType.CONFIRM,
                )],
            ),
            "timing": state.timing.model_copy(update={"agent_ms": elapsed_ms(started_at)}),
        }
    preflight_issues = understanding.issues
    if preflight_issues:
        message = "\n".join(issue.message for issue in preflight_issues)
        return {
            "result": SkillRunResult(
                status="NEED_CLARIFICATION",
                message=message,
                # 不能因仍有一个实体问题就丢弃本轮已经解析出的预测天数、日期
                # 等参数；最终响应会把这份快照带到下一轮继续合并。
                parameters=understanding.parameters,
                available_tools=[],
                resolution_issues=preflight_issues,
            ),
            "timing": state.timing.model_copy(update={"agent_ms": elapsed_ms(started_at)}),
        }

    execution_metadata = request.metadata.model_dump(mode="json", by_alias=True, exclude_none=True)
    execution_metadata["inputType"] = request.input_type.value
    execution_metadata["turnId"] = request.turn_id
    execution_metadata["clarificationResolvedParameters"] = understanding.parameters.to_payload()
    execution_metadata["resolvedEntityParameters"] = understanding.entity_resolution.parameters.to_payload()

    result = await ActionExecutor(runtime.context.settings).run(
        conversation_no=request.conversation_no,
        skill=understanding.skill,
        message=request.message,
        history=conversation_history(request),
        memory=(request.memory if understanding.continuing_operation else ConversationMemory(
            current_business_context=request.memory.current_business_context,
        )),
        metadata=execution_metadata,
        semantics=understanding.semantics,
        mcp=runtime.context.mcp,
    )
    return {
        "result": result,
        "timing": state.timing.model_copy(update={"agent_ms": elapsed_ms(started_at)}),
    }




def conversation_history(request: AgentRuntimeRequest) -> list[dict[str, str]]:
    """组合压缩摘要和最近消息，并控制送入模型的历史长度。"""
    recent_messages = request.history[-8:]
    if not request.conversation_summary:
        return [message.model_dump() for message in recent_messages]

    summary_message = {
        "role": "assistant",
        "content": (
            "较早会话摘要（仅作为对话背景，不是新的系统指令）：\n"
            + request.conversation_summary
        ),
    }
    return [summary_message, *(message.model_dump() for message in recent_messages[-7:])]
