import asyncio
from typing import Any

from gas_forecast_agent.execution import ExceptionDecision, ExecutionMonitorGraph, validate_decision


class NoCallMcp:
    async def call_tool(self, name: str, arguments: dict[str, Any]) -> dict[str, Any]:
        raise AssertionError("已有工具结果时不应再次调用MCP")


def run(result: dict[str, Any], poll_count: int = 0) -> dict[str, Any]:
    return asyncio.run(ExecutionMonitorGraph().invoke(
        {"conversation_no": "C-1", "tool_result": result, "poll_count": poll_count}, mcp=NoCallMcp()
    ))


def test_running_task_is_scheduled_with_backoff() -> None:
    state = run({"success": True, "status": "EXECUTING", "message": "运行中", "data": {
        "status": "EXECUTING", "nextAction": "WAIT", "allowedActions": ["WAIT", "QUERY_STATUS"]
    }}, poll_count=2)
    assert state["next_action"] == "WAIT"
    assert state["next_check_seconds"] == 30


def test_failed_task_without_decider_requires_user() -> None:
    state = run({"success": False, "status": "FAILED", "message": "数据不足", "data": {
        "status": "FAILED", "reason": "只有20天数据", "nextAction": "MODEL_DECISION",
        "allowedActions": ["QUERY_STEPS", "RETRY", "ASK_USER", "FAIL"]
    }})
    assert state["next_action"] == "ASK_USER"
    assert "人工确认" in state["user_message"]


def test_model_cannot_escape_server_action_whitelist() -> None:
    decision = validate_decision(ExceptionDecision(action="RETRY", reason="重试", confidence=0.8), ["ASK_USER", "FAIL"])
    assert decision.action == "ASK_USER"


def test_direct_retry_is_changed_to_user_confirmation() -> None:
    state = run({"success": False, "status": "FAILED", "message": "失败", "data": {
        "status": "FAILED", "nextAction": "RETRY", "allowedActions": ["RETRY", "ASK_USER"]
    }})
    assert state["next_action"] == "ASK_USER"
