"""根据本轮计划和执行结果生成记忆补丁；数据库仍由 Java 统一保存。"""

from gas_forecast_agent.operation_models import OperationParameters
from gas_forecast_agent.schemas import MemoryPatch, PendingOperation
from gas_forecast_agent.workflow_nodes.state import AgentGraphState


def memory_patch_for(state: AgentGraphState) -> MemoryPatch:
    """等待时整体替换快照；完成或拒绝时清除，避免新任务继承旧参数。"""
    result = state.require_result()
    understanding = state.require_understanding()
    previous = state.request.memory.pending_operation
    intent = understanding.semantics.intent.value if understanding.semantics.intent else None
    pending = PendingOperation(
        original_message=previous.original_message if previous else state.request.message,
        action=result.pending_tool or ("forecast_start" if intent == "FORECAST" else None),
        arguments=result.parameters.pending_snapshot(),
        issues=result.resolution_issues,
    ) if result.resolution_issues else None
    return MemoryPatch(pending_operation=pending)


def persistent_parameters(parameters: OperationParameters) -> dict[str, object]:
    """仅在审计接口边界输出业务参数，不返回单轮控制字段。"""
    return {
        key: value for key, value in parameters.to_payload().items()
        if not key.startswith("_") and key != "confirmed"
    }
