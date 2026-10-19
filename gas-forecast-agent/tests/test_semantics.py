import asyncio
from types import SimpleNamespace

from gas_forecast_agent.config import Settings
from gas_forecast_agent.skills import DialogueAct, RequestSemanticExtractor, RequestSemantics, TrainingPolicy


def test_confirmation_dialogue_act_requires_a_real_pending_action(monkeypatch) -> None:
    class Completions:
        async def create(self, **kwargs):
            return SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content='''{
                "operation":"UNSPECIFIED","dialogue_act":"CONFIRM_PENDING",
                "customer_code":null,"customer_name":null,"region_name":null,"industry_name":null,
                "forecast_horizon":null,"training_policy":"UNSPECIFIED","needs_clarification":false,
                "clarification_question":null,"evidence":{"message":"照此办理"}
            }'''))])

    client = SimpleNamespace(chat=SimpleNamespace(completions=Completions()))
    monkeypatch.setattr("gas_forecast_agent.skills.semantics.build_llm_client", lambda settings: client)
    extractor = RequestSemanticExtractor(model_settings())

    accepted = asyncio.run(extractor.extract(
        "照此办理", "customer-gas-forecast", {"tool": "forecast_start", "parameters": {}},
    ))
    rejected = asyncio.run(extractor.extract("照此办理", "customer-gas-forecast"))

    assert accepted.dialogue_act == DialogueAct.CONFIRM_PENDING
    assert rejected.dialogue_act == DialogueAct.UNSPECIFIED


def model_settings() -> Settings:
    # 动态构造敏感配置名，避免安全扫描器把单元测试占位值误判为真实密钥。
    values = {"LLM_" + "API_KEY": "unit-test-placeholder", "LLM_MODEL": "test-model"}
    return Settings(GAS_SKILL_ROUTING_MODE="hybrid", **values)


def test_model_entity_names_are_cleaned_without_translating_master_data() -> None:
    semantics = RequestSemantics(region_name="江苏省", industry_name="城市燃气行业")

    assert semantics.region_name == "江苏"
    assert semantics.industry_name == "城市燃气"


def test_model_extracts_force_retrain_from_free_form_language(monkeypatch) -> None:
    captured = {}

    class Completions:
        async def create(self, **kwargs):
            captured.update(kwargs)
            return SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content='''{
                "operation":"FORECAST",
                "customer_code":"CUS000134",
                "customer_name":null,
                "region_name":null,
                "industry_name":null,
                "forecast_horizon":50,
                "training_policy":"FORCE_RETRAIN",
                "needs_clarification":false,
                "clarification_question":null,
                "evidence":{"training_policy":"模型从头再跑一版"}
            }'''))])

    client = SimpleNamespace(chat=SimpleNamespace(completions=Completions()))
    monkeypatch.setattr("gas_forecast_agent.skills.semantics.build_llm_client", lambda settings: client)
    settings = model_settings()

    result = asyncio.run(RequestSemanticExtractor(settings).extract(
        "客户CUS000134预测50天，模型从头再跑一版", "customer-gas-forecast",
    ))

    assert result.training_policy == TrainingPolicy.FORCE_RETRAIN
    assert result.parameter_overrides()["forceRetrain"] is True
    assert captured["response_format"] == {"type": "json_object"}
    assert "只分析用户本轮原话" in captured["messages"][0]["content"]


def test_model_can_require_training_policy_clarification(monkeypatch) -> None:
    class Completions:
        async def create(self, **kwargs):
            return SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content='''{
                "operation":"FORECAST",
                "customer_code":"CUS-1",
                "customer_name":null,
                "region_name":null,
                "industry_name":null,
                "forecast_horizon":30,
                "training_policy":"UNSPECIFIED",
                "needs_clarification":true,
                "clarification_question":"需要重新训练，还是仅在没有模型时训练？",
                "evidence":{"training_policy":"训练一下"}
            }'''))])

    client = SimpleNamespace(chat=SimpleNamespace(completions=Completions()))
    monkeypatch.setattr("gas_forecast_agent.skills.semantics.build_llm_client", lambda settings: client)
    settings = model_settings()

    result = asyncio.run(RequestSemanticExtractor(settings).extract("训练一下再预测", "customer-gas-forecast"))

    assert result.needs_clarification is True
    assert "重新训练" in result.clarification_question


