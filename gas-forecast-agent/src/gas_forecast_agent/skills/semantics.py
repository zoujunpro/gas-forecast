"""从用户本轮原话抽取稳定、可审计的请求语义。"""

from __future__ import annotations

import json
import logging
import re
from enum import StrEnum
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

from gas_forecast_agent.config import Settings
from gas_forecast_agent.llm import build_llm_client

from .models import SkillIntent


logger = logging.getLogger(__name__)


class SemanticExtractionError(RuntimeError):
    """语义模型不可用或返回无效结果。"""


def _to_camel(name: str) -> str:
    head, *tail = name.split("_")
    return head + "".join(part.capitalize() for part in tail)


class SemanticModel(BaseModel):
    """意图识别模型公共配置：输入兼容蛇形，输出使用小驼峰。"""

    model_config = ConfigDict(
        alias_generator=_to_camel,
        populate_by_name=True,
        serialize_by_alias=True,
        extra="forbid",
        str_strip_whitespace=True,
    )


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


class TargetSelection(StrEnum):
    """用户如何选择本次请求的目标集合，而不是最终匹配数量。"""

    EXPLICIT = "EXPLICIT"
    ALL_MATCHED = "ALL_MATCHED"
    CONTEXT = "CONTEXT"
    UNSPECIFIED = "UNSPECIFIED"


class ContextRelation(StrEnum):
    """本轮请求对最近兼容业务上下文的处理方式。"""

    NEW = "NEW"
    CONTINUE = "CONTINUE"
    MODIFY = "MODIFY"


class BusinessScenario(StrEnum):
    """系统当前支持的三类预测业务场景。"""

    SHORT_TERM = "short-term"
    MONTHLY_SALES = "monthly-sales"
    WINTER_SUPPLY = "winter-supply"


class RequestEntities(SemanticModel):
    """用户原话中明确出现的实体名称；内部主数据编码不属于识别结果。"""

    regions: list[str] = Field(default_factory=list)
    industries: list[str] = Field(default_factory=list)
    customers: list[str] = Field(default_factory=list)

    @field_validator("regions", "industries", "customers", mode="before")
    @classmethod
    def normalize_entity_list(cls, value: object) -> list[object]:
        """兼容单字符串或{name/text}对象，但永远丢弃模型生成的编码。"""
        if value in (None, ""):
            return []
        items = value if isinstance(value, list) else [value]
        normalized: list[object] = []
        for item in items:
            if isinstance(item, dict):
                item = item.get("name") or item.get("text")
            if item not in (None, ""):
                normalized.append(item)
        return normalized

    @field_validator("regions")
    @classmethod
    def normalize_regions(cls, values: list[str]) -> list[str]:
        return cls._unique(
            re.sub(
                r"(特别行政区|壮族自治区|回族自治区|维吾尔自治区|自治区|省|市|地区)$",
                "",
                cls._clean_scope_word(value),
            )
            for value in values
        )

    @field_validator("industries")
    @classmethod
    def normalize_industries(cls, values: list[str]) -> list[str]:
        return cls._unique(
            re.sub(r"行业$", "", cls._clean_scope_word(value))
            for value in values
        )

    @field_validator("customers")
    @classmethod
    def normalize_customers(cls, values: list[str]) -> list[str]:
        return cls._unique("".join(value.split()) for value in values)

    @staticmethod
    def _clean_scope_word(value: str) -> str:
        text = "".join(str(value).split())
        return re.sub(r"^(?:所有|全部|全量|每个)", "", text)

    @staticmethod
    def _unique(values: Any) -> list[str]:
        result: list[str] = []
        for value in values:
            text = str(value).strip()
            if text and text not in result:
                result.append(text)
        return result


