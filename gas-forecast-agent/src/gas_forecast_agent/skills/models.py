"""运行时加载的轻量 Skill 能力定义。"""

from enum import StrEnum

from pydantic import BaseModel, ConfigDict, Field, model_validator


class SkillIntent(StrEnum):
    """系统支持的稳定顶层意图。"""

    TRAIN = "TRAIN"
    FORECAST = "FORECAST"
    QUERY = "QUERY"
    ANALYZE = "ANALYZE"
    CHAT = "CHAT"


class SkillDefinition(BaseModel):
    """Skill 只声明能力边界，不实现语义解析或业务流程。"""

    model_config = ConfigDict(extra="forbid")

    name: str = Field(description="Skill 唯一编码，必须与目录名称一致")
    intent: SkillIntent = Field(description="该 Skill 唯一负责的顶层业务意图")
    description: str = Field(description="提供给语义模型的简短能力说明")
    allowed_tools: list[str] = Field(
        alias="allowed-tools",
        description="该 Skill 允许调用的 MCP 工具白名单",
    )
    read_only: bool = Field(
        alias="read-only",
        description="是否禁止创建或修改业务对象",
    )
    instructions: str = Field(description="该能力的业务约束和结果处理说明")
    references: dict[str, str] = Field(default_factory=dict)

    @model_validator(mode="after")
    def validate_intent_boundary(self) -> "SkillDefinition":
        """确保配置本身不会把读写能力放进错误的意图边界。"""
        action_intents = {SkillIntent.TRAIN, SkillIntent.FORECAST}
        if self.intent in action_intents and self.read_only:
            raise ValueError(f"意图 {self.intent.value} 必须声明为非只读 Skill")
        if self.intent not in action_intents and not self.read_only:
            raise ValueError(f"意图 {self.intent.value} 必须声明为只读 Skill")
        if "training_start" in self.allowed_tools:
            raise ValueError("系统不再提供 training_start；请使用 forecast_start")
        if self.read_only and "forecast_start" in self.allowed_tools:
            raise ValueError(f"只读 Skill {self.name} 不允许包含写工具")
        return self

    def model_instructions(self) -> str:
        sections = [self.instructions]
        sections.extend(f"# 参考：{name}\n\n{content}" for name, content in self.references.items())
        return "\n\n".join(sections)

    def summary(self) -> dict[str, object]:
        """返回语义识别所需的最小能力说明。"""
        return {
            "name": self.name,
            "intent": self.intent.value,
            "description": self.description,
            "readOnly": self.read_only,
        }
