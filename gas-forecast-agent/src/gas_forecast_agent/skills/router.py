"""选择与用户请求匹配的 Skill。"""

from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field

from .models import SkillIntent
from .registry import SkillRegistry
from .semantics import RequestSemantics


class SkillSelection(BaseModel):
    """经过校验的 Skill 路由结果。"""

    model_config = ConfigDict(extra="forbid")

    skill_name: str = Field(
        description="最终选中的已注册 Skill 编码",
    )
    confidence: float = Field(
        ge=0,
        le=1,
        description="本次 Skill 选择的置信度，范围为0到1",
    )
    reason: str = Field(
        description="选择该 Skill 的简短原因",
    )


class SkillRouter:
    """校验语义理解结果，并选出本轮唯一的已注册 Skill。"""

    def __init__(self, registry: SkillRegistry) -> None:
        self._registry = registry

    def select(
        self,
        semantics: RequestSemantics | None = None,
    ) -> SkillSelection:
        # understand_request 只返回稳定意图；Skill 名称由服务端注册表映射，
        # 不允许模型生成目录名，避免路由和 Skill 实现耦合。
        if not semantics or semantics.intent is None:
            raise ValueError("语义理解没有返回意图，无法继续执行")

        try:
            skill = self._registry.get_by_intent(semantics.intent)
        except KeyError as exc:
            raise ValueError(
                f"语义意图没有对应的 Skill：{semantics.intent.value}"
            ) from exc

        primary = semantics.primary_request
        return SkillSelection(
            skill_name=skill.name,
            confidence=0.8,
            reason=(
                f"由结构化请求 {primary.request_id} 的意图直接选择。"
                if primary
                else "由本轮结构化意图选择。"
            ),
        )
