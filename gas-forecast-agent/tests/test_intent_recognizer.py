import asyncio

import pytest
from pydantic import ValidationError

from gas_forecast_agent.capabilities import CapabilityRegistry
from gas_forecast_agent.config import Settings
from gas_forecast_agent.intent import HybridIntentRecognizer, IntentMatchStatus, LlmIntentRecognizer, RecognizedIntent, RuleIntentRecognizer, SpeechAct


def recognize(message: str, memory: dict[str, object] | None = None):
    return asyncio.run(RuleIntentRecognizer().recognize(message, memory or {}))


def test_rules_cover_master_data_task_and_short_term_intents() -> None:
    cases = {
        "查询所有地区": "REGION_LIST",
        "查询所有行业": "INDUSTRY_LIST",
        "查询江苏城燃客户": "CUSTOMER_LIST",
        "查看这个客户的详情信息": "CUSTOMER_DETAIL",
        "查看任务执行步骤": "TASK_STEPS",
        "这个任务执行到哪了": "TASK_STATUS",
        "恢复这个任务继续执行": "TASK_RESUME",
        "重试失败任务": "TASK_RETRY",
        "给这个客户训练模型": "SHORT_TERM_TRAINING_START",
        "训练进度到哪了": "SHORT_TERM_TRAINING_STATUS",
        "查看训练指标结果": "SHORT_TERM_TRAINING_RESULT",
        "这个客户训练过吗": "SHORT_TERM_TRAINING_LATEST",
        "查询历史训练记录": "SHORT_TERM_TRAINING_HISTORY",
        "预测这个客户未来30天用气量": "SHORT_TERM_FORECAST_START",
        "查询最新预测": "SHORT_TERM_FORECAST_LATEST",
    }
    for message, capability_code in cases.items():
        result = recognize(message)
        assert result.status == IntentMatchStatus.MATCHED, message
        assert result.capability_code == capability_code, message


def test_rules_recognize_configured_but_unavailable_scenarios() -> None:
    assert recognize("发起月度销量预测").capability_code == "MONTHLY_SALES_FORECAST_START"
    assert recognize("查询月度销量预测历史").capability_code == "MONTHLY_SALES_HISTORY"
    assert recognize("发起冬季保供训练").capability_code == "WINTER_SUPPLY_TRAINING_START"
    assert recognize("查看冬季保供预测结果").capability_code == "WINTER_SUPPLY_RESULT"


def test_rules_extract_deterministic_parameters() -> None:
    result = recognize("使用最近1740天数据训练江苏所有城燃客户")
    assert result.parameters["trainingPeriods"] == 1740
    assert result.parameters["trainMode"] == "RECENT"
    assert result.parameters["regionName"] == "江苏"
    assert result.parameters["industryName"] == "城燃"
    assert result.parameters["bulk"] is True


def test_rules_extract_real_training_batch_prefix() -> None:
    result = recognize("查询训练批次 TRBATCH-20261016-00714 的基本信息和回测效果")
    assert result.capability_code == "SHORT_TERM_TRAINING_RESULT"
    assert result.parameters["trainBatchNo"] == "TRBATCH-20261016-00714"


def test_customer_query_is_not_mistaken_for_region_list() -> None:
    result = recognize("查询浙江地区的城燃客户")
    assert result.capability_code == "CUSTOMER_LIST"
    assert result.parameters["regionName"] == "浙江"
    assert result.parameters["industryName"] == "城燃"


def test_rules_route_train_then_forecast_without_deciding_semantic_preference() -> None:
    result = recognize("给这个客户重新训练后预测未来30天")
    assert result.capability_code == "SHORT_TERM_FORECAST_START"
    assert "trainingPreference" not in result.parameters
    assert result.parameters["forecastHorizon"] == 30


@pytest.mark.parametrize("message", [
    "给这个客户发起新的训练再预测未来50天",
    "给这个客户新训练后预测未来50天",
    "先训练这个客户再预测未来50天",
    "帮我预测南京中燃未来50天，要发起一个最新训练进行预测",
    "创建一个最新的训练后再预测未来50天",
])
def test_rules_do_not_maintain_a_phrase_list_for_training_preference(message: str) -> None:
    result = recognize(message)
    assert result.capability_code == "SHORT_TERM_FORECAST_START"
    assert "trainingPreference" not in result.parameters