class SemanticRequest(SemanticModel):
    """一句话中的一个独立业务请求。"""

    request_id: str = Field(default="R1", min_length=1, max_length=32)
    intent: SkillIntent | None = Field(default=None, description="稳定顶层意图；存在歧义时为空")
    scenario: BusinessScenario | None = Field(default=None, description="用户明确表达的预测业务场景")
    target_selection: TargetSelection = Field(
        default=TargetSelection.UNSPECIFIED,
        description="用户选择目标的方式，不代表解析后的目标数量",
    )
    context_relation: ContextRelation = Field(
        default=ContextRelation.NEW,
        description="NEW不继承；CONTINUE承接；MODIFY承接后修改",
    )
    clear_parameters: list[str] = Field(
        default_factory=list,
        description="MODIFY 时明确取消的业务过滤维度：region、industry、customer",
    )
    entities: RequestEntities = Field(default_factory=RequestEntities)
    references: dict[str, Any] = Field(
        default_factory=dict,
        description="刚才、上次、这些客户等尚未解析的上下文指代",
    )
    parameters: dict[str, Any] = Field(
        default_factory=dict,
        description="用户明确给出的时间、周期和训练策略等操作参数",
    )
    depends_on: list[str] = Field(default_factory=list, description="本请求依赖的同轮requestId")

    @field_validator("intent", mode="before")
    @classmethod
    def normalize_intent(cls, value: object) -> object:
        if value in (None, ""):
            return None
        text = str(value).strip().upper()
        return text if text in {item.value for item in SkillIntent} else None

    @field_validator("scenario", mode="before")
    @classmethod
    def normalize_scenario(cls, value: object) -> object:
        if value in (None, "", "UNSPECIFIED"):
            return None
        aliases = {
            "SHORT_TERM": BusinessScenario.SHORT_TERM,
            "SHORT-TERM": BusinessScenario.SHORT_TERM,
            "MONTHLY_SALES": BusinessScenario.MONTHLY_SALES,
            "MONTHLY-SALES": BusinessScenario.MONTHLY_SALES,
            "WINTER_SUPPLY": BusinessScenario.WINTER_SUPPLY,
            "WINTER-SUPPLY": BusinessScenario.WINTER_SUPPLY,
        }
        return aliases.get(str(value).strip().upper(), value)

    @field_validator("target_selection", mode="before")
    @classmethod
    def normalize_target_selection(cls, value: object) -> object:
        return TargetSelection.UNSPECIFIED if value in (None, "") else str(value).strip().upper()

    @field_validator("context_relation", mode="before")
    @classmethod
    def normalize_context_relation(cls, value: object) -> object:
        return ContextRelation.NEW if value in (None, "") else str(value).strip().upper()

    @field_validator("clear_parameters", mode="before")
    @classmethod
    def normalize_clear_parameters(cls, value: object) -> list[str]:
        values = value if isinstance(value, list) else [value]
        aliases = {
            "region": "region", "地区": "region", "regioncode": "region", "regionname": "region",
            "industry": "industry", "行业": "industry", "industrycode": "industry", "industryname": "industry",
            "customer": "customer", "客户": "customer", "customercode": "customer", "customername": "customer",
        }
        return list(dict.fromkeys(
            aliases[str(item).replace("_", "").lower()]
            for item in values
            if str(item).replace("_", "").lower() in aliases
        ))

    @field_validator("references", mode="before")
    @classmethod
    def normalize_references(cls, value: object) -> dict[str, Any]:
        return dict(value) if isinstance(value, dict) else {}

    @field_validator("parameters", mode="before")
    @classmethod
    def normalize_parameters(cls, value: object) -> dict[str, Any]:
        if not isinstance(value, dict):
            return {}
        aliases = {
            "forecast_horizon": "forecastHorizon",
            "training_start_date": "historyStartDate",
            "training_end_date": "historyEndDate",
        }
        blocked = {
            "customerCode", "customer_code",
            "regionCode", "region_code",
            "industryCode", "industry_code",
            "needsClarification", "needs_clarification",
            "clarificationQuestion", "clarification_question",
        }
        result: dict[str, Any] = {}
        for key, item in value.items():
            name = str(key)
            if name in blocked or item is None:
                continue
            result[aliases.get(name, name)] = item
        policy = result.get("trainingPolicy")
        if policy is not None:
            normalized_policy = str(policy).strip().upper()
            result["trainingPolicy"] = (
                normalized_policy
                if normalized_policy in {item.value for item in TrainingPolicy}
                else TrainingPolicy.UNSPECIFIED.value
            )
        horizon = result.get("forecastHorizon")
        if horizon is not None:
            try:
                horizon_value = int(horizon)
            except (TypeError, ValueError):
                result.pop("forecastHorizon", None)
            else:
                if 1 <= horizon_value <= 365:
                    result["forecastHorizon"] = horizon_value
                else:
                    result.pop("forecastHorizon", None)
        return result

    @field_validator("depends_on", mode="before")
    @classmethod
    def normalize_dependencies(cls, value: object) -> list[str]:
        if value in (None, ""):
            return []
        items = value if isinstance(value, list) else [value]
        return list(dict.fromkeys(str(item).strip() for item in items if str(item).strip()))

    @property
    def training_policy(self) -> TrainingPolicy:
        raw = self.parameters.get("trainingPolicy", TrainingPolicy.UNSPECIFIED)
        try:
            return TrainingPolicy(str(raw).upper())
        except ValueError:
            return TrainingPolicy.UNSPECIFIED

    @property
    def forecast_horizon(self) -> int | None:
        value = self.parameters.get("forecastHorizon")
        return value if isinstance(value, int) and 1 <= value <= 365 else None

    def parameter_overrides(self) -> dict[str, object]:
        """把单请求语义适配为现有执行规划器的候选参数。"""
        values = {
            key: value
            for key, value in self.parameters.items()
            if value not in (None, "", TrainingPolicy.UNSPECIFIED.value)
        }
        if len(self.entities.customers) == 1:
            values["customerName"] = self.entities.customers[0]
        elif self.entities.customers:
            values["customerNames"] = list(self.entities.customers)
            if self.intent != SkillIntent.FORECAST:
                values["bulk"] = True
        if len(self.entities.regions) == 1:
            values["regionName"] = self.entities.regions[0]
        elif self.entities.regions:
            values["regionNames"] = list(self.entities.regions)
        if len(self.entities.industries) == 1:
            values["industryName"] = self.entities.industries[0]
        elif self.entities.industries:
            values["industryNames"] = list(self.entities.industries)
        if self.target_selection == TargetSelection.ALL_MATCHED:
            values["fetchAll"] = True
            if self.intent != SkillIntent.FORECAST:
                values["bulk"] = True
        if self.scenario is not None:
            values["agentCode"] = self.scenario.value

        policy = self.training_policy
        if policy == TrainingPolicy.FORCE_RETRAIN:
            values.update(forceRetrain=True, autoTrain=True, trainingPreference="FORCE_RETRAIN")
        elif policy == TrainingPolicy.AUTO_IF_MISSING:
            values.update(forceRetrain=False, autoTrain=True, trainingPreference="AUTO")
        elif policy == TrainingPolicy.USE_EXISTING:
            values.update(forceRetrain=False, autoTrain=False, trainingPreference="USE_EXISTING_ONLY")
        return values


