import asyncio
import json
from types import SimpleNamespace

import pytest

from gas_forecast_agent.config import Settings
from gas_forecast_agent.skills import (
    DialogueAct,
    RequestEntities,
    SemanticExtractionError,
    RequestSemanticExtractor,
    RequestSemantics,
    SkillIntent,
    TargetSelection,
    TrainingPolicy,
)
SKILLS = [
    {
        "name": "model-training-management",
        "intent": "TRAIN",
    },
    {
        "name": "customer-gas-forecast",
        "intent": "FORECAST",
    },
    {
        "name": "business-data-query",
        "intent": "QUERY",
    },
    {
        "name": "forecast-analysis",
        "intent": "ANALYZE",
    },
    {
        "name": "knowledge-qa",
        "intent": "KNOWLEDGE_QA",
    },
    {
        "name": "general-chat",
        "intent": "CHAT",
    },
    {
        "name": "unknown-request",
        "intent": "UNKNOWN",
    },
]


def model_settings() -> Settings:
    values = {"LLM_" + "API_KEY": "unit-test-placeholder", "LLM_MODEL": "test-model"}
    return Settings(GAS_SKILL_ROUTING_MODE="hybrid", **values)


def extract(
    monkeypatch,
    message: str,
    payload: dict,
    pending_action=None,
    conversation_context=None,
) -> tuple[RequestSemantics, dict]:
    captured = {}

    class Completions:
        async def create(self, **kwargs):
            captured.update(kwargs)
            content = json.dumps(payload, ensure_ascii=False)
            return SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content=content))])

    client = SimpleNamespace(chat=SimpleNamespace(completions=Completions()))
    monkeypatch.setattr("gas_forecast_agent.skills.semantics.build_llm_client", lambda settings: client)
    result = asyncio.run(RequestSemanticExtractor(model_settings()).extract(
        message,
        pending_action=pending_action,
        available_skills=SKILLS,
        conversation_context=conversation_context,
    ))
    return result, captured


def request_payload(
    *,
    intent: str = "QUERY",
    target_selection: str = "UNSPECIFIED",
    customers: list[str] | None = None,
    regions: list[str] | None = None,
    industries: list[str] | None = None,
    references: dict | None = None,
    parameters: dict | None = None,
) -> dict:
    return {
        "dialogueAct": "NEW_REQUEST",
        "requests": [{
            "requestId": "R1",
            "intent": intent,
            "scenario": None,
            "targetSelection": target_selection,
            "entities": {
                "regions": regions or [],
                "industries": industries or [],
                "customers": customers or [],
            },
            "references": references or {},
            "parameters": parameters or {},
            "dependsOn": [],
        }],
    }


def test_new_contract_serializes_as_request_and_entity_arrays() -> None:
    semantics = RequestSemantics.model_validate(request_payload(
        target_selection="EXPLICIT",
        customers=["高邮安源", "扬州中燃"],
    ))

    assert semantics.model_dump(mode="json") == {
        "dialogueAct": "NEW_REQUEST",
        "requests": [{
            "requestId": "R1",
            "intent": "QUERY",
            "scenario": None,
            "targetSelection": "EXPLICIT",
            "entities": {
                "regions": [],
                "industries": [],
                "customers": ["高邮安源", "扬州中燃"],
            },
            "references": {},
            "parameters": {},
            "dependsOn": [],
        }],
    }


def test_plain_greeting_is_deterministically_classified_as_chat(monkeypatch) -> None:
    result, captured = extract(
        monkeypatch,
        "你好",
        request_payload(intent="FORECAST"),
        pending_action={"tool": "forecast_start"},
    )

    assert result.intent == SkillIntent.CHAT
    assert captured == {}