def test_model_training_preference_is_used_when_not_in_conflict() -> None:
    rules = RecognizedIntent(capability_code="SHORT_TERM_FORECAST_START", confidence=0.93, explanation="规则",
                             speech_act=SpeechAct.COMMAND, parameters={"forecastHorizon": 50})
    semantic = RecognizedIntent(capability_code="SHORT_TERM_FORECAST_START", confidence=0.96, explanation="模型",
                                speech_act=SpeechAct.COMMAND,
                                parameters={"forecastHorizon": 50, "trainingPreference": "FORCE_RETRAIN"},
                                parameter_confidence={"trainingPreference": 0.96})
    merged = HybridIntentRecognizer._merge_semantic(rules, semantic, "基于最新数据重新建模，然后预测50天")
    assert merged.parameters["trainingPreference"] == "FORCE_RETRAIN"


def test_model_existing_only_preference_is_used() -> None:
    rules = RecognizedIntent(capability_code="SHORT_TERM_FORECAST_START", confidence=0.93, explanation="规则",
                             speech_act=SpeechAct.COMMAND, parameters={})
    semantic = RecognizedIntent(capability_code="SHORT_TERM_FORECAST_START", confidence=0.9, explanation="模型",
                                speech_act=SpeechAct.COMMAND, parameters={"trainingPreference": "USE_EXISTING_ONLY"},
                                parameter_confidence={"trainingPreference": 0.94})
    merged = HybridIntentRecognizer._merge_semantic(rules, semantic, "不要重新训练，只用已有模型预测")
    assert merged.parameters["trainingPreference"] == "USE_EXISTING_ONLY"


def test_current_message_entity_overrides_model_memory_entity() -> None:
    rules = RecognizedIntent(capability_code="SHORT_TERM_TRAINING_LATEST", confidence=0.93, explanation="规则",
                             speech_act=SpeechAct.QUESTION, parameters={"customerName": "火星天然气公司"})
    semantic = RecognizedIntent(capability_code="SHORT_TERM_TRAINING_LATEST", confidence=0.9, explanation="模型",
                                speech_act=SpeechAct.QUESTION,
                                parameters={"customerCode": "CUS000061", "customerName": "南京中燃"})
    merged = HybridIntentRecognizer._merge_semantic(rules, semantic, "查询火星天然气公司的最新训练信息")
    assert merged.parameters["customerName"] == "火星天然气公司"
    assert "customerCode" not in merged.parameters


def test_explicit_customer_name_clears_old_code_and_scope_even_when_model_keeps_new_name() -> None:
    rules = RecognizedIntent(capability_code="SHORT_TERM_TRAINING_LATEST", confidence=0.93, explanation="规则",
                             speech_act=SpeechAct.QUESTION, parameters={"customerName": "火星天然气公司"})
    semantic = RecognizedIntent(status=IntentMatchStatus.AMBIGUOUS,
                                capability_code="SHORT_TERM_TRAINING_LATEST", confidence=0.7, explanation="上下文冲突",
                                speech_act=SpeechAct.QUESTION,
                                parameters={"customerCode": "CUS000061", "customerName": "火星天然气公司",
                                            "regionCode": "REG000012", "regionName": "江苏",
                                            "industryCode": "IND000001", "industryName": "城燃"})
    merged = HybridIntentRecognizer._merge_semantic(rules, semantic, "查询火星天然气公司的最新训练信息")
    assert merged.status == IntentMatchStatus.MATCHED
    assert merged.parameters == {"customerName": "火星天然气公司"}


def test_low_confidence_training_preference_asks_user_instead_of_reusing_old_model() -> None:
    rules = RecognizedIntent(capability_code="SHORT_TERM_FORECAST_START", confidence=0.93, explanation="规则",
                             speech_act=SpeechAct.COMMAND, parameters={})
    semantic = RecognizedIntent(capability_code="SHORT_TERM_FORECAST_START", confidence=0.82, explanation="模型不确定",
                                speech_act=SpeechAct.COMMAND, parameters={"trainingPreference": "AUTO"},
                                parameter_confidence={"trainingPreference": 0.55})
    merged = HybridIntentRecognizer._merge_semantic(rules, semantic, "处理一下训练再预测")
    assert merged.status == IntentMatchStatus.AMBIGUOUS
    assert "trainingPreference" not in merged.parameters
    assert "重新训练" in merged.clarification_question


def test_rules_keep_raw_customer_name_for_entity_resolution() -> None:
    result = recognize("查询杭州燃气最新预测")
    assert result.capability_code == "SHORT_TERM_FORECAST_LATEST"
    assert result.parameters["customerName"] == "杭州燃气"
    assert result.raw_entities[0].type == "CUSTOMER"


def test_rules_resolve_customer_pronoun_from_recent_user_history() -> None:
    memory = {"recentHistory": [
        {"role": "user", "content": "查询杭州燃气公司的客户信息"},
        {"role": "assistant", "content": "已找到杭州燃气公司。"},
    ]}
    result = recognize("预测这个客户未来两周", memory)
    assert result.capability_code == "SHORT_TERM_FORECAST_START"
    assert result.parameters["customerName"] == "杭州燃气公司"
    assert result.parameters["timeExpression"] == "未来两周"


