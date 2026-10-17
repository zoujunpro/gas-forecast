import asyncio

from gas_forecast_agent.capabilities import CapabilityRegistry
from gas_forecast_agent.graph.main_graph import AgentMainFlow
from gas_forecast_agent.intent import RecognizedIntent, SpeechAct


class FakeMcp:
    def __init__(self, actual_type: str, agent_code: str = "short-term", found: bool = True):
        self.actual_type = actual_type
        self.agent_code = agent_code
        self.found = found

    async def call_tool(self, name, arguments=None):
        assert name == "business_reference_resolve"
        return {
            "structuredContent": {
                "success": True,
                "data": {"found": self.found, "actualType": self.actual_type, "agentCode": self.agent_code},
            },
        }


class Runtime:
    def __init__(self, mcp):
        self.context = type("Context", (), {"mcp": mcp, "capabilities": CapabilityRegistry()})()


def intent(code: str, domain: str, action: str, parameters: dict | None = None) -> dict:
    return RecognizedIntent(
        capability_code=code, domain=domain, action=action, resource="BATCH", scenario="SHORT_TERM",
        parameters=parameters or {}, confidence=0.9, explanation="模型识别", speech_act=SpeechAct.QUESTION,
    ).model_dump(mode="json")


def validate(message: str, recognized: dict, mcp: FakeMcp) -> RecognizedIntent:
    result = asyncio.run(AgentMainFlow()._validate_reference(
        {"message": message, "intent": recognized}, Runtime(mcp),
    ))
    return RecognizedIntent.model_validate(result["intent"])


def test_training_batch_corrects_forecast_result_model_error() -> None:
    result = validate(
        "查询训练批次 TRBATCH-20261016-00714 的基本信息和回测效果",
        intent("SHORT_TERM_FORECAST_RESULT", "FORECAST", "RESULT", {"forecastBatchNo": "TRBATCH-20261016-00714"}),
        FakeMcp("TRAINING_BATCH"),
    )
    assert result.capability_code == "SHORT_TERM_TRAINING_RESULT"
    assert result.parameters == {"trainBatchNo": "TRBATCH-20261016-00714"}


def test_backtest_words_correct_model_status_action_to_training_result() -> None:
    result = validate(
        "查询训练批次 TRBATCH-20261016-00714 的基本信息和回测效果",
        intent("TASK_STATUS", "TASK", "STATUS", {"taskNo": "TRBATCH-20261016-00714"}),
        FakeMcp("TRAINING_BATCH"),
    )
    assert result.capability_code == "SHORT_TERM_TRAINING_RESULT"
    assert result.action == "RESULT"


def test_training_batch_corrects_generic_task_status_model_error() -> None:
    result = validate(
        "查询训练批次 TRBATCH-20261016-00714 当前状态",
        intent("TASK_STATUS", "TASK", "STATUS", {"taskNo": "TRBATCH-20261016-00714"}),
        FakeMcp("TRAINING_BATCH"),
    )
    assert result.capability_code == "SHORT_TERM_TRAINING_STATUS"
    assert result.parameters == {"trainBatchNo": "TRBATCH-20261016-00714"}


def test_unknown_reference_stops_before_business_query() -> None:
    result = validate(
        "查询预测批次 FORECAST-20990101-999999 的结果",
        intent("SHORT_TERM_FORECAST_RESULT", "FORECAST", "RESULT", {"forecastBatchNo": "FORECAST-20990101-999999"}),
        FakeMcp("UNKNOWN", found=False),
    )
    assert result.status.value == "AMBIGUOUS"
    assert "没有查询到编号" in (result.clarification_question or "")