def test_greeting_with_business_goal_is_not_swallowed_by_chat_guard(monkeypatch) -> None:
    result, _ = extract(
        monkeypatch,
        "你好，帮我预测高邮安源未来30天用气量",
        request_payload(
            intent="FORECAST",
            customers=["高邮安源"],
            parameters={"forecastHorizon": 30},
        ),
    )

    assert result.intent == SkillIntent.FORECAST


def test_unknown_intent_is_preserved_instead_of_guessing_business_action(monkeypatch) -> None:
    result, _ = extract(
        monkeypatch,
        "做一下那个东西",
        request_payload(intent="UNKNOWN"),
    )

    assert result.intent == SkillIntent.UNKNOWN


def test_entity_arrays_clean_suffixes_and_remove_duplicates() -> None:
    entities = RequestEntities(
        regions=["江苏省", "江苏", "浙江省"],
        industries=["城市燃气行业", "城市燃气"],
        customers=["高邮安源", "高邮安源", "扬州 中燃"],
    )

    assert entities.regions == ["江苏", "浙江"]
    assert entities.industries == ["城市燃气"]
    assert entities.customers == ["高邮安源", "扬州中燃"]


def test_model_clarification_decision_is_discarded_from_recognition_result() -> None:
    payload = request_payload()
    payload["needsClarification"] = True
    payload["clarificationQuestion"] = "模型不应在这一层追问"
    payload["requests"][0]["needsClarification"] = True

    semantics = RequestSemantics.model_validate(payload)

    serialized = semantics.model_dump(mode="json")
    assert "needsClarification" not in serialized
    assert "clarificationQuestion" not in serialized
    assert "needsClarification" not in serialized["requests"][0]


def test_entity_objects_discard_internal_codes() -> None:
    payload = request_payload(customers=[])
    payload["requests"][0]["entities"]["customers"] = [
        {"name": "高邮安源", "code": "MODEL-INVENTED-CODE"},
    ]

    semantics = RequestSemantics.model_validate(payload)

    assert semantics.customer_names == ["高邮安源"]
    assert "MODEL-INVENTED-CODE" not in json.dumps(semantics.model_dump(mode="json"), ensure_ascii=False)


def test_parameter_codes_are_removed() -> None:
    payload = request_payload(
        customers=["高邮安源"],
        parameters={
            "customerCode": "MODEL-INVENTED",
            "region_code": "MODEL-INVENTED",
            "forecastHorizon": 30,
        },
    )
    payload["requests"][0]["customerCode"] = "MODEL-INVENTED"

    semantics = RequestSemantics.model_validate(payload)

    assert semantics.primary_request.parameters == {"forecastHorizon": 30}
    assert "customerCode" not in semantics.model_dump(mode="json")["requests"][0]


def test_confirmation_dialogue_act_requires_real_pending_action(monkeypatch) -> None:
    payload = request_payload()
    payload["dialogueAct"] = "CONFIRM_PENDING"

    accepted, _ = extract(
        monkeypatch,
        "确认",
        payload,
        pending_action={"tool": "forecast_start", "parameters": {}},
    )
    rejected, _ = extract(monkeypatch, "确认", payload)

    assert accepted.dialogue_act == DialogueAct.CONFIRM_PENDING
    assert rejected.dialogue_act == DialogueAct.UNSPECIFIED


def test_multiple_explicit_customers_are_preserved(monkeypatch) -> None:
    result, _ = extract(
        monkeypatch,
        "查询高邮安源和扬州中燃的最新训练结果",
        request_payload(
            target_selection="EXPLICIT",
            customers=["高邮安源", "扬州中燃"],
        ),
    )

    assert result.customer_names == ["高邮安源", "扬州中燃"]
    assert result.target_selection == TargetSelection.EXPLICIT
    assert result.parameter_overrides()["customerNames"] == ["高邮安源", "扬州中燃"]
    assert result.parameter_overrides()["bulk"] is True
    assert "customerName" not in result.parameter_overrides()