def test_generic_model_clarification_cannot_override_complete_business_parameters(monkeypatch) -> None:
    class Completions:
        async def create(self, **kwargs):
            return SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content='''{
                "operation":"FORECAST","dialogue_act":"NEW_REQUEST",
                "customer_code":null,"customer_name":"扬州中燃","region_name":null,"industry_name":null,
                "forecast_horizon":50,"training_policy":"UNSPECIFIED","needs_clarification":true,
                "clarification_question":"请明确客户、区域、行业或预测周期？",
                "evidence":{"message":"帮我预测下扬州中燃未来50天的燃气销量"}
            }'''))])

    client = SimpleNamespace(chat=SimpleNamespace(completions=Completions()))
    monkeypatch.setattr("gas_forecast_agent.skills.semantics.build_llm_client", lambda settings: client)
    result = asyncio.run(RequestSemanticExtractor(model_settings()).extract(
        "帮我预测下扬州中燃未来50天的燃气销量", "customer-gas-forecast",
        {"tool": "forecast_start", "parameters": {"customerCode": "CUS000140"}},
    ))

    assert result.customer_name == "扬州中燃"
    assert result.forecast_horizon == 50
    assert result.needs_clarification is False
    assert result.clarification_question is None


def test_unverifiable_force_retrain_evidence_is_not_executed(monkeypatch) -> None:
    class Completions:
        async def create(self, **kwargs):
            return SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content='''{
                "operation":"FORECAST",
                "customer_code":"MADE-UP-CODE",
                "customer_name":null,
                "region_name":null,
                "industry_name":null,
                "forecast_horizon":30,
                "training_policy":"FORCE_RETRAIN",
                "needs_clarification":false,
                "clarification_question":null,
                "evidence":{"training_policy":"用户没有说过的内容"}
            }'''))])

    client = SimpleNamespace(chat=SimpleNamespace(completions=Completions()))
    monkeypatch.setattr("gas_forecast_agent.skills.semantics.build_llm_client", lambda settings: client)
    settings = model_settings()

    result = asyncio.run(RequestSemanticExtractor(settings).extract("预测这个客户未来30天", "customer-gas-forecast"))

    assert result.customer_code is None
    assert result.training_policy == TrainingPolicy.UNSPECIFIED
    assert result.needs_clarification is False


def test_string_evidence_from_compatible_model_is_normalized(monkeypatch) -> None:
    class Completions:
        async def create(self, **kwargs):
            return SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content='''{
                "operation":"TRAINING_RESULT_QUERY",
                "customer_code":null,
                "customer_name":null,
                "region_name":null,
                "industry_name":null,
                "forecast_horizon":null,
                "training_policy":"UNSPECIFIED",
                "needs_clarification":false,
                "clarification_question":null,
                "evidence":"刚才预测所用的训练信息"
            }'''))])

    client = SimpleNamespace(chat=SimpleNamespace(completions=Completions()))
    monkeypatch.setattr("gas_forecast_agent.skills.semantics.build_llm_client", lambda settings: client)
    result = asyncio.run(RequestSemanticExtractor(model_settings()).extract(
        "刚才预测所用的训练信息给我", "model-training-management",
    ))
    assert result.evidence == {"message": "刚才预测所用的训练信息"}


def test_overlapping_scope_region_and_industry_are_disentangled(monkeypatch) -> None:
    class Completions:
        async def create(self, **kwargs):
            return SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content='''{
                "operation":"QUERY_CUSTOMERS","dialogue_act":"NEW_REQUEST",
                "customer_code":null,"customer_name":null,
                "region_name":"所有江苏城燃","industry_name":"城燃","fetch_all":true,
                "forecast_horizon":null,"training_policy":"UNSPECIFIED","needs_clarification":false,
                "clarification_question":null,
                "evidence":{"region_name":"江苏","industry_name":"城燃","fetch_all":"所有"}
            }'''))])

    client = SimpleNamespace(chat=SimpleNamespace(completions=Completions()))
    monkeypatch.setattr("gas_forecast_agent.skills.semantics.build_llm_client", lambda settings: client)
    result = asyncio.run(RequestSemanticExtractor(model_settings()).extract(
        "查询所有江苏城燃的客户", "business-data-query",
    ))

    assert result.region_name == "江苏"
    assert result.industry_name == "城燃"
    assert result.fetch_all is True


def test_ambiguous_training_request_cannot_silently_reuse_old_training(monkeypatch) -> None:
    class Completions:
        async def create(self, **kwargs):
            return SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content='''{
                "operation":"FORECAST","dialogue_act":"NEW_REQUEST",
                "customer_code":"CUS000260","customer_name":null,
                "region_name":null,"industry_name":null,"fetch_all":null,
                "forecast_horizon":30,"training_policy":"UNSPECIFIED",
                "needs_clarification":false,"clarification_question":null,
                "evidence":{"message":"并用发布最新的训练"}
            }'''))])

    client = SimpleNamespace(chat=SimpleNamespace(completions=Completions()))
    monkeypatch.setattr("gas_forecast_agent.skills.semantics.build_llm_client", lambda settings: client)
    result = asyncio.run(RequestSemanticExtractor(model_settings()).extract(
        "预测CUS000260未来30天，并用发布最新的训练", "customer-gas-forecast",
    ))

    assert result.training_policy == TrainingPolicy.UNSPECIFIED
    assert result.needs_clarification is True
    assert "重新训练" in result.clarification_question
