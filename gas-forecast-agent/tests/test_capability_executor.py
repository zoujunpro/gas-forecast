import asyncio
from typing import Any

from gas_forecast_agent.capabilities import CapabilityRegistry
from gas_forecast_agent.execution import CapabilityExecutor


def test_forecast_start_maps_training_preference_and_calls_only_forecast() -> None:
    calls: list[tuple[str, dict[str, Any]]] = []

    async def call(name: str, arguments: dict[str, Any]) -> dict[str, Any]:
        calls.append((name, arguments))
        return {"status": "WAITING_TRAINING", "message": "先训练", "data": {"trainBatchNo": "T-1"}}

    capability = CapabilityRegistry().get("SHORT_TERM_FORECAST_START")
    result = asyncio.run(CapabilityExecutor().execute(capability, {
        "conversationNo": "C-1", "customerCode": "CUS-1", "agentCode": "short-term",
        "trainingPreference": "FORCE_RETRAIN", "irrelevant": "drop-me",
    }, call))

    assert result.tool_name == "forecast_start"
    assert [name for name, _ in calls] == ["forecast_start"]
    assert calls[0][1]["forceRetrain"] is True
    assert calls[0][1]["autoTrain"] is True
    assert calls[0][1]["trainingPreference"] == "FORCE_RETRAIN"
    assert "irrelevant" not in calls[0][1]


def test_unspecified_training_preference_does_not_authorize_training() -> None:
    calls: list[dict[str, Any]] = []

    async def call(name: str, arguments: dict[str, Any]) -> dict[str, Any]:
        calls.append(arguments)
        return {"status": "NEED_CONFIRMATION", "data": {"errorCode": "TRAINING_REQUIRED"}}

    capability = CapabilityRegistry().get("SHORT_TERM_FORECAST_START")
    asyncio.run(CapabilityExecutor().execute(capability, {
        "conversationNo": "C-1", "customerCode": "CUS-1", "trainingPreference": "UNSPECIFIED",
    }, call))

    assert calls[0]["trainingPreference"] == "UNSPECIFIED"
    assert calls[0]["autoTrain"] is False
    assert calls[0]["forceRetrain"] is False


def test_query_capability_uses_final_query_tool() -> None:
    async def call(name: str, arguments: dict[str, Any]) -> dict[str, Any]:
        return {"status": "COMPLETED", "data": {"name": name, "arguments": arguments}}

    capability = CapabilityRegistry().get("CUSTOMER_LIST")
    result = asyncio.run(CapabilityExecutor().execute(capability, {
        "customerName": "燃气", "regionCode": "ZJ", "conversationNo": "not-supported",
    }, call))

    assert result.tool_name == "customer_list"
    assert result.arguments == {"keyword": "燃气", "regionCode": "ZJ"}


def test_fetch_all_is_one_tool_call_when_java_supports_it() -> None:
    calls: list[dict[str, Any]] = []

    async def call(name: str, arguments: dict[str, Any]) -> dict[str, Any]:
        calls.append(arguments)
        records = [{"customerCode": f"C-{index}", "customerName": f"客户{index}"} for index in range(154)]
        return {
            "success": True, "status": "COMPLETED", "resultType": "PAGE", "message": "客户列表查询成功",
            "data": {"records": records, "total": 154, "fetchedCount": 154, "allFetched": True},
        }

    capability = CapabilityRegistry().get("CUSTOMER_LIST")
    result = asyncio.run(CapabilityExecutor().execute(capability, {"fetchAll": True}, call))

    assert len(calls) == 1
    assert calls[0]["fetchAll"] is True
    assert len(result.result["data"]["records"]) == 154
