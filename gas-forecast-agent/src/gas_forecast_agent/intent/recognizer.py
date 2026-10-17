"""规则约束与千问语义识别组合的意图识别器。"""

from __future__ import annotations

import json
import logging
import re
from datetime import datetime
from typing import Any, Protocol
from zoneinfo import ZoneInfo


from gas_forecast_agent.capabilities import CapabilityDefinition, CapabilityRegistry
from gas_forecast_agent.config import Settings
from gas_forecast_agent.llm import build_llm_client
from gas_forecast_agent.schemas import AgentType

from .models import IntentMatchStatus, RawEntity, RecognizedIntent, SpeechAct

logger = logging.getLogger(__name__)


class IntentRecognizer(Protocol):
    async def recognize(self, message: str, memory: dict[str, object], requested_agent: AgentType | None = None) -> RecognizedIntent: ...


class LlmIntentRecognizer:
    """通过项目现有的 OpenAI 兼容千问服务识别全部业务能力。"""

    def __init__(self, settings: Settings, capabilities: CapabilityRegistry) -> None:
        if not settings.llm_api_key or not settings.llm_model:
            raise ValueError("千问意图识别需要配置LLM_API_KEY和LLM_MODEL")
        self._model = settings.llm_model
        self._client = build_llm_client(settings)
        self._capabilities = capabilities
        self._catalog = [self._catalog_item(item) for item in capabilities.list(include_unavailable=True)]

    async def recognize(self, message: str, memory: dict[str, object], requested_agent: AgentType | None = None) -> RecognizedIntent:
        completion = await self._client.chat.completions.create(
            model=self._model,
            temperature=0.1,
            response_format={"type": "json_object"},
            messages=[
                {"role": "system", "content": self._system_prompt()},
                {"role": "user", "content": json.dumps({
                    "userMessage": message,
                    "currentDate": datetime.now(ZoneInfo("Asia/Shanghai")).date().isoformat(),
                    "timezone": "Asia/Shanghai",
                    "requestedAgent": requested_agent.value if requested_agent else None,
                    "shortTermMemory": self._safe_memory(memory),
                    "capabilities": self._catalog,
                }, ensure_ascii=False, default=str)},
            ],
        )
        content = completion.choices[0].message.content or ""
        try:
            payload = json.loads(self._clean_json(content))
            parsed = RecognizedIntent.model_validate(payload)
        except Exception as exc:
            raise RuntimeError(f"千问未返回有效的结构化意图：{exc}") from exc
        if not parsed.raw_entities:
            raw_value = payload.get("raw_entities") if isinstance(payload, dict) else None
            normalized = raw_value is None or raw_value == {}
            logger.info(
                "event=intent_entities_empty message=未从用户输入中抽取到实体 normalized=%s original_type=%s capability_code=%s",
                normalized,
                type(raw_value).__name__,
                parsed.capability_code or "-",
            )
        return self._normalize(parsed)

    def _normalize(self, intent: RecognizedIntent) -> RecognizedIntent:
        capability: CapabilityDefinition | None = None
        if intent.capability_code:
            try:
                capability = self._capabilities.get(intent.capability_code)
            except KeyError:
                intent.status = IntentMatchStatus.UNKNOWN
                intent.capability_code = None
        if capability is None and intent.domain and intent.action and intent.resource:
            matches = [item for item in self._capabilities.list(include_unavailable=True)
                       if item.intent.domain == intent.domain and item.intent.action == intent.action
                       and item.intent.resource == intent.resource and item.intent.scenario == intent.scenario]
            if len(matches) == 1:
                capability = matches[0]
        if capability:
            intent.capability_code = capability.code
            intent.domain, intent.action = capability.intent.domain, capability.intent.action
            intent.resource, intent.scenario = capability.intent.resource, capability.intent.scenario
        if intent.status == IntentMatchStatus.UNKNOWN:
            fallback = self._capabilities.get("GENERAL_CHAT")
            return RecognizedIntent(
                status=IntentMatchStatus.MATCHED,
                capability_code=fallback.code,
                domain=fallback.intent.domain,
                action=fallback.intent.action,
                resource=fallback.intent.resource,
                scenario=fallback.intent.scenario,
                confidence=1,
                explanation="未命中业务能力，转入通用对话。",
                speech_act=SpeechAct.QUESTION,
            )
        if intent.status == IntentMatchStatus.MATCHED and (capability is None or intent.confidence < 0.6):
            intent.status = IntentMatchStatus.AMBIGUOUS
            intent.candidate_capabilities = intent.candidate_capabilities or ([capability.code] if capability else [])
            intent.clarification_question = intent.clarification_question or "请说明要查询、训练、预测，还是查看任务进度。"
        return intent

    @staticmethod
    def _catalog_item(item: CapabilityDefinition) -> dict[str, Any]:
        return {"code": item.code, "name": item.name, "description": item.description, "status": item.status.value,
                "intent": item.intent.model_dump(mode="json"), "parameters": item.required_parameters + item.optional_parameters}

    @staticmethod
    def _safe_memory(memory: dict[str, object]) -> dict[str, object]:
        allowed = {"currentIntent", "currentCapability", "currentParameters", "missingParameters", "pendingAction",
                   "customerCode", "customerName", "regionCode", "regionName", "industryCode", "industryName",
                   "taskNo", "trainBatchNo", "forecastBatchNo", "lastTrainBatchNo", "lastForecastBatchNo",
                   "lastExecution", "recentHistory"}
        return {key: value for key, value in memory.items() if key in allowed}

    @staticmethod
    def _clean_json(content: str) -> str:
        text = content.strip()
        if text.startswith("```"):
            text = re.sub(r"^```(?:json)?\s*", "", text, flags=re.I)
            text = re.sub(r"\s*```$", "", text)
        start, end = text.find("{"), text.rfind("}")
        return text[start:end + 1] if start >= 0 and end >= start else text

    @staticmethod
    def _system_prompt() -> str:
        return """你是天然气预测系统的意图分类器。只返回JSON对象，不要Markdown。
必须从capabilities中选择capability_code，不得创造能力或工具名。即使能力状态不是AVAILABLE，也可以识别，执行层会拦截。
status只能是MATCHED、AMBIGUOUS、UNKNOWN、CANCELLED；speech_act只能是QUESTION、COMMAND、CONFIRM、CANCEL、FOLLOW_UP、UNKNOWN。
区分START、STATUS、RESULT、LATEST、HISTORY：开始执行才是START；进度是STATUS；某批次结果是RESULT；最近一次是LATEST；历史记录是HISTORY。
“刚才的、之前的、上一个、那个没完成的”表示既有任务。询问是否完成使用TASK_STATUS；要求继续处理使用TASK_RESUME；失败后明确要求再试使用TASK_RETRY。此类表达绝不能识别为新的训练或预测START。
parameters只能提取用户原话、recentHistory中的用户消息或shortTermMemory已有的信息，不得编造任何编码、任务号、批次号或日期。recentHistory只用于理解指代、省略和连续追问，助手消息不能当成用户确认的事实。
所有FORECAST_START能力必须在parameters.trainingPreference中返回且只能返回FORCE_RETRAIN、UNSPECIFIED、USE_EXISTING_ONLY之一，并在parameter_confidence.trainingPreference返回0到1的独立置信度：
FORCE_RETRAIN表示用户明确要求先创建一次新训练再预测；UNSPECIFIED表示用户没有说明训练要求；USE_EXISTING_ONLY表示用户明确要求不训练、只用已有模型。UNSPECIFIED不代表允许自动训练。请理解语义而不是只匹配关键词。
解析“今天、明天、下周、下个月、月底、未来两周、最近一个月”等相对时间时必须以currentDate和timezone为基准；保留用户原始表达到timeExpression，并输出对应的标准日期参数。若存在多种合理解释，返回AMBIGUOUS并追问。
如果存在missingParameters或pendingAction，判断用户是在补充、确认、取消还是提出新问题。补充时保留原能力并合并参数。
无法唯一确定能力时返回AMBIGUOUS，candidate_capabilities给出候选编码，并生成简短clarification_question。
raw_entities必须是JSON数组，保存用户原文中的CUSTOMER、REGION、INDUSTRY、TIME实体，不能把模型推测内容放进去；没有实体时必须返回[]，不得返回{}或null。
输出字段必须完整：status, capability_code, domain, action, resource, scenario, parameters, parameter_confidence, raw_entities, confidence, explanation, speech_act, candidate_capabilities, clarification_question。"""


