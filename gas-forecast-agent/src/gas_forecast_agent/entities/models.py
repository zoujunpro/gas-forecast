"""实体解析结果模型。"""

from enum import StrEnum
from typing import Any

from pydantic import BaseModel, Field


class EntityResolutionStatus(StrEnum):
    RESOLVED = "RESOLVED"
    NEED_INFORMATION = "NEED_INFORMATION"
    NEED_SELECTION = "NEED_SELECTION"
    NOT_FOUND = "NOT_FOUND"
    INVALID_TIME = "INVALID_TIME"


class EntityResolutionOutcome(BaseModel):
    status: EntityResolutionStatus
    parameters: dict[str, Any] = Field(default_factory=dict)
    resolved_entities: dict[str, dict[str, Any]] = Field(default_factory=dict)
    entity_type: str | None = None
    raw_text: str | None = None
    candidates: list[dict[str, Any]] = Field(default_factory=list)
    message: str | None = None
