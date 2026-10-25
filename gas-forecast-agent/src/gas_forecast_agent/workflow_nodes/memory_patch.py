"""根据本轮计划和执行结果生成记忆补丁；数据库仍由 Java 统一保存。"""

from gas_forecast_agent.operation_models import OperationParameters
from gas_forecast_agent.schemas import MemoryPatch, PendingOperation
from gas_forecast_agent.workflow_nodes.interactions import _interaction_details
from gas_forecast_agent.workflow_nodes.state import AgentGraphState


def memory_patch_for(state: AgentGraphState) -> MemoryPatch:
    """等待时整体替换快照；完成或拒绝时清除，避免新任务继承旧参数。"""
    result = state.require_result()
    understanding = state.require_understanding()
    interaction = _interaction_details(state.request, result, understanding.semantics)
    pending = PendingOperation(
        interaction=interaction,
        semantics=understanding.semantics,
        parameters=result.parameters.pending_snapshot(),
        pending_tool=result.pending_tool,
        issues=result.resolution_issues,
    ) if interaction is not None else None
    return MemoryPatch(pending_operation=pending)


def persistent_parameters(parameters: OperationParameters) -> dict[str, object]:
    """仅在审计接口边界输出业务参数，不返回单轮控制字段。"""
    return {
        key: value for key, value in parameters.to_payload().items()
        if not key.startswith("_") and key != "confirmed"
    }
