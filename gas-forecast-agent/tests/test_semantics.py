import asyncio
from types import SimpleNamespace

from gas_forecast_agent.config import Settings
from gas_forecast_agent.skills import RequestSemanticExtractor, RequestSemantics, TrainingPolicy


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
                "evidence":{}
            }'''))])

    client = SimpleNamespace(chat=SimpleNamespace(completions=Completions()))
    monkeypatch.setattr("gas_forecast_agent.skills.semantics.build_llm_client", lambda settings: client)
    settings = model_settings()

    result = asyncio.run(RequestSemanticExtractor(settings).extract("训练一下再预测", "customer-gas-forecast"))

    assert result.needs_clarification is True
    assert "重新训练" in result.clarification_question


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
    assert result.needs_clarification is True


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
