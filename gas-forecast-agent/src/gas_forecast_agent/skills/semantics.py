"""从本轮原话抽取稳定的业务参数语义。"""

from __future__ import annotations

import json
import logging
from enum import StrEnum

from pydantic import BaseModel, ConfigDict, Field

from gas_forecast_agent.config import Settings
from gas_forecast_agent.llm import build_llm_client


logger = logging.getLogger(__name__)


class TrainingPolicy(StrEnum):
    FORCE_RETRAIN = "FORCE_RETRAIN"
    AUTO_IF_MISSING = "AUTO_IF_MISSING"
    USE_EXISTING = "USE_EXISTING"
    UNSPECIFIED = "UNSPECIFIED"


class RequestSemantics(BaseModel):
    """只描述用户本轮明确表达的内容，不补全历史参数。"""

    model_config = ConfigDict(extra="forbid")

    operation: str = "UNSPECIFIED"
    customer_code: str | None = None
    customer_name: str | None = None
    region_name: str | None = None
    industry_name: str | None = None
    forecast_horizon: int | None = Field(default=None, ge=1, le=365)
    training_policy: TrainingPolicy = TrainingPolicy.UNSPECIFIED
    needs_clarification: bool = False
    clarification_question: str | None = None
    evidence: dict[str, str] = Field(default_factory=dict)

    def parameter_overrides(self) -> dict[str, object]:
        values: dict[str, object] = {}
        mapping = {
            "customerCode": self.customer_code,
            "customerName": self.customer_name,
            "regionName": self.region_name,
            "industryName": self.industry_name,
            "forecastHorizon": self.forecast_horizon,
        }
        values.update({name: value for name, value in mapping.items() if value not in (None, "")})
        if self.training_policy == TrainingPolicy.FORCE_RETRAIN:
            values.update(forceRetrain=True, autoTrain=True, trainingPreference="FORCE_RETRAIN")
        elif self.training_policy == TrainingPolicy.AUTO_IF_MISSING:
            values.update(forceRetrain=False, autoTrain=True, trainingPreference="AUTO")
        elif self.training_policy == TrainingPolicy.USE_EXISTING:
            values.update(forceRetrain=False, autoTrain=False, trainingPreference="USE_EXISTING_ONLY")
        return values


class RequestSemanticExtractor:
    """使用结构化模型输出理解本轮参数；失败时不伪造语义。"""

    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    async def extract(self, message: str, skill_name: str) -> RequestSemantics:
        if self._settings.skill_routing_mode == "rules" or not self._settings.llm_api_key or not self._settings.llm_model:
            return RequestSemantics()
        try:
            response = await build_llm_client(self._settings).chat.completions.create(
                model=self._settings.llm_model,
                temperature=0,
                response_format={"type": "json_object"},
                messages=[
                    {
                        "role": "system",
                        "content": (
                            "你是业务参数语义抽取器，只分析用户本轮原话，不使用或猜测历史信息。"
                            "只输出JSON，字段必须为：operation、customer_code、customer_name、region_name、"
                            "industry_name、forecast_horizon、training_policy、needs_clarification、"
                            "clarification_question、evidence。未明确字段填null。"
                            "training_policy只能是FORCE_RETRAIN、AUTO_IF_MISSING、USE_EXISTING、UNSPECIFIED。"
                            "用户表达重新、重做、从头、再跑一遍、新建或不沿用旧模型等同义语义时，"
                            "training_policy=FORCE_RETRAIN；只在缺少模型时训练为AUTO_IF_MISSING；"
                            "明确复用已有训练为USE_EXISTING。不能明确区分且会影响是否创建新训练时，"
                            "needs_clarification=true并给出简短问题。evidence记录对应原文片段。"
                        ),
                    },
                    {
                        "role": "user",
                        "content": json.dumps({"skill": skill_name, "message": message}, ensure_ascii=False),
                    },
                ],
            )
            extracted = RequestSemantics.model_validate_json(response.choices[0].message.content or "{}")
            updates: dict[str, object] = {}
            # 编码属于强标识，只能来自用户本轮原文，不能接受模型推测。
            if extracted.customer_code and extracted.customer_code.lower() not in message.lower():
                updates["customer_code"] = None
            # 会触发新训练的高风险语义必须能指回本轮原文；证据无效时转为追问。
            if extracted.training_policy == TrainingPolicy.FORCE_RETRAIN:
                evidence = extracted.evidence.get("training_policy", "").strip()
                if not evidence or evidence not in message:
                    updates.update(
                        training_policy=TrainingPolicy.UNSPECIFIED,
                        needs_clarification=True,
                        clarification_question="请确认：是否要忽略已有模型并创建一个全新的训练批次？",
                    )
            return extracted.model_copy(update=updates) if updates else extracted
        except Exception:
            logger.exception("event=request_semantics_extract_failed skill=%s", skill_name)
            return RequestSemantics()