def test_multiple_regions_and_industry_form_all_matched_scope(monkeypatch) -> None:
    result, _ = extract(
        monkeypatch,
        "查询江苏、浙江所有城燃客户",
        request_payload(
            target_selection="ALL_MATCHED",
            regions=["江苏", "浙江"],
            industries=["城燃"],
        ),
    )

    assert result.region_names == ["江苏", "浙江"]
    assert result.industry_names == ["城燃"]
    assert result.target_selection == TargetSelection.ALL_MATCHED
    assert result.parameter_overrides()["fetchAll"] is True


def test_context_reference_is_not_treated_as_entity(monkeypatch) -> None:
    result, _ = extract(
        monkeypatch,
        "查询这些客户的训练结果",
        request_payload(
            target_selection="CONTEXT",
            references={"customers": "LAST_RESULT"},
        ),
    )

    assert result.customer_names == []
    assert result.target_selection == TargetSelection.CONTEXT
    assert result.primary_request.references == {"customers": "LAST_RESULT"}


def test_hallucinated_entity_name_is_removed(monkeypatch) -> None:
    result, _ = extract(
        monkeypatch,
        "查询当前客户的最新训练结果",
        request_payload(
            target_selection="CONTEXT",
            customers=["高邮安源"],
            references={"customer": "CURRENT"},
        ),
    )

    assert result.customer_names == []
    assert result.target_selection == TargetSelection.CONTEXT


def test_force_retrain_is_an_explicit_parameter_not_a_clarification(monkeypatch) -> None:
    result, captured = extract(
        monkeypatch,
        "重新训练高邮安源",
        request_payload(
            intent="TRAIN",
            target_selection="EXPLICIT",
            customers=["高邮安源"],
            parameters={"trainingPolicy": "FORCE_RETRAIN"},
        ),
    )

    assert result.training_policy == TrainingPolicy.FORCE_RETRAIN
    assert result.parameter_overrides()["forceRetrain"] is True
    prompt = captured["messages"][0]["content"]
    assert "不判断是否需要澄清" in prompt
    assert "禁止出现needsClarification" in prompt
    assert captured["response_format"] == {"type": "json_object"}


def test_unverifiable_training_policy_is_reset_without_asking_question(monkeypatch) -> None:
    result, _ = extract(
        monkeypatch,
        "预测当前客户未来30天",
        request_payload(
            intent="FORECAST",
            target_selection="CONTEXT",
            references={"customer": "CURRENT"},
            parameters={"forecastHorizon": 30, "trainingPolicy": "FORCE_RETRAIN"},
        ),
    )

    assert result.training_policy == TrainingPolicy.UNSPECIFIED
    assert "needsClarification" not in result.model_dump(mode="json")


def test_conversation_context_is_forwarded_for_follow_up_understanding(monkeypatch) -> None:
    context = {
        "summary": "用户刚查询了高邮安源。",
        "recentMessages": [{"role": "assistant", "content": "已找到该客户。"}],
        "memory": {"currentSkill": "business-data-query"},
        "pageContext": {"customerName": "高邮安源"},
        "historyReferences": [{"turnId": "TURN-1", "references": []}],
    }
    result, captured = extract(
        monkeypatch,
        "再预测15天",
        request_payload(
            intent="FORECAST",
            target_selection="CONTEXT",
            references={"customer": "CURRENT"},
            parameters={"forecastHorizon": 15},
        ),
        conversation_context=context,
    )

    user_payload = json.loads(captured["messages"][1]["content"])
    assert user_payload["conversationContext"] == context
    assert result.intent == SkillIntent.FORECAST
    assert result.forecast_horizon == 15
    assert result.target_selection == TargetSelection.CONTEXT
    assert "承接表达" in captured["messages"][0]["content"]