class RequestSemantics(SemanticModel):
    """
    用户本轮原话的结构化语义。

    本对象只记录原话明确表达的请求、实体名称、指代和参数。上下文合并、
    主数据匹配、工具参数校验以及是否需要澄清均由后续流程完成。
    """

    dialogue_act: DialogueAct = Field(default=DialogueAct.UNSPECIFIED)
    requests: list[SemanticRequest] = Field(default_factory=list)

    @model_validator(mode="before")
    @classmethod
    def upgrade_flat_payload(cls, value: object) -> object:
        """滚动升级期间兼容旧扁平结果，但序列化始终使用新结构。"""
        if not isinstance(value, dict):
            return value
        data = dict(value)
        if "requests" in data:
            for key in (
                "needsClarification", "needs_clarification",
                "clarificationQuestion", "clarification_question",
                "customerCode", "customer_code",
                "regionCode", "region_code",
                "industryCode", "industry_code",
            ):
                data.pop(key, None)
            requests = data.get("requests")
            if isinstance(requests, list):
                sanitized_requests: list[object] = []
                for item in requests:
                    if not isinstance(item, dict):
                        sanitized_requests.append(item)
                        continue
                    request = dict(item)
                    for key in (
                        "needsClarification", "needs_clarification",
                        "clarificationQuestion", "clarification_question",
                        "customerCode", "customer_code",
                        "regionCode", "region_code",
                        "industryCode", "industry_code",
                    ):
                        request.pop(key, None)
                    sanitized_requests.append(request)
                data["requests"] = sanitized_requests
            return data
        dialogue_act = data.get("dialogueAct", data.get("dialogue_act", DialogueAct.UNSPECIFIED))
        intent = data.get("intent")
        customer = data.get("customerName", data.get("customer_name"))
        region = data.get("regionName", data.get("region_name"))
        industry = data.get("industryName", data.get("industry_name"))
        horizon = data.get("forecastHorizon", data.get("forecast_horizon"))
        policy = data.get("trainingPolicy", data.get("training_policy"))
        has_request = any((
            intent not in (None, ""),
            customer not in (None, ""),
            region not in (None, ""),
            industry not in (None, ""),
            horizon not in (None, ""),
            policy not in (None, "", "UNSPECIFIED"),
        ))
        if not has_request:
            return {"dialogueAct": dialogue_act, "requests": []}

        parameters = dict(data.get("parameters") or {})
        if horizon not in (None, ""):
            parameters["forecastHorizon"] = horizon
        if policy not in (None, ""):
            parameters["trainingPolicy"] = policy
        fetch_all = data.get("fetchAll", data.get("fetch_all"))
        if customer not in (None, ""):
            target_selection = TargetSelection.EXPLICIT
        elif fetch_all is True:
            target_selection = TargetSelection.ALL_MATCHED
        else:
            target_selection = TargetSelection.UNSPECIFIED
        request = {
            "requestId": data.get("requestId", data.get("request_id", "R1")),
            "intent": intent,
            "scenario": data.get("scenario"),
            "targetSelection": target_selection,
            "contextRelation": data.get("contextRelation", data.get("context_relation", "NEW")),
            "clearParameters": data.get("clearParameters", data.get("clear_parameters", [])),
            "entities": {
                "regions": [region] if region not in (None, "") else [],
                "industries": [industry] if industry not in (None, "") else [],
                "customers": [customer] if customer not in (None, "") else [],
            },
            "references": data.get("references") or {},
            "parameters": parameters,
            "dependsOn": data.get("dependsOn", data.get("depends_on", [])),
        }
        return {"dialogueAct": dialogue_act, "requests": [request]}

    @field_validator("dialogue_act", mode="before")
    @classmethod
    def normalize_dialogue_act(cls, value: object) -> object:
        return DialogueAct.UNSPECIFIED if value in (None, "") else str(value).strip().upper()

    @property
    def primary_request(self) -> SemanticRequest | None:
        return self.requests[0] if self.requests else None

    # 以下只用于现有单请求执行链的平滑适配，不会出现在新的序列化结果中。
    @property
    def intent(self) -> SkillIntent | None:
        return self.primary_request.intent if self.primary_request else None

    @property
    def target_selection(self) -> TargetSelection:
        return self.primary_request.target_selection if self.primary_request else TargetSelection.UNSPECIFIED

    @property
    def customer_names(self) -> list[str]:
        return list(self.primary_request.entities.customers) if self.primary_request else []

    @property
    def region_names(self) -> list[str]:
        return list(self.primary_request.entities.regions) if self.primary_request else []

    @property
    def industry_names(self) -> list[str]:
        return list(self.primary_request.entities.industries) if self.primary_request else []

    @property
    def customer_name(self) -> str | None:
        return self.customer_names[0] if self.customer_names else None

    @property
    def region_name(self) -> str | None:
        return self.region_names[0] if self.region_names else None

    @property
    def industry_name(self) -> str | None:
        return self.industry_names[0] if self.industry_names else None

    @property
    def fetch_all(self) -> bool | None:
        return True if self.target_selection == TargetSelection.ALL_MATCHED else None

    @property
    def forecast_horizon(self) -> int | None:
        return self.primary_request.forecast_horizon if self.primary_request else None

    @property
    def training_policy(self) -> TrainingPolicy:
        return self.primary_request.training_policy if self.primary_request else TrainingPolicy.UNSPECIFIED

    @property
    def has_explicit_entities(self) -> bool:
        request = self.primary_request
        return bool(request and any((
            request.entities.customers,
            request.entities.regions,
            request.entities.industries,
        )))

    def parameter_overrides(self) -> dict[str, object]:
        return self.primary_request.parameter_overrides() if self.primary_request else {}