class RuleIntentRecognizer:
    """确定性识别明确表达，并抽取不应交给模型猜测的参数。"""

    _AFFIRM = re.compile(r"^(是|好的?|可以|确认|继续|执行|没问题|同意)[。！! ]*$")
    _CANCEL = re.compile(r"^(不|否|取消|算了|不用了|停止)[。！! ]*$")
    _GREETING = re.compile(r"^(你好|您好|嗨|哈喽|hello|hi|在吗|谢谢|感谢|辛苦了)[。！!,.， ]*$", re.I)

    def __init__(self, capabilities: CapabilityRegistry | None = None) -> None:
        self._capabilities = capabilities or CapabilityRegistry()

    async def recognize(self, message: str, memory: dict[str, object], requested_agent: AgentType | None = None) -> RecognizedIntent:
        matched = self.try_recognize(message, memory, requested_agent)
        if matched:
            return matched
        if any(word in (message or "") for word in ("客户", "地区", "行业", "训练", "预测", "任务", "批次")):
            return self._ambiguous()
        return self._by_code("GENERAL_CHAT", {}, 1, SpeechAct.QUESTION)

    def try_recognize(self, message: str, memory: dict[str, object], requested_agent: AgentType | None = None) -> RecognizedIntent | None:
        text = re.sub(r"\s+", "", message or "")
        pending_code = self._pending_capability(memory)
        if pending_code and self._CANCEL.match(text):
            return RecognizedIntent(status=IntentMatchStatus.CANCELLED, confidence=1, explanation="用户取消待处理动作", speech_act=SpeechAct.CANCEL)
        if pending_code and self._AFFIRM.match(text):
            return self._by_code(pending_code, {"confirmed": True}, 1, SpeechAct.CONFIRM)
        if not pending_code and self._GREETING.match(text):
            return self._by_code("GREETING", {}, 1, SpeechAct.QUESTION)
        # 能力和流程咨询不授权创建训练或预测任务。
        if self._is_capability_question(text):
            return self._by_code("GENERAL_CHAT", {}, 1, SpeechAct.QUESTION)

        parameters = self._extract_parameters(message, memory)
        scenario = self._scenario(text, requested_agent)
        code: str | None = None
        if "地区" in text and "客户" not in text and any(word in text for word in ("所有", "列表", "哪些", "查询", "查看")):
            code = "REGION_LIST"
        elif "行业" in text and "客户" not in text and not any(word in text for word in ("训练", "预测")):
            code = "INDUSTRY_LIST"
        elif "客户" in text and any(word in text for word in ("详情", "信息")) and not any(word in text for word in ("所有", "列表", "哪些")):
            code = "CUSTOMER_DETAIL"
        elif "客户" in text and any(word in text for word in ("所有", "列表", "哪些", "名单", "查询", "查找")) and not any(word in text for word in ("训练", "预测")):
            code = "CUSTOMER_LIST"
        elif "任务" in text and "步骤" in text:
            code = "TASK_STEPS"
        elif self._references_unfinished_task(text) and any(word in text for word in ("处理完", "处理完成", "继续", "恢复", "接着")):
            code = "TASK_RESUME"
            parameters["taskReference"] = "LATEST_UNFINISHED"
        elif any(word in text for word in ("恢复", "继续执行")) and "任务" in text:
            code = "TASK_RESUME"
        elif "重试" in text and ("任务" in text or parameters.get("taskNo")):
            code = "TASK_RETRY"
        elif ("任务" in text or parameters.get("taskNo")) and any(word in text for word in ("进度", "执行到哪", "状态")):
            code = "TASK_STATUS"
        elif any(word in text for word in ("训练", "重训")) and any(word in text for word in ("预测", "预估")):
            code = self._scenario_code(scenario, "FORECAST", "START")
        elif any(word in text for word in ("训练", "重训")):
            if any(word in text for word in ("是否需要训练", "要不要训练", "需要训练吗")):
                return None
            if any(word in text for word in ("查询", "查看")) and not any(word in text for word in ("历史", "以前", "记录", "最新", "最近一次", "上一次", "进度", "状态", "到哪", "结果", "指标", "效果")):
                return None
            code = self._scenario_code(scenario, "TRAINING", self._action(text))
        elif any(word in text for word in ("预测", "预估")):
            if any(word in text for word in ("可以预测吗", "能预测吗", "能否预测", "是否支持预测")):
                return None
            if any(word in text for word in ("查询", "查看")) and not any(word in text for word in ("历史", "以前", "记录", "最新", "最近一次", "上一次", "进度", "状态", "到哪", "结果", "指标", "效果")):
                return None
            code = self._scenario_code(scenario, "FORECAST", self._action(text))
        if not code:
            return None
        try:
            speech_act = SpeechAct.QUESTION if any(word in text for word in ("吗", "是否", "多少", "哪些", "查询", "查看")) else SpeechAct.COMMAND
            return self._by_code(code, parameters, 0.93, speech_act)
        except KeyError:
            return None

    def _by_code(self, code: str, parameters: dict[str, Any], confidence: float, speech_act: SpeechAct) -> RecognizedIntent:
        item = self._capabilities.get(code)
        return RecognizedIntent(status=IntentMatchStatus.MATCHED, capability_code=code, domain=item.intent.domain,
                                action=item.intent.action, resource=item.intent.resource, scenario=item.intent.scenario,
                                parameters=parameters, raw_entities=self._raw_entities(parameters), confidence=confidence,
                                explanation="由确定性规则识别。", speech_act=speech_act)

    @staticmethod
    def _references_unfinished_task(text: str) -> bool:
        reference = any(word in text for word in ("刚才", "之前", "上一个", "前一个", "那个", "刚刚"))
        unfinished = any(word in text for word in ("没完成", "未完成", "还没有", "没处理完", "卡住", "中断"))
        return reference and unfinished

    @staticmethod
    def _is_capability_question(text: str) -> bool:
        if not any(word in text for word in ("训练", "预测")):
            return False
        return bool(
            re.search(r"(可以|能否|能不能|是否支持|支不支持).*(吗|么|？|\?)$", text)
            or re.search(r"(会|应该|需要).*(怎么|如何)处理", text)
            or re.search(r"(怎么|如何)处理", text)
        )

    @staticmethod
    def _raw_entities(parameters: dict[str, Any]) -> list[RawEntity]:
        mapping = {
            "customerName": ("CUSTOMER", None),
            "regionName": ("REGION", None),
            "industryName": ("INDUSTRY", None),
            "timeExpression": ("TIME", None),
        }
        return [RawEntity(type=kind, raw_text=str(parameters[key]), purpose=purpose)
                for key, (kind, purpose) in mapping.items() if parameters.get(key)]

    @staticmethod
    def _action(text: str) -> str:
        if any(word in text for word in ("历史", "以前", "记录")): return "HISTORY"
        if any(word in text for word in ("最新", "最近一次", "上一次", "训练过吗")): return "LATEST"
        if any(word in text for word in ("进度", "状态", "到哪")): return "STATUS"
        if any(word in text for word in ("结果", "指标", "效果")): return "RESULT"
        return "START"

    @staticmethod
    def _scenario_code(scenario: str, domain: str, action: str) -> str | None:
        if scenario != "SHORT_TERM" and action not in {"START", "HISTORY", "RESULT"}: return None
        code = f"{scenario}_{domain}_{action}"
        return {"MONTHLY_SALES_FORECAST_HISTORY": "MONTHLY_SALES_HISTORY",
                "WINTER_SUPPLY_FORECAST_RESULT": "WINTER_SUPPLY_RESULT"}.get(code, code)

    @staticmethod
    def _scenario(text: str, requested_agent: AgentType | None) -> str:
        if requested_agent:
            return {AgentType.SHORT_TERM: "SHORT_TERM", AgentType.MONTHLY_SALES: "MONTHLY_SALES", AgentType.WINTER_SUPPLY: "WINTER_SUPPLY"}[requested_agent]
        if any(word in text for word in ("冬季", "采暖季", "保供", "供需缺口")): return "WINTER_SUPPLY"
        if any(word in text for word in ("月度", "本月", "下个月", "月销量", "销量")): return "MONTHLY_SALES"
        return "SHORT_TERM"

    @staticmethod
    def _pending_capability(memory: dict[str, object]) -> str | None:
        pending = memory.get("pendingAction")
        if isinstance(pending, dict) and isinstance(pending.get("capabilityCode"), str): return pending["capabilityCode"]
        current = memory.get("currentCapability")
        return current if memory.get("missingParameters") and isinstance(current, str) else None

    @staticmethod
    def _extract_parameters(message: str, memory: dict[str, object]) -> dict[str, Any]:
        keys = ("customerCode", "customerName", "regionCode", "regionName", "industryCode", "industryName", "taskNo", "trainBatchNo", "forecastBatchNo")
        result = {key: memory[key] for key in keys if memory.get(key) not in (None, "")}
        last_execution = memory.get("lastExecution")
        if isinstance(last_execution, dict):
            for key in ("taskNo", "trainBatchNo", "forecastBatchNo"):
                if not result.get(key) and last_execution.get(key):
                    result[key] = last_execution[key]
        text = message or ""
        history = memory.get("recentHistory")
        previous_user_texts = [str(item.get("content") or "") for item in history or []
                               if isinstance(item, dict) and item.get("role") == "user"]
        context_texts = [text, *reversed(previous_user_texts)]
        for key, pattern in {"taskNo": r"\b(TASK[-_A-Za-z0-9]+)\b", "trainBatchNo": r"\b((?:TRBATCH|TRAIN)[-_A-Za-z0-9]+)\b",
                             "forecastBatchNo": r"\b(FORECAST[-_A-Za-z0-9]+)\b", "customerCode": r"(?:客户编号|客户编码)[:：]?\s*([A-Za-z0-9_-]+)"}.items():
            match = re.search(pattern, text, re.I)
            if match: result[key] = match.group(1)
        for context_text in context_texts:
            customer = re.search(r"(?:查询|查看|给|为)?\s*([\u4e00-\u9fa5A-Za-z0-9（）()·]{2,30}?(?:天然气|燃气|集团|公司))(?:的|最新|历史|进行|训练|预测|未来|$)", context_text)
            if customer:
                name = re.sub(r"^(?:查询|查看|给|为)", "", customer.group(1)).strip()
                if name not in {"这个客户", "该客户", "客户"}:
                    result["customerName"] = name
                    break
        horizon = re.search(r"(?:预测未来|未来|预测)\s*(\d{1,3})\s*(?:天|日)", text)
        if horizon:
            result["forecastHorizon"] = int(horizon.group(1))
            result["timeExpression"] = horizon.group(0)
        periods = re.search(r"(?:最近|近)\s*(\d{1,5})\s*(?:天|日).*(?:训练|重训)", text)
        if periods:
            result.update({"trainMode": "RECENT", "trainingPeriods": int(periods.group(1)), "timeExpression": periods.group(0)})
        explicit_range = re.search(r"(20\d{2}[-/.年]\d{1,2}[-/.月]\d{1,2}日?)\s*(?:到|至|~|—|-)\s*(20\d{2}[-/.年]\d{1,2}[-/.月]\d{1,2}日?)", text)
        if explicit_range:
            result["timeExpression"] = explicit_range.group(0)
        relative = re.search(r"(?:最近|近|过去)\s*(\d{1,4})\s*(?:天|日|个月|月)", text)
        if relative and "timeExpression" not in result:
            result["timeExpression"] = relative.group(0)
        natural_time = re.search(r"(今天|明天|后天|昨天|本周|这周|下周|本月|这个月|下个月|月末|月底|未来\s*[一二两三四五六七八九十\d]+\s*(?:天|日|周|个月|月)|最近\s*(?:一个|一|两|二|三|四|五|六|\d+)\s*(?:周|个月|月))", text)
        if natural_time and "timeExpression" not in result:
            result["timeExpression"] = natural_time.group(0)
        if re.search(r"(?:全部|所有|完整)(?:历史)?数据.*(?:训练|重训)", text): result["trainMode"] = "ALL"
        if re.search(r"(?:全部|所有|每个).*(?:客户|用户)", text): result["bulk"] = True
        for context_text in context_texts:
            matched = next((region for region in ("江苏", "浙江", "安徽", "上海", "山东", "河南", "河北", "北京", "天津", "广东", "福建", "江西", "湖北", "湖南", "四川", "重庆") if region in context_text), None)
            if matched:
                result["regionName"] = matched
                break
        for context_text in context_texts:
            matched = next((industry for industry in ("城燃", "化工", "电力", "发电", "CNG") if industry.lower() in context_text.lower()), None)
            if matched:
                result["industryName"] = "电力" if matched == "发电" else matched.upper() if matched == "CNG" else matched
                break
        return result

    @staticmethod
    def _ambiguous() -> RecognizedIntent:
        return RecognizedIntent(status=IntentMatchStatus.AMBIGUOUS, confidence=0, explanation="规则无法唯一确定业务能力。",
                                clarification_question="请说明要查询客户信息、训练模型、发起预测，还是查看任务进度。")


