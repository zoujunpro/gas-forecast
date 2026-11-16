"""公共展示节点：将真实工具结果转换为受控展示配置。"""

from gas_forecast_agent.presentation import PresentationPlanner, build_content_blocks
from gas_forecast_agent.presentation_planner import plan_blocks
from langgraph.runtime import Runtime
from gas_forecast_agent.workflow_nodes.state import AgentGraphContext
from gas_forecast_agent.workflow_nodes.state import AgentGraphState


async def presentation_node(state: AgentGraphState, runtime: Runtime[AgentGraphContext]) -> dict:
    """规则构建真实数据，模型可选调整展示计划；不执行业务工具。"""
    result = state.require_result()
    planned = PresentationPlanner.plan(
        result.executed_tool,
        result.execution_result.to_payload() if result.execution_result else {},
        query=state.request.message,
    )
    blocks = build_content_blocks(
        result.message,
        result.execution_result.to_payload() if result.execution_result else {},
        planned,
        tool_name=result.executed_tool,
    )
    # 仅使用真实执行轨迹中的参数绑定分页，不接受模型生成的查询条件。
    trace = next((trace for trace in reversed(result.tool_trace) if trace.tool == result.executed_tool), None)
    if result.executed_tool == "customer_list" and trace is not None:
        args = trace.arguments.to_payload()
        filters = {key: args[key] for key in ("keyword", "regionCode", "industryCode") if key in args}
        for block in blocks:
            if block.type == "TABLE" and block.data.get("total", 0) > len(block.data.get("rows", [])):
                block.data["pagination"] = {"resource": "customers", "filters": filters}
    blocks = await plan_blocks(state.request.message, blocks, runtime.context.settings)
    return {
        "content_blocks": blocks,
    }
