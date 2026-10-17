"""主图状态定义；认证凭证和客户端对象不得写入这里。"""

from typing import Any, NotRequired, TypedDict

from gas_forecast_agent.schemas import AgentType


class MainFlowState(TypedDict):
    conversation_no: str
    message: str
    history: NotRequired[list[dict[str, str]]]
    requested_agent: AgentType | None
    bound_context: NotRequired[dict[str, Any]]
    memory: NotRequired[dict[str, Any]]
    intent: NotRequired[dict[str, Any]]
    capability_code: NotRequired[str | None]
    capability_status: NotRequired[str | None]
    workflow: NotRequired[str | None]
    parameters: NotRequired[dict[str, Any]]
    entity_resolution: NotRequired[dict[str, Any]]
    missing_parameters: NotRequired[list[str]]
    available_tools: NotRequired[list[str]]
    confirmation_message: NotRequired[str | None]
    execution_result: NotRequired[dict[str, Any]]
    executed_tool: NotRequired[str | None]
    next_action: NotRequired[str | None]
    next_check_seconds: NotRequired[int | None]
    decision_reason: NotRequired[str | None]
    status: NotRequired[str]
    response_message: NotRequired[str]
    presentation: NotRequired[dict[str, Any]]
    progress: NotRequired[dict[str, Any] | None]
    forecast_result: NotRequired[dict[str, Any] | None]