class RequestSemanticExtractor:
    """一次模型调用抽取一项或多项请求，不在本层决定是否澄清。"""

    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    async def extract(
        self,
        message: str,
        pending_action: dict[str, object] | None = None,
        pending_interaction: dict[str, object] | None = None,
        input_type: str = "TEXT",
        answers: dict[str, object] | None = None,
        available_skills: list[dict[str, object]] | None = None,
        conversation_context: dict[str, object] | None = None,
    ) -> RequestSemantics:
        if input_type == "TEXT" and self._is_plain_chat(message):
            # 对完整、无业务目标的寒暄做确定性保护，避免模型受历史预测上下文
            # 影响而把“你好”识别成继续预测。包含业务诉求的长句仍交给语义模型。
            return RequestSemantics(
                dialogue_act=DialogueAct.NEW_REQUEST,
                requests=[SemanticRequest(
                    intent=SkillIntent.CHAT,
                )],
            )
        if self._settings.skill_routing_mode == "rules":
            raise SemanticExtractionError("当前配置未启用语义识别模型")
        if not self._settings.llm_api_key or not self._settings.llm_model:
            raise SemanticExtractionError("语义识别模型配置不完整")
        try:
            response = await build_llm_client(self._settings).chat.completions.create(
                model=self._settings.llm_model,
                temperature=0,
                response_format={"type": "json_object"},
                messages=[
                    {"role": "system", "content": self._system_prompt()},
                    {
                        "role": "user",
                        "content": json.dumps(
                            {
                                "skills": available_skills or [],
                                "message": message,
                                "inputType": input_type,
                                "answers": answers or {},
                                "pendingAction": pending_action,
                                "pendingInteraction": pending_interaction,
                                "conversationContext": conversation_context or {},
                            },
                            ensure_ascii=False,
                        ),
                    },
                ],
            )
            extracted = RequestSemantics.model_validate_json(response.choices[0].message.content or "{}")
            normalized = self._normalize(
                extracted,
                message,
                pending_action,
                pending_interaction,
                available_skills or [],
            )
            if not normalized.requests:
                raise SemanticExtractionError("语义识别模型没有返回任何请求")
            primary = normalized.primary_request
            logger.info(
                "event=request_semantics_extracted request_count=%s intent=%s "
                "dialogue_act=%s target_selection=%s customers=%s regions=%s industries=%s",
                len(normalized.requests),
                primary.intent.value if primary and primary.intent else "-",
                normalized.dialogue_act,
                primary.target_selection if primary else TargetSelection.UNSPECIFIED,
                primary.entities.customers if primary else [],
                primary.entities.regions if primary else [],
                primary.entities.industries if primary else [],
            )
            return normalized
        except SemanticExtractionError:
            raise
        except Exception as exc:
            logger.exception("event=request_semantics_extract_failed")
            raise SemanticExtractionError("语义识别模型调用或结果解析失败") from exc

    @staticmethod
    def _system_prompt() -> str:
        return (
            "你是业务意图与语义抽取器，以用户本轮原话为主，不解析主数据，不判断是否需要澄清。"
            "conversationContext只用于理解省略表达、承接关系和上下文指代，不能把历史消息当成本轮新请求，"
            "也不能从历史中复制用户本轮没有明确提及的实体。只输出一个JSON对象，结构必须为："
            "{\"dialogueAct\":\"NEW_REQUEST|CONFIRM_PENDING|REJECT_PENDING|MODIFY_PENDING|UNSPECIFIED\","
            "\"requests\":[{\"requestId\":\"R1\",\"intent\":\"TRAIN|FORECAST|QUERY|ANALYZE|CHAT\","
            "\"scenario\":null,"
            "\"targetSelection\":\"EXPLICIT|ALL_MATCHED|CONTEXT|UNSPECIFIED\","
            "\"contextRelation\":\"NEW|CONTINUE|MODIFY\",\"clearParameters\":[],"
            "\"entities\":{\"regions\":[],\"industries\":[],\"customers\":[]},"
            "\"references\":{},\"parameters\":{},\"dependsOn\":[]}]}."
            "一句话包含多个独立操作时拆成多个requests并按出现顺序编号；后一个请求依赖前一个结果时填写dependsOn。"
            "intent就是Skill分类，只能从skills提供的intent中选择；识别出intent即确定唯一Skill。"
            "TRAIN和FORECAST都创建统一预测任务；训练、回测和预测结果查询都属于QUERY。"
            "具体查询内容和工具调用由后续Skill根据原话、参数和工具结果决定，本层不得输出工具名称。"
            "contextRelation默认NEW：NEW不得继承历史业务过滤条件；仅当用户明确承接上一轮时用CONTINUE，"
            "明确替换或取消上一轮条件时用MODIFY。clearParameters只可填region、industry、customer，"
            "表示用户明确取消该维度限制；未说承接时不得因为conversationContext存在实体而填CONTINUE或MODIFY。"
            "要求解释变化、比较结果、分析误差或原因使用ANALYZE。"
            "问候、感谢、概念说明、系统用法及不包含业务目标的普通交流使用CHAT。"
            "当前原话无法可靠归入任何能力时intent填null，不得猜测TRAIN、FORECAST或QUERY。"
            "entities三个字段始终是字符串数组，支持一个或多个名称，只能逐字抽取用户说出的地区、行业、客户名称。"
            "用户不知道内部客户、地区、行业编码，entities和parameters中禁止输出这些编码，也禁止猜测或生成编码。"
            "同类实体表示或关系，不同类实体表示且关系。EXPLICIT表示明确列举一个或多个客户；"
            "ALL_MATCHED表示按地区、行业等条件选择全部匹配客户；CONTEXT表示目标来自‘这些客户’‘当前客户’等上下文；"
            "UNSPECIFIED表示原话没有表达选择方式。‘当前客户’‘这些客户’不是实体名称，应放入references。"
            "‘最新’是查询操作语义，不是上下文指代，不要把LATEST放入references。"
            "scenario仅在用户明确表达短期、月度销量或冬季保供时填写short-term、monthly-sales或winter-supply，否则为null。"
            "parameters只保存用户明确表达的操作参数，例如forecastHorizon、historyStartDate、historyEndDate。"
            "仅当输入中确实包含pendingAction时，才能输出CONFIRM_PENDING、REJECT_PENDING或MODIFY_PENDING；"
            "否则新业务目标使用NEW_REQUEST，无法判断使用UNSPECIFIED。"
            "当inputType为CLARIFICATION_REPLY时，必须结合pendingInteraction中的原请求理解用户答案；"
            "实体、参数或范围澄清只修正原请求，输出MODIFY_PENDING，不得因为答案很短而改成问候或新请求；"
            "只有原问题的reason是INTENT_AMBIGUOUS时，才重新判断最终intent。"
            "普通TEXT如果是‘那客户呢’‘再预测15天’等承接表达，可结合conversationContext恢复intent；"
            "省略的目标必须使用CONTEXT和references表示，不得伪装成本轮逐字出现的entities。"
            "输出中禁止出现needsClarification、clarificationQuestion、customerCode、regionCode或industryCode。"
        )

    @staticmethod
    def _is_plain_chat(message: str) -> bool:
        """识别完整寒暄；只匹配整句，避免吞掉“你好，帮我预测”等业务请求。"""
        compact = re.sub(r"[\s!！。,.，?？~～]+", "", message).lower()
        return compact in {
            "你好", "您好", "嗨", "哈喽", "哈啰", "hello", "hi", "hey",
            "在吗", "早", "早上好", "上午好", "下午好", "晚上好",
            "谢谢", "多谢", "感谢", "辛苦了", "再见", "拜拜",
        }

    def _normalize(
        self,
        semantics: RequestSemantics,
        message: str,
        pending_action: dict[str, object] | None,
        pending_interaction: dict[str, object] | None,
        available_skills: list[dict[str, object]],
    ) -> RequestSemantics:
        dialogue_act = semantics.dialogue_act
        if dialogue_act in {
            DialogueAct.CONFIRM_PENDING,
            DialogueAct.REJECT_PENDING,
            DialogueAct.MODIFY_PENDING,
        } and not pending_action and not pending_interaction:
            dialogue_act = DialogueAct.UNSPECIFIED

        allowed_intents = {
            str(item.get("intent"))
            for item in available_skills
            if item.get("intent")
        }
        normalized_requests: list[SemanticRequest] = []
        used_ids: set[str] = set()
        for index, request in enumerate(semantics.requests, start=1):
            request_id = request.request_id.strip() or f"R{index}"
            if request_id in used_ids:
                request_id = f"R{index}"
            used_ids.add(request_id)

            intent = request.intent
            if intent and allowed_intents and intent.value not in allowed_intents:
                intent = None

            entities = RequestEntities(
                customers=self._verified_mentions(request.entities.customers, message, "customer"),
                regions=self._verified_mentions(request.entities.regions, message, "region"),
                industries=self._verified_mentions(request.entities.industries, message, "industry"),
            )
            parameters = dict(request.parameters)
            policy = self._training_policy(parameters)
            if policy != TrainingPolicy.UNSPECIFIED and not self._valid_training_policy(policy, message):
                parameters["trainingPolicy"] = TrainingPolicy.UNSPECIFIED.value

            references = dict(request.references)

            context_relation = request.context_relation
            # 防止模型仅因历史存在实体就伪造承接关系；承接必须有本轮文本证据。
            if context_relation != ContextRelation.NEW and not re.search(
                r"那|这个|这些|当前|刚才|上次|继续|换成|改成|不要|不限制|取消|去掉|还是",
                message,
            ):
                context_relation = ContextRelation.NEW
            clear_parameters = request.clear_parameters if context_relation == ContextRelation.MODIFY else []

            target_selection = request.target_selection
            if entities.customers:
                target_selection = TargetSelection.EXPLICIT
            elif references and target_selection == TargetSelection.UNSPECIFIED:
                target_selection = TargetSelection.CONTEXT

            scenario = request.scenario
            if scenario and not self._scenario_is_explicit(scenario, message):
                scenario = None

            normalized_requests.append(request.model_copy(update={
                "request_id": request_id,
                "intent": intent,
                "scenario": scenario,
                "target_selection": target_selection,
                "context_relation": context_relation,
                "clear_parameters": clear_parameters,
                "entities": entities,
                "references": references,
                "parameters": parameters,
            }))

        valid_ids = {request.request_id for request in normalized_requests}
        normalized_requests = [
            request.model_copy(update={
                "depends_on": [
                    dependency
                    for dependency in request.depends_on
                    if dependency in valid_ids and dependency != request.request_id
                ],
            })
            for request in normalized_requests
        ]
        return RequestSemantics(dialogue_act=dialogue_act, requests=normalized_requests)

    @classmethod
    def _verified_mentions(
        cls,
        values: list[str],
        message: str,
        entity_type: str = "generic",
    ) -> list[str]:
        """允许可解释的口语简称，同时拒绝只存在于历史或模型猜测中的实体。"""
        compact_message = cls._compact_mention_text(message)
        message_variants = {compact_message, compact_message.replace("的", "")}
        verified: list[str] = []
        for value in values:
            aliases = cls._mention_aliases(value, entity_type)
            if any(
                len(alias) >= 2 and alias in candidate
                for alias in aliases
                for candidate in message_variants
            ):
                verified.append(value)
        return verified

    @staticmethod
    def _compact_mention_text(value: str) -> str:
        return re.sub(r"[^0-9a-zA-Z\u4e00-\u9fff]+", "", str(value)).lower()

    @classmethod
    def _mention_aliases(cls, value: str, entity_type: str) -> set[str]:
        """生成有限且可审计的实体别名，不做无边界模糊匹配。"""
        base = cls._compact_mention_text(value)
        aliases = {base} if base else set()
        if entity_type == "customer":
            suffixes = (
                "有限责任公司", "股份有限公司", "集团有限公司", "股份公司",
                "集团公司", "有限公司", "天然气公司", "燃气公司", "集团", "公司",
            )
            candidate = base
            changed = True
            while changed:
                changed = False
                for suffix in suffixes:
                    if candidate.endswith(suffix) and len(candidate) - len(suffix) >= 3:
                        candidate = candidate[:-len(suffix)]
                        aliases.add(candidate)
                        changed = True
                        break
            for suffix in ("天然气", "燃气"):
                if candidate.endswith(suffix) and len(candidate) - len(suffix) >= 3:
                    aliases.add(candidate[:-len(suffix)])
        elif entity_type == "region":
            for suffix in (
                "特别行政区", "壮族自治区", "回族自治区", "维吾尔自治区",
                "自治区", "地区", "省", "市",
            ):
                if base.endswith(suffix) and len(base) - len(suffix) >= 2:
                    aliases.add(base[:-len(suffix)])
        elif entity_type == "industry":
            industry_aliases = {
                "城市燃气": "城燃",
                "液化天然气": "lng",
                "压缩天然气": "cng",
                "天然气发电": "燃气发电",
                "化学工业": "化工",
            }
            for formal, colloquial in industry_aliases.items():
                if base == formal:
                    aliases.add(colloquial)
                elif base == colloquial:
                    aliases.add(formal)
        return {alias for alias in aliases if alias}

    @staticmethod
    def _training_policy(parameters: dict[str, Any]) -> TrainingPolicy:
        try:
            return TrainingPolicy(str(parameters.get("trainingPolicy", "UNSPECIFIED")).upper())
        except ValueError:
            return TrainingPolicy.UNSPECIFIED

    @staticmethod
    def _valid_training_policy(policy: TrainingPolicy, message: str) -> bool:
        compact = "".join(message.split()).lower()
        if policy == TrainingPolicy.FORCE_RETRAIN:
            return bool(re.search(r"重新训练|重训|重新建模|从头|重做|重跑|再跑|新一轮|另起炉灶|新训练", compact))
        if policy == TrainingPolicy.AUTO_IF_MISSING:
            return bool(re.search(r"没有.{0,8}(?:模型|训练结果).{0,8}(?:再|就)?训练|缺少.{0,8}(?:模型|训练结果)", compact))
        if policy == TrainingPolicy.USE_EXISTING:
            return bool(re.search(r"已有|现有|上次|当前|最近一次|复用|沿用|不要重新训练", compact))
        return True

    @staticmethod
    def _scenario_is_explicit(scenario: BusinessScenario, message: str) -> bool:
        terms = {
            BusinessScenario.SHORT_TERM: ("短期", "日预测", "short-term"),
            BusinessScenario.MONTHLY_SALES: ("月度", "月销量", "月用气", "monthly-sales"),
            BusinessScenario.WINTER_SUPPLY: ("冬季", "保供", "旬", "winter-supply"),
        }
        lower_message = message.lower()
        return any(term in lower_message for term in terms[scenario])
