"""会话接口使用的输入输出对象。"""

from enum import StrEnum
from typing import Any

from pydantic import AliasChoices, BaseModel, Field


class AgentType(StrEnum):
    SHORT_TERM = "short-term"
    MONTHLY_SALES = "monthly-sales"
    WINTER_SUPPLY = "winter-supply"


class ChatRequest(BaseModel):
    turn_id: str | None = Field(
        default=None, min_length=1, max_length=64, validation_alias=AliasChoices("turn_id", "turnId")
    )
    conversation_no: str | None = Field(
        default=None, max_length=64, validation_alias=AliasChoices("conversation_no", "conversationNo")
    )
    forecast_batch_no: str | None = Field(
        default=None, max_length=64, validation_alias=AliasChoices("forecast_batch_no", "forecastBatchNo")
    )
    message: str = Field(min_length=1, max_length=4000)
    agent_type: AgentType | None = Field(
        default=None, validation_alias=AliasChoices("agent_type", "agentType")
    )
    metadata: dict[str, Any] = Field(default_factory=dict)
    history: list[dict[str, str]] = Field(default_factory=list, max_length=12)


class ChatResponse(BaseModel):
    conversation_no: str
    agent_type: AgentType | None = None
    message: str
    status: str
    skill_name: str | None = None
    skill_selection: dict[str, Any] | None = None
    parameters: dict[str, Any] = Field(default_factory=dict)
    entity_resolution: dict[str, Any] | None = None
    missing_parameters: list[str] = Field(default_factory=list)
    memory_loaded: bool = False
    available_tools: list[str] = Field(default_factory=list)
    executed_tool: str | None = None
    execution_result: dict[str, Any] | None = None
    presentation: dict[str, Any] | None = None
    progress: dict[str, Any] | None = None
    forecast_result: dict[str, Any] | None = None
    training_result: dict[str, Any] | None = None
    next_action: str | None = None
    next_check_seconds: int | None = None
    decision_reason: str | None = None
