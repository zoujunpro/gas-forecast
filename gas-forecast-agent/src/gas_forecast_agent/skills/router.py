"""选择与用户请求匹配的 Skill。"""

from __future__ import annotations

import json
import re

from pydantic import BaseModel, ConfigDict, Field

from gas_forecast_agent.config import Settings
from gas_forecast_agent.llm import build_llm_client

from .registry import SkillRegistry


class SkillSelection(BaseModel):
    model_config = ConfigDict(extra="forbid")

    skill_name: str
    confidence: float = Field(ge=0, le=1)
    reason: str


class SkillRouter:
    def __init__(self, settings: Settings, registry: SkillRegistry) -> None:
        self._settings = settings
        self._registry = registry

    async def select(
        self,
        message: str,
        memory: dict[str, object] | None = None,
        context: dict[str, object] | None = None,
    ) -> SkillSelection:
        if (context or {}).get("scene") == "FORECAST_ANALYSIS":
            return SkillSelection(
                skill_name="customer-gas-forecast",
                confidence=1,
                reason="页面已绑定预测结果场景。",
            )
        remembered = (memory or {}).get("currentSkill")
        if remembered and self._is_confirmation(message):
            self._registry.get(str(remembered))
            return SkillSelection(skill_name=str(remembered), confidence=1, reason="继续处理待确认操作。")
        if self._settings.skill_routing_mode != "rules" and self._settings.llm_api_key and self._settings.llm_model:
            try:
                return await self._select_with_model(message)
            except Exception:
                pass
        return self._select_with_rules(message)

    @staticmethod
    def _is_confirmation(message: str) -> bool:
        return re.sub(r"\s+", "", message).lower() in {"确认", "确定", "继续", "是", "好的", "可以", "同意"}

    async def _select_with_model(self, message: str) -> SkillSelection:
        catalog = [skill.summary() for skill in self._registry.list_skills()]
        response = await build_llm_client(self._settings).chat.completions.create(
            model=self._settings.llm_model,
            temperature=0,
            response_format={"type": "json_object"},
            messages=[
                {
                    "role": "system",
                    "content": (
                        "从给定 Skill 中选择唯一最匹配项。只输出 JSON："
                        '{"skill_name":"...","confidence":0.0,"reason":"..."}。'
                        "不要选择不存在的 Skill。"
                    ),
                },
                {"role": "user", "content": json.dumps({"skills": catalog, "message": message}, ensure_ascii=False)},
            ],
        )
        selection = SkillSelection.model_validate_json(response.choices[0].message.content or "{}")
        self._registry.get(selection.skill_name)
        return selection

    @staticmethod
    def _select_with_rules(message: str) -> SkillSelection:
        text = re.sub(r"\s+", "", message).lower()
        # “重新训练后预测”最终目标仍是预测，应由预测 Skill 通过 forecast_start
        # 编排训练后自动续跑；只有不包含预测目标时才进入独立训练管理。
        if any(word in text for word in ("预测", "预估")):
            name = "customer-gas-forecast"
        elif any(word in text for word in (
            "训练", "重训", "重新建模", "重新学习", "跑模型", "跑一下",
            "恢复任务", "恢复刚才", "恢复训练", "重试任务", "重试训练",
        )):
            name = "model-training-management"
        else:
            name = "business-data-query"
        return SkillSelection(skill_name=name, confidence=0.9, reason="由明确业务词选择。")
