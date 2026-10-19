"""运行时加载的 Skill 文档。"""

from pydantic import BaseModel, ConfigDict, Field


class SkillDefinition(BaseModel):
    """一份可交给模型执行的业务说明。"""

    model_config = ConfigDict(extra="forbid")

    name: str
    description: str
    allowed_tools: list[str] = Field(alias="allowed-tools")
    instructions: str
    references: dict[str, str] = Field(default_factory=dict)

    def model_instructions(self) -> str:
        sections = [self.instructions]
        sections.extend(f"# 参考：{name}\n\n{content}" for name, content in self.references.items())
        return "\n\n".join(sections)

    def summary(self) -> dict[str, str]:
        return {"name": self.name, "description": self.description}
