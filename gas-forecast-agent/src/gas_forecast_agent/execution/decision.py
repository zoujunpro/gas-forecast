"""业务异常的受限决策模型。"""

from __future__ import annotations

import json
from typing import Any, Literal, Protocol

from pydantic import BaseModel, Field

from gas_forecast_agent.config import Settings
from gas_forecast_agent.llm import build_llm_client


Action = Literal["WAIT", "QUERY_STATUS", "QUERY_STEPS", "START_FORECAST", "QUERY_RESULT", "RESUME", "RETRY", "ASK_USER", "FAIL"]


class ExceptionDecision(BaseModel):
    """大模型只能从服务端白名单中选择下一步。"""

    action: Action
    reason: str
    user_message: str | None = None
    proposed_parameters: dict[str, Any] = Field(default_factory=dict)
    confidence: float = Field(default=0.5, ge=0, le=1)


class ExceptionDecider(Protocol):
    async def decide(self, context: dict[str, Any], allowed_actions: list[str]) -> ExceptionDecision: ...


class QwenExceptionDecider:
    """仅在Java无法给出确定动作时，让项目现有千问服务选择处理方向。"""

    def __init__(self, settings: Settings) -> None:
        if not settings.llm_api_key or not settings.llm_model:
            raise ValueError("异常决策需要配置LLM_API_KEY和LLM_MODEL")
        self._model = settings.llm_model
        self._client = build_llm_client(settings)

    async def decide(self, context: dict[str, Any], allowed_actions: list[str]) -> ExceptionDecision:
        response = await self._client.chat.completions.create(
            model=self._model,
            temperature=0,
            response_format={"type": "json_object"},
            messages=[
                {"role": "system", "content": "你是燃气预测任务异常处理器。只能从allowedActions选择action；不能编造任务状态、批次号或成功结果。重试会创建新批次时优先ASK_USER。只返回JSON。"},
                {"role": "user", "content": json.dumps({"context": context, "allowedActions": allowed_actions}, ensure_ascii=False, default=str)},
            ],
        )
        decision = ExceptionDecision.model_validate_json(response.choices[0].message.content or "{}")
        return validate_decision(decision, allowed_actions)


def validate_decision(decision: ExceptionDecision, allowed_actions: list[str]) -> ExceptionDecision:
    """拒绝模型越权动作；安全降级为询问用户或结束。"""
    if decision.action in allowed_actions:
        return decision
    fallback: Action = "ASK_USER" if "ASK_USER" in allowed_actions else "FAIL"
    return ExceptionDecision(action=fallback, reason=f"模型选择的动作{decision.action}不在服务端白名单中",
                             user_message="当前异常无法自动处理，需要人工确认。", confidence=1)