def test_pending_action_supports_confirm_and_cancel() -> None:
    memory = {"pendingAction": {"capabilityCode": "SHORT_TERM_FORECAST_START"}}
    confirmed = recognize("确认", memory)
    cancelled = recognize("取消", memory)
    assert confirmed.capability_code == "SHORT_TERM_FORECAST_START"
    assert confirmed.speech_act == SpeechAct.CONFIRM
    assert confirmed.parameters["confirmed"] is True
    assert cancelled.status == IntentMatchStatus.CANCELLED


def test_rules_resume_latest_unfinished_task_instead_of_starting_new_forecast() -> None:
    memory = {"lastExecution": {"taskNo": "TASK-123", "status": "WAITING_TRAINING"}}
    result = recognize("我刚才的还没有预测完成，帮我把刚才的处理完毕", memory)
    assert result.capability_code == "TASK_RESUME"
    assert result.action == "RESUME"
    assert result.parameters["taskNo"] == "TASK-123"
    assert result.parameters["taskReference"] == "LATEST_UNFINISHED"
    assert result.speech_act == SpeechAct.COMMAND


def test_hybrid_blocks_model_from_turning_existing_task_reference_into_new_start() -> None:
    class WrongStartModel:
        async def recognize(self, message, memory, requested_agent=None):
            return RecognizedIntent(capability_code="SHORT_TERM_FORECAST_START", domain="FORECAST", action="START",
                                    resource="CUSTOMER_FORECAST", scenario="SHORT_TERM", confidence=0.91,
                                    explanation="误判为新预测", speech_act=SpeechAct.COMMAND,
                                    parameters={"trainingPreference": "UNSPECIFIED"},
                                    parameter_confidence={"trainingPreference": 0.9})

    recognizer = HybridIntentRecognizer(RuleIntentRecognizer(), WrongStartModel())
    result = asyncio.run(recognizer.recognize(
        "我刚才的还没有预测完成，帮我把刚才的处理完毕",
        {"lastExecution": {"taskNo": "TASK-123", "status": "WAITING_TRAINING"}},
    ))
    assert result.capability_code == "TASK_RESUME"
    assert result.parameters["taskNo"] == "TASK-123"


def test_capability_question_is_not_mistaken_for_execution() -> None:
    result = recognize("这个客户可以预测吗")
    assert result.status == IntentMatchStatus.MATCHED
    assert result.capability_code == "GENERAL_CHAT"
    assert result.speech_act == SpeechAct.QUESTION


@pytest.mark.parametrize("message", [
    "预测未来30天，但不要重新训练，只使用已有模型，可以这样做吗？",
    "如果客户没有可用模型，直接预测会怎么处理？",
])
def test_capability_consultation_never_creates_a_task(message: str) -> None:
    result = recognize(message, {"customerCode": "CUS000061", "customerName": "南京中燃"})
    assert result.capability_code == "GENERAL_CHAT"
    assert result.speech_act == SpeechAct.QUESTION


def test_plain_greeting_matches_greeting_capability() -> None:
    result = recognize("你好")
    assert result.capability_code == "GREETING"
    assert result.parameters == {}


def test_unmatched_message_falls_back_to_general_chat() -> None:
    result = recognize("给我讲个轻松的小故事")
    assert result.capability_code == "GENERAL_CHAT"
    assert result.parameters == {}


def test_qwen_catalog_contains_all_capability_statuses() -> None:
    settings = Settings(
        llm_api_key="test-key",
        llm_base_url="https://dashscope.aliyuncs.com/compatible-mode/v1",
        llm_model="qwen-plus",
    )
    recognizer = LlmIntentRecognizer(settings, CapabilityRegistry())
    assert len(recognizer._catalog) == 26
    assert {item["status"] for item in recognizer._catalog} == {"AVAILABLE", "NEEDS_CONFIRMATION", "PLANNED"}


@pytest.mark.parametrize("raw_entities", [{}, None, []])
def test_recognized_intent_normalizes_empty_raw_entities(raw_entities) -> None:
    intent = RecognizedIntent.model_validate({
        "confidence": 1,
        "explanation": "测试",
        "raw_entities": raw_entities,
    })
    assert intent.raw_entities == []


def test_recognized_intent_rejects_non_empty_raw_entity_object() -> None:
    with pytest.raises(ValidationError):
        RecognizedIntent.model_validate({
            "confidence": 1,
            "explanation": "测试",
            "raw_entities": {"type": "CUSTOMER", "raw_text": "苏州燃气"},
        })
