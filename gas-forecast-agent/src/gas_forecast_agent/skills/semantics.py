"""从本轮原话抽取稳定的业务参数语义。"""

from __future__ import annotations

import json
import logging
import re
from enum import StrEnum

from pydantic import BaseModel, ConfigDict, Field, field_validator

from gas_forecast_agent.config import Settings
from gas_forecast_agent.llm import build_llm_client


logger = logging.getLogger(__name__)


class TrainingPolicy(StrEnum):
    FORCE_RETRAIN = "FORCE_RETRAIN"
    AUTO_IF_MISSING = "AUTO_IF_MISSING"
    USE_EXISTING = "USE_EXISTING"
    UNSPECIFIED = "UNSPECIFIED"


class DialogueAct(StrEnum):
    """用户本轮话语相对于待确认动作的作用。"""

    NEW_REQUEST = "NEW_REQUEST"
    CONFIRM_PENDING = "CONFIRM_PENDING"
    REJECT_PENDING = "REJECT_PENDING"
    MODIFY_PENDING = "MODIFY_PENDING"
    UNSPECIFIED = "UNSPECIFIED"


class RequestSemantics(BaseModel):
    """只描述用户本轮明确表达的内容，不补全历史参数。"""

    model_config = ConfigDict(extra="forbid")

    operation: str = "UNSPECIFIED"
    dialogue_act: DialogueAct = DialogueAct.UNSPECIFIED
    customer_code: str | None = None
    customer_name: str | None = None
    region_name: str | None = None
    industry_name: str | None = None
    forecast_horizon: int | None = Field(default=None, ge=1, le=365)
    training_policy: TrainingPolicy = TrainingPolicy.UNSPECIFIED
    needs_clarification: bool = False
    clarification_question: str | None = None
    evidence: dict[str, str] = Field(default_factory=dict)

    @field_validator("region_name", mode="before")
    @classmethod
    def normalize_region_name(cls, value: object) -> object:
        """只清理行政区划通用后缀，不在 Agent 中维护省份名单。"""
        if not isinstance(value, str):
            return value
        text = "".join(value.split())
        return re.sub(r"(特别行政区|壮族自治区|回族自治区|维吾尔自治区|自治区|省|市|地区)$", "", text)

    @field_validator("industry_name", mode="before")
    @classmethod
    def normalize_industry_name(cls, value: object) -> object:
        """去掉模型附带的字段描述，行业别名仍交给 Java 主数据解析。"""
        if not isinstance(value, str):
            return value
        text = "".join(value.split())
        return re.sub(r"行业$", "", text)

    @field_validator("evidence", mode="before")
    @classmethod
    def normalize_evidence(cls, value: object) -> dict[str, str]:
        """兼容部分兼容模型把 evidence 直接返回成原文字符串。"""
        if value is None:
            return {}
        if isinstance(value, str):
            text = value.strip()
            return {"message": text} if text else {}
        if isinstance(value, dict):
            return {str(key): str(item) for key, item in value.items() if item is not None}
        return {}

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

    async def extract(
        self,
        message: str,
        skill_name: str,
        pending_action: dict[str, object] | None = None,
    ) -> RequestSemantics:
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
                            "只输出JSON，字段必须为：operation、dialogue_act、customer_code、customer_name、region_name、"
                            "industry_name、forecast_horizon、training_policy、needs_clarification、"
                            "clarification_question、evidence。dialogue_act未明确时填UNSPECIFIED，其他未明确字段填null。"
                            "dialogue_act只能是NEW_REQUEST、CONFIRM_PENDING、REJECT_PENDING、MODIFY_PENDING、UNSPECIFIED。"
                            "仅当输入包含pending_action时，结合该待确认动作判断用户是在确认、拒绝还是修改它；"
                            "用户提出无关的新目标时为NEW_REQUEST。没有pending_action时不得输出CONFIRM_PENDING。"
                            "region_name和industry_name只保留用户原话中的实体名称，不要包含查询、所有、客户信息等修饰语，"
                            "不要生成或猜测地区编码、行业编码。"
                            "training_policy只能是FORCE_RETRAIN、AUTO_IF_MISSING、USE_EXISTING、UNSPECIFIED。"
                            "用户表达重新、重做、从头、再跑一遍、新建或不沿用旧模型等同义语义时，"
                            "training_policy=FORCE_RETRAIN；只在缺少模型时训练为AUTO_IF_MISSING；"
                            "明确复用已有训练为USE_EXISTING。不能明确区分且会影响是否创建新训练时，"
                            "needs_clarification=true并给出简短问题。evidence记录对应原文片段。"
                        ),
                    },
                    {
                        "role": "user",
                        "content": json.dumps(
                            {"skill": skill_name, "message": message, "pending_action": pending_action},
                            ensure_ascii=False,
                        ),
                    },
                ],
            )
            extracted = RequestSemantics.model_validate_json(response.choices[0].message.content or "{}")
            updates: dict[str, object] = {}
            # 没有服务端待确认动作时，模型无权凭用户一句话伪造确认。
            if extracted.dialogue_act in {
                DialogueAct.CONFIRM_PENDING, DialogueAct.REJECT_PENDING, DialogueAct.MODIFY_PENDING,
            } and not pending_action:
                updates["dialogue_act"] = DialogueAct.UNSPECIFIED
            # 编码属于强标识，只能来自用户本轮原文，不能接受模型推测。
            if extracted.customer_code and extracted.customer_code.lower() not in message.lower():
                updates["customer_code"] = None
            # 会触发新训练的高风险语义必须能指回本轮原文，而且证据本身必须
            # 明确谈到训练或模型。仅把“预测”等普通词放进 evidence 不能证明
            # 用户要求了新的训练策略。
            if extracted.training_policy == TrainingPolicy.FORCE_RETRAIN:
                evidence = extracted.evidence.get("training_policy", "").strip()
                if not self._valid_training_policy_evidence(evidence, message):
                    updates.update(
                        training_policy=TrainingPolicy.UNSPECIFIED,
                        needs_clarification=False,
                        clarification_question=None,
                    )
            # 参数是否缺失由工具 Schema 和实体解析结果确定，不能接受模型生成的
            # 泛化追问。当前语义层唯一需要主动澄清的是会改变训练行为的策略冲突，
            # 且必须提供能在用户原话中定位到的证据。
            if extracted.needs_clarification:
                policy_evidence = extracted.evidence.get("training_policy", "").strip()
                if not self._valid_training_policy_evidence(policy_evidence, message):
                    updates.update(needs_clarification=False, clarification_question=None)
            normalized = extracted.model_copy(update=updates) if updates else extracted
            logger.info(
                "event=request_semantics_extracted skill=%s operation=%s dialogue_act=%s "
                "customer=%s region=%s industry=%s horizon=%s training_policy=%s clarification=%s",
                skill_name, normalized.operation, normalized.dialogue_act,
                normalized.customer_code or normalized.customer_name or "-",
                normalized.region_name or "-", normalized.industry_name or "-",
                normalized.forecast_horizon or "-", normalized.training_policy,
                normalized.needs_clarification,
            )
            return normalized
        except Exception:
            logger.exception("event=request_semantics_extract_failed skill=%s", skill_name)
            return RequestSemantics()

    @staticmethod
    def _valid_training_policy_evidence(evidence: str, message: str) -> bool:
        """验证证据确实来自原话并描述训练策略，而不是模型随意挑出的普通词。"""
        if not evidence or evidence not in message:
            return False
        return bool(re.search(r"训练|模型|建模|重训|学习|复用|沿用", evidence))
