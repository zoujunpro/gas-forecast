"""使用 LangGraph 编排一次 Agent 会话处理。

节点实现位于 :mod:`gas_forecast_agent.workflow_nodes`，本模块只负责图装配与运行入口。
"""

from __future__ import annotations

import time

from langgraph.graph import END, START, StateGraph

from gas_forecast_agent.config import Settings
from gas_forecast_agent.internal_client import InternalAgentClient
from gas_forecast_agent.mcp import StreamableHttpMcpClient
from gas_forecast_agent.schemas import AgentRuntimeRequest, AgentRuntimeResponse
from gas_forecast_agent.skills import RequestSemanticExtractor, SkillRegistry
from gas_forecast_agent.workflow_nodes import (
    build_response,
    execute_action,
    understand_request,
)
from gas_forecast_agent.workflow_nodes.build_response import (
    resolve_agent_type,
    training_result_summary,
)
from gas_forecast_agent.workflow_nodes.common import elapsed_ms
from gas_forecast_agent.workflow_nodes.execute_action import conversation_history
from gas_forecast_agent.workflow_nodes.interactions import (
    WAITING_RESULT_STATUSES,
    _interaction_details,
)
from gas_forecast_agent.workflow_nodes.memory_patch import (
    persistent_parameters,
)
from gas_forecast_agent.workflow_nodes.state import AgentGraphContext, AgentGraphState, AgentTiming


def build_agent_graph():
    """
    声明并编译一次 Agent Run 的固定主流程。

    节点按顺序执行，但共享同一个 AgentGraphState。每个节点只返回自己新增或
    更新的字段，LangGraph 会自动把它们合并，因此进入后续节点时前面节点的
    request、understanding 和 result 都仍然存在。会话记忆只从 request.memory 读取。
    """
    builder = StateGraph(AgentGraphState, context_schema=AgentGraphContext)

    # 1. request 已携带 Java 传入的只读 memory_json 快照；本节点一次性理解用户原话，输出稳定意图、对话动作、
    #    客户/地区/行业、预测周期、训练策略和澄清要求等结构化语义。
    builder.add_node("understand_request", understand_request)

    # 2. understand_request 已经确定 Skill；执行器按 Skill 分发业务处理并校验参数，
    #    调用真实 MCP 工具并形成 Agent 本轮执行结果。
    builder.add_node("execute_action", execute_action)

    # 3. 将语义、Skill、工具结果、展示数据和记忆补丁组装为 Python -> Java 内部响应。
    builder.add_node("build_response", build_response)

    # 当前版本是清晰的线性主流程；后续若增加“等待澄清”“人工确认”等分支，
    # 可以在对应节点后增加条件边，而不需要把多种职责塞进单个节点。
    builder.add_edge(START, "understand_request")
    builder.add_edge("understand_request", "execute_action")
    builder.add_edge("execute_action", "build_response")
    builder.add_edge("build_response", END)
    return builder.compile(name="gas-forecast-agent")


agent_graph = build_agent_graph()


async def run_agent_graph(
    request: AgentRuntimeRequest,
    client: StreamableHttpMcpClient,
    internal_client: InternalAgentClient,
    settings: Settings,
    skills: SkillRegistry,
) -> AgentRuntimeResponse:
    """执行一次 LangGraph，并返回 Java 接口需要的内部结果。"""
    initial_state = AgentGraphState(
        request=request,
        timing=AgentTiming(started_at=time.monotonic()),
    )
    context = AgentGraphContext(
        settings=settings,
        mcp=client,
        internal_client=internal_client,
        skills=skills,
    )
    config = {
        "run_name": "gas-forecast-agent",
        "tags": ["agent-chat"],
        "metadata": {
            "conversation_no": request.conversation_no,
            "turn_id": request.turn_id,
        },
        "recursion_limit": 20,
    }

    final_state = await agent_graph.ainvoke(
        initial_state,
        config=config,
        context=context,
    )
    response = AgentGraphState.model_validate(final_state).response
    if not isinstance(response, AgentRuntimeResponse):
        raise RuntimeError("LangGraph 未生成 Agent 响应")
    return response