class HybridIntentRecognizer:
    """模型主判自然语言语义；规则处理确认/取消并在模型失败时安全降级。"""

    def __init__(self, rules: RuleIntentRecognizer, llm: LlmIntentRecognizer | None) -> None:
        self._rules, self._llm = rules, llm

    async def recognize(self, message: str, memory: dict[str, object], requested_agent: AgentType | None = None) -> RecognizedIntent:
        deterministic = self._rules.try_recognize(message, memory, requested_agent)
        if deterministic and deterministic.speech_act in {SpeechAct.CONFIRM, SpeechAct.CANCEL}:
            return deterministic
        if self._llm:
            try:
                semantic = await self._llm.recognize(message, memory, requested_agent)
                if semantic.status == IntentMatchStatus.UNKNOWN:
                    return self._rules._by_code("GENERAL_CHAT", {}, 1, SpeechAct.QUESTION)
                if deterministic and semantic.capability_code == deterministic.capability_code:
                    return self._merge_semantic(deterministic, semantic, message)
                # 既有任务指代属于高风险边界：不能被模型改成创建新的训练或预测任务。
                if deterministic and deterministic.capability_code in {"TASK_STATUS", "TASK_RESUME", "TASK_RETRY"}:
                    if semantic.action == "START" or (semantic.capability_code or "").endswith("_START"):
                        logger.warning("event=intent_start_blocked reason=existing_task_reference message=%s", message)
                        return deterministic
                return semantic
            except Exception as exc:
                logger.warning("千问意图识别失败，降级为规则结果：%s", exc)
        if deterministic:
            return deterministic
        return await self._rules.recognize(message, memory, requested_agent)

    @staticmethod
    def _merge_semantic(deterministic: RecognizedIntent, semantic: RecognizedIntent, message: str) -> RecognizedIntent:
        """一次模型调用给出完整字段；规则只校验高风险训练策略与明确原话不冲突。"""
        merged = {**deterministic.parameters, **semantic.parameters}
        # 规则从本轮原文抽取出的实体优先，防止模型把短期记忆中的旧实体覆盖到新问题。
        for key in ("customerName", "regionName", "industryName", "taskNo", "trainBatchNo", "forecastBatchNo", "timeExpression"):
            if deterministic.parameters.get(key):
                merged[key] = deterministic.parameters[key]
        if deterministic.parameters.get("customerName"):
            # 本轮只说客户名称时，旧客户编码及旧范围不得跟随；交给实体接口重新解析。
            if not merged.get("customerCode") or str(merged["customerCode"]) not in message:
                merged.pop("customerCode", None)
            if not deterministic.parameters.get("regionName") or str(deterministic.parameters["regionName"]) not in message:
                merged.pop("regionCode", None)
                merged.pop("regionName", None)
            if not deterministic.parameters.get("industryName") or str(deterministic.parameters["industryName"]) not in message:
                merged.pop("industryCode", None)
                merged.pop("industryName", None)
            if semantic.status == IntentMatchStatus.AMBIGUOUS and semantic.capability_code == deterministic.capability_code:
                semantic.status = IntentMatchStatus.MATCHED
                semantic.candidate_capabilities = []
                semantic.clarification_question = None
        preference = semantic.parameters.get("trainingPreference")
        allowed = {"FORCE_RETRAIN", "UNSPECIFIED", "USE_EXISTING_ONLY"}
        if preference not in allowed:
            merged.pop("trainingPreference", None)

        training_confidence = semantic.parameter_confidence.get("trainingPreference", 0.0)
        needs_training_decision = bool(semantic.capability_code and semantic.capability_code.endswith("FORECAST_START"))
        if needs_training_decision and (preference not in allowed or training_confidence < 0.75):
            semantic.status = IntentMatchStatus.AMBIGUOUS
            semantic.clarification_question = "请确认这次是要重新训练后再预测，还是直接使用已有模型预测？"
            merged.pop("trainingPreference", None)

        semantic.parameters = merged
        semantic.raw_entities = semantic.raw_entities or deterministic.raw_entities
        return semantic


def build_intent_recognizer(settings: Settings, capabilities: CapabilityRegistry) -> IntentRecognizer:
    """构建规则与千问组合识别器。"""
    mode, rules = settings.intent_mode.strip().lower(), RuleIntentRecognizer(capabilities)
    llm = LlmIntentRecognizer(settings, capabilities) if settings.llm_api_key and settings.llm_model else None
    if mode == "rules": return rules
    if mode == "llm":
        if llm is None: raise ValueError("GAS_INTENT_MODE=llm时必须配置项目现有的LLM_API_KEY和LLM_MODEL")
        return llm
    if mode in {"auto", "hybrid"}: return HybridIntentRecognizer(rules, llm)
    raise ValueError("GAS_INTENT_MODE只支持hybrid、auto、llm或rules")
