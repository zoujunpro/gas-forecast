"""build_response 节点与响应展示辅助逻辑。"""

from __future__ import annotations

import logging
from typing import Any

from gas_forecast_agent.presentation import PresentationPlanner
from gas_forecast_agent.schemas import (
    AgentRuntimeRequest,
    AgentRuntimeResponse,
    AgentType,
    PresentationResponse,
    SkillSelectionResponse,
)
from gas_forecast_agent.skills import SkillDefinition
from gas_forecast_agent.workflow_nodes.common import elapsed_ms
from gas_forecast_agent.workflow_nodes.interactions import _interaction_details
from gas_forecast_agent.workflow_nodes.memory_patch import memory_patch_for
from gas_forecast_agent.workflow_nodes.state import AgentGraphState


logger = logging.getLogger(__name__)


def build_response(state: AgentGraphState) -> dict[str, Any]:
    """
    把图内各节点结果组装成返回给 Java 的内部 Agent 响应。

    输入：
        request：会话编号和用户本轮请求。
        semantics：结构化意图和实体解析结果。
        selection / skill：Skill 路由结果。
        result：公共执行器产生的回复、参数和真实工具结果。

    处理：
        1. 根据工具结果生成训练卡片、预测卡片或进度等展示数据。
        2. 返回 intentCode、Skill 编码、路由原因和本轮 Agent 状态。
        3. 保留已执行工具及其结果，供 main.py 提取任务、训练和预测引用。
        4. 记录语义识别、路由、动作执行及总耗时日志。

    输出：
        response：AgentRuntimeResponse，由 run_agent_graph 返回给 Java。

    本节点只进行内存中的响应转换，不调用模型、不调用 MCP，也不写数据库。
    """
    request = state.request
    result = state.require_result()
    understanding = state.require_understanding()
    selection = understanding.selection
    skill = understanding.skill

    presentation = PresentationPlanner.plan(
        result.executed_tool,
        result.execution_result.to_payload() if result.execution_result else {},
    )
    training_result = presentation.get("training_result")
    interaction = _interaction_details(request, result, understanding.semantics)
    response = AgentRuntimeResponse(
        conversation_no=request.conversation_no,
        agent_type=resolve_agent_type(request, skill),
        intent_code=(
            understanding.semantics.intent.value
            if understanding.semantics.intent is not None
            else None
        ),
        status=result.status,
        message=(
            training_result_summary(training_result)
            if training_result
            else result.message
        ),
        skill_name=skill.name,
        skill_selection=SkillSelectionResponse(
            skill_name=skill.name, confidence=selection.confidence, reason=selection.reason),
        parameters=result.parameters,
        entity_resolution=understanding.semantics,
        missing_parameters=[issue.field for issue in result.resolution_issues if issue.required],
        resolution_issues=result.resolution_issues,
        interaction_id=interaction.interaction_id if interaction else None,
        interaction_type=(
            interaction.interaction_type
            if interaction
            else None
        ),
        reply_to_turn_id=interaction.source_turn_id if interaction else None,
        request_id=interaction.request_id if interaction else None,
        clarification_reason=(
            interaction.reason
            if interaction
            else None
        ),
        resume_from=interaction.resume_from if interaction else None,
        memory_loaded=True,
        available_tools=result.available_tools,
        executed_tool=result.executed_tool,
        execution_result=result.execution_result,
        presentation=PresentationResponse.model_validate(presentation["presentation"]),
        decision_reason=selection.reason,
        memory_patch=memory_patch_for(state),
    )

    logger.info(
        "event=agent_graph_completed conversation_no=%s skill=%s tool=%s "
        "semantics_ms=%s routing_ms=%s agent_ms=%s total_ms=%s",
        request.conversation_no,
        skill.name,
        result.executed_tool or "-",
        state.timing.semantics_ms,
        state.timing.routing_ms,
        state.timing.agent_ms,
        elapsed_ms(state.timing.started_at),
    )
    return {"response": response}




def resolve_agent_type(
    request: AgentRuntimeRequest,
    skill: SkillDefinition,
) -> AgentType | None:
    """没有显式 Agent 类型时，根据预测业务语义补全。"""
    if request.agent_type is not None:
        return request.agent_type
    if skill.name != "customer-gas-forecast":
        return None
    if "冬季" in request.message or "保供" in request.message:
        return AgentType.WINTER_SUPPLY
    if "月度" in request.message or "销量" in request.message:
        return AgentType.MONTHLY_SALES
    return AgentType.SHORT_TERM


def training_result_summary(training: dict[str, Any]) -> str:
    """将训练结果整理为聊天页面可直接显示的摘要。"""
    lines = [
        f"训练批次：{training.get('batchNo') or '-'}",
        f"状态：{training.get('status') or '-'}",
    ]
    scope = " / ".join(
        str(value)
        for value in (
            training.get("regionName"),
            training.get("industryName"),
            training.get("customerName"),
        )
        if value
    )
    if scope:
        lines.append(f"训练范围：{scope}")
    if training.get("trainStartDate") or training.get("trainEndDate"):
        lines.append(
            f"训练区间：{training.get('trainStartDate') or '-'} "
            f"至 {training.get('trainEndDate') or '-'}"
        )
    if training.get("bestModel"):
        model = str(training["bestModel"])
        if training.get("modelVersion"):
            model += f"（{training['modelVersion']}）"
        lines.append(f"最优模型：{model}")

    metrics = []
    metric_names = (
        ("mape", "MAPE"),
        ("wmape", "WMAPE"),
        ("smape", "SMAPE"),
        ("mae", "MAE"),
        ("rmse", "RMSE"),
        ("r2", "R²"),
    )
    for key, label in metric_names:
        if training.get(key) is not None:
            metrics.append(f"{label}={training[key]}")
    if metrics:
        lines.append("评估指标：" + "，".join(metrics))

    points = training.get("backtestPoints")
    if isinstance(points, list) and points:
        lines.append(f"回测明细：{len(points)} 个数据点")
    else:
        lines.append("回测明细：暂无")
    return "\n".join(lines)
