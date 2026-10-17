"""业务能力配置模型。"""

from enum import StrEnum
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, model_validator


class CapabilityStatus(StrEnum):
    """能力可用程度。"""

    AVAILABLE = "AVAILABLE"
    PLANNED = "PLANNED"
    NEEDS_CONFIRMATION = "NEEDS_CONFIRMATION"
    DISABLED = "DISABLED"


class IntentDefinition(BaseModel):
    """用于将结构化用户意图匹配到业务能力。"""

    model_config = ConfigDict(extra="forbid")

    domain: str
    action: str
    resource: str
    scenario: str | None = None


class ConfirmationRule(BaseModel):
    """需要人工确认的条件和提示。"""

    model_config = ConfigDict(extra="forbid")

    condition: str
    message: str


class CapabilityDefinition(BaseModel):
    """一项可路由、可执行或待建设的业务能力。"""

    model_config = ConfigDict(extra="forbid")

    code: str
    name: str
    description: str
    status: CapabilityStatus
    user_visible: bool = True
    read_only: bool
    workflow: str
    context_policy: str | None = None
    response_mode: str | None = None
    intent: IntentDefinition
    scopes: list[str] = Field(default_factory=list)
    required_parameters: list[str] = Field(default_factory=list)
    optional_parameters: list[str] = Field(default_factory=list)
    defaults: dict[str, Any] = Field(default_factory=dict)
    confirmations: list[ConfirmationRule] = Field(default_factory=list)
    tools: list[str] = Field(default_factory=list)
    missing_tools: list[str] = Field(default_factory=list)
    notes: list[str] = Field(default_factory=list)

    @model_validator(mode="after")
    def validate_availability(self) -> "CapabilityDefinition":
        if self.status == CapabilityStatus.AVAILABLE and self.missing_tools:
            raise ValueError("AVAILABLE能力不能声明missing_tools")
        if not self.tools and not self.missing_tools and self.response_mode != "LLM":
            raise ValueError("能力至少需要一个已实现或待开发工具")
        return self


class CapabilityFile(BaseModel):
    """单个场景能力配置文件。"""

    model_config = ConfigDict(extra="forbid")

    schema_version: int = Field(alias="schemaVersion")
    scenario: str
    scenario_name: str = Field(alias="scenarioName")
    description: str
    capabilities: list[CapabilityDefinition]
