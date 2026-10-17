"""模型意图识别的稳定输出结构。"""

from enum import StrEnum
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, field_validator


class IntentMatchStatus(StrEnum):
    MATCHED = "MATCHED"
    AMBIGUOUS = "AMBIGUOUS"
    UNKNOWN = "UNKNOWN"
    CANCELLED = "CANCELLED"


class SpeechAct(StrEnum):
    QUESTION = "QUESTION"
    COMMAND = "COMMAND"
    CONFIRM = "CONFIRM"
    CANCEL = "CANCEL"
    FOLLOW_UP = "FOLLOW_UP"
    UNKNOWN = "UNKNOWN"


class RawEntity(BaseModel):
    """从用户原话中抽取、尚未通过业务数据校验的实体。"""

    model_config = ConfigDict(extra="forbid")

    type: str
    raw_text: str
    purpose: str | None = None


class RecognizedIntent(BaseModel):
    """只描述用户想做什么，不直接决定调用哪个 MCP 工具。"""

    model_config = ConfigDict(extra="forbid")

    status: IntentMatchStatus = IntentMatchStatus.MATCHED
    capability_code: str | None = None
    domain: str | None = Field(default=None, description="业务域，例如MASTER_DATA、TRAINING、FORECAST或TASK")
    action: str | None = Field(default=None, description="动作，例如LIST、DETAIL、START、STATUS、RESULT、LATEST、HISTORY、RESUME或RETRY")
    resource: str | None = Field(default=None, description="能力配置中定义的资源编码")
    scenario: str | None = Field(default=None, description="SHORT_TERM、MONTHLY_SALES、WINTER_SUPPLY或空")
    parameters: dict[str, Any] = Field(default_factory=dict, description="从用户输入和记忆中抽取的业务参数")
    parameter_confidence: dict[str, float] = Field(default_factory=dict, description="关键参数的独立置信度，值域0到1")
    raw_entities: list[RawEntity] = Field(default_factory=list)
    confidence: float = Field(ge=0, le=1, description="意图识别置信度")
    explanation: str = Field(description="简短说明识别依据，不包含思维链")
    speech_act: SpeechAct = SpeechAct.UNKNOWN
    candidate_capabilities: list[str] = Field(default_factory=list)
    clarification_question: str | None = None

    @field_validator("raw_entities", mode="before")
    @classmethod
    def normalize_empty_raw_entities(cls, value: Any) -> Any:
        """兼容模型把“无实体”输出成 null 或空对象；非空对象仍交给 Pydantic 拒绝。"""
        if value is None or value == {}:
            return []
        return value

    @field_validator("parameter_confidence")
    @classmethod
    def validate_parameter_confidence(cls, value: dict[str, float]) -> dict[str, float]:
        if any(score < 0 or score > 1 for score in value.values()):
            raise ValueError("参数置信度必须在0到1之间")
        return value