def test_region_or_industry_does_not_implicitly_mean_all_matched(monkeypatch) -> None:
    result, _ = extract(
        monkeypatch,
        "查询江苏城燃客户",
        request_payload(
            target_selection="UNSPECIFIED",
            regions=["江苏"],
            industries=["城燃"],
        ),
    )

    assert result.region_names == ["江苏"]
    assert result.industry_names == ["城燃"]
    assert result.target_selection == TargetSelection.UNSPECIFIED
    assert "fetchAll" not in result.parameter_overrides()
    assert "bulk" not in result.parameter_overrides()


def test_colloquial_customer_mention_is_preserved(monkeypatch) -> None:
    result, _ = extract(
        monkeypatch,
        "查一下高邮的安源最新训练结果",
        request_payload(
            customers=["高邮安源燃气有限公司"],
        ),
    )

    assert result.customer_names == ["高邮安源燃气有限公司"]


def test_model_failure_is_not_returned_as_empty_semantics(monkeypatch) -> None:
    class Completions:
        async def create(self, **kwargs):
            raise TimeoutError("model timeout")

    client = SimpleNamespace(chat=SimpleNamespace(completions=Completions()))
    monkeypatch.setattr("gas_forecast_agent.skills.semantics.build_llm_client", lambda settings: client)

    with pytest.raises(SemanticExtractionError, match="调用或结果解析失败"):
        asyncio.run(RequestSemanticExtractor(model_settings()).extract(
            "预测高邮安源未来15天",
            available_skills=SKILLS,
        ))


def test_empty_model_result_is_a_recognition_failure(monkeypatch) -> None:
    class Completions:
        async def create(self, **kwargs):
            return SimpleNamespace(
                choices=[SimpleNamespace(message=SimpleNamespace(content="{}"))]
            )

    client = SimpleNamespace(chat=SimpleNamespace(completions=Completions()))
    monkeypatch.setattr("gas_forecast_agent.skills.semantics.build_llm_client", lambda settings: client)

    with pytest.raises(SemanticExtractionError, match="没有返回任何请求"):
        asyncio.run(RequestSemanticExtractor(model_settings()).extract(
            "预测高邮安源未来15天",
            available_skills=SKILLS,
        ))


def test_multiple_requests_and_dependencies_are_retained(monkeypatch) -> None:
    payload = {
        "dialogueAct": "NEW_REQUEST",
        "requests": [
            {
                "requestId": "R1",
                "intent": "TRAIN",
                "scenario": None,
                "targetSelection": "EXPLICIT",
                "entities": {"regions": [], "industries": [], "customers": ["高邮安源"]},
                "references": {},
                "parameters": {"trainingPolicy": "FORCE_RETRAIN"},
                "dependsOn": [],
            },
            {
                "requestId": "R2",
                "intent": "FORECAST",
                "scenario": "short-term",
                "targetSelection": "EXPLICIT",
                "entities": {"regions": [], "industries": [], "customers": ["高邮安源"]},
                "references": {"trainingResult": "R1"},
                "parameters": {"forecastHorizon": 30},
                "dependsOn": ["R1", "UNKNOWN", "R2"],
            },
        ],
    }

    result, _ = extract(monkeypatch, "重新训练高邮安源，然后短期预测未来30天", payload)

    assert [request.request_id for request in result.requests] == ["R1", "R2"]
    assert result.requests[1].depends_on == ["R1"]
    assert result.requests[1].scenario.value == "short-term"


def test_legacy_flat_payload_is_upgraded_but_serializes_only_new_contract() -> None:
    semantics = RequestSemantics(
        intent=SkillIntent.QUERY,
        customer_name="高邮安源",
        forecast_horizon=30,
        needs_clarification=True,
        clarification_question="旧字段不应保留",
    )

    serialized = semantics.model_dump(mode="json")
    assert serialized["requests"][0]["entities"]["customers"] == ["高邮安源"]
    assert serialized["requests"][0]["parameters"] == {"forecastHorizon": 30}
    assert "needsClarification" not in serialized
    assert "clarificationQuestion" not in serialized
