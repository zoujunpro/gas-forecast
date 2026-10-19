import hashlib
from typing import Any

from fastapi.testclient import TestClient

from gas_forecast_agent.config import Settings, get_settings
from gas_forecast_agent.main import _persistent_parameters, app, get_mcp_client
from gas_forecast_agent.mcp import McpClientError
from gas_forecast_agent.skills import DialogueAct, RequestSemantics


TOOLS = [
    "region_list", "industry_list", "region_search", "industry_search", "customer_list", "customer_search",
    "task_status_get", "task_steps_get", "forecast_start", "forecast_status_get", "forecast_result_get",
    "forecast_latest_get", "forecast_history_get", "training_start", "training_status_get", "training_result_get",
    "training_latest_get", "training_history_get", "task_resume", "task_retry",
]


class FakeMcpClient:
    def __init__(self) -> None:
        self.memory: dict[str, Any] = {}
        self.calls: list[tuple[str, dict[str, Any]]] = []

    async def list_tools(self) -> list[dict[str, Any]]:
        return [
            {
                "name": name,
                "description": name,
                "inputSchema": {
                    "type": "object",
                    "required": ["taskNo", "confirmed"] if name in {"task_resume", "task_retry"} else [],
                    "properties": {
                        "conversationNo": {"type": "string"},
                        "customerCode": {"type": "string"},
                        "taskNo": {"type": "string"},
                        "confirmed": {"type": "boolean"},
                        "idempotencyKey": {"type": "string"},
                    },
                },
                "annotations": {
                    "readOnlyHint": name not in {"forecast_start", "training_start", "task_resume", "task_retry"},
                    "destructiveHint": name in {"forecast_start", "training_start"},
                },
            }
            for name in TOOLS
        ] + [{"name": "admin_delete", "description": "禁止工具", "inputSchema": {}}]

    async def call_tool(self, name: str, arguments: dict[str, Any]) -> dict[str, Any]:
        self.calls.append((name, arguments))
        if name == "memory_get":
            data = dict(self.memory)
        elif name == "conversation_prepare":
            metadata = dict(arguments.get("metadata") or {})
            metadata["turnId"] = arguments["turnId"]
            data = {
                "conversationNo": arguments.get("conversationNo") or "C-NEW",
                "history": [],
                "metadata": metadata,
            }
        elif name == "conversation_reply_append":
            data = {
                "conversationNo": arguments["conversationNo"],
                "lastTrainBatchNo": None,
                "lastForecastBatchNo": "FORECAST-1",
            }
        elif name == "memory_update":
            self.memory.update(arguments["values"])
            data = dict(self.memory)
        elif name == "forecast_start":
            data = {"taskNo": "TASK-1", "forecastBatchNo": "FORECAST-1"}
        elif name == "customer_list":
            data = {"records": [{"customerCode": "C-1", "customerName": "杭州燃气"}], "total": 1}
        elif name == "region_search":
            data = {"status": "UNIQUE", "candidates": [{
                "type": "REGION", "code": "330000", "name": "浙江",
                "regionCode": "330000", "regionName": "浙江",
            }]}
        elif name == "industry_search":
            data = {"status": "UNIQUE", "candidates": [{
                "type": "INDUSTRY", "code": "CITY_GAS", "name": "城燃",
                "industryCode": "CITY_GAS", "industryName": "城燃",
            }]}
        else:
            data = {"tool": name, "arguments": arguments}
        return {
            "isError": False,
            "structuredContent": {
                "success": True,
                "status": "EXECUTING" if name == "forecast_start" else "COMPLETED",
                "message": "预测任务已创建" if name == "forecast_start" else "查询完成",
                "retryable": False,
                "data": data,
            },
        }


class FailingPrepareMcpClient(FakeMcpClient):
    async def call_tool(self, name: str, arguments: dict[str, Any]) -> dict[str, Any]:
        if name == "conversation_prepare":
            raise McpClientError("unauthorized", 401)
        return await super().call_tool(name, arguments)


class RetryReplyMcpClient(FakeMcpClient):
    reply_attempts = 0

    async def call_tool(self, name: str, arguments: dict[str, Any]) -> dict[str, Any]:
        if name == "conversation_reply_append":
            self.reply_attempts += 1
            if self.reply_attempts < 3:
                self.calls.append((name, arguments))
                raise McpClientError("temporary failure", 503)
        return await super().call_tool(name, arguments)


def rule_settings() -> Settings:
    return Settings(GAS_SKILL_ROUTING_MODE="rules", LLM_API_KEY=None)


def test_skill_endpoint_exposes_skill_tool_allowlists() -> None:
    response = TestClient(app).get("/api/v1/skills")

    assert response.status_code == 200
    body = response.json()
    assert body["total"] == 3
    forecast = next(item for item in body["skills"] if item["name"] == "customer-gas-forecast")
    assert "forecast_start" in forecast["allowed_tools"]


def test_only_business_parameters_are_persisted_between_turns() -> None:
    assert _persistent_parameters({
        "customerCode": "CUS-1",
        "forecastHorizon": 30,
        "confirmed": True,
        "turnId": "TURN-1",
        "_clarificationQuestion": "旧问题",
        "_lockedParameters": ["customerCode"],
        "_pendingTool": "forecast_start",
    }) == {"customerCode": "CUS-1", "forecastHorizon": 30}


def test_forecast_uses_skill_allowed_tool_and_stops_after_async_start() -> None:
    fake = FakeMcpClient()
    app.dependency_overrides[get_mcp_client] = lambda: fake
    app.dependency_overrides[get_settings] = rule_settings
    try:
        response = TestClient(app).post(
            "/api/v1/chat",
            json={"turnId": "TURN-1", "conversationNo": "C-1", "message": "预测当前客户未来30天用气量", "metadata": {"customerCode": "CUS-1"}},
        )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200
    body = response.json()["data"]
    assert body["skillName"] == "customer-gas-forecast"
    assert body["skillSelection"]["skill_name"] == "customer-gas-forecast"
    assert "intent" not in body
    assert body["executedTool"] == "forecast_start"
    assert body["status"] == "EXECUTING"
    assert [name for name, _ in fake.calls].count("forecast_start") == 1
    start_arguments = next(arguments for name, arguments in fake.calls if name == "forecast_start")
    assert start_arguments["conversationNo"] == "C-1"
    assert len(start_arguments["idempotencyKey"]) == 32
    assert start_arguments["idempotencyKey"] == hashlib.sha256(b"TURN-1").hexdigest()[:32]
    assert start_arguments["idempotencyKey"] == body["parameters"]["idempotencyKey"]
    assert "admin_delete" not in body["availableTools"]


def test_browser_chat_owns_turn_and_persists_through_java_tools() -> None:
    fake = FakeMcpClient()
    app.dependency_overrides[get_mcp_client] = lambda: fake
    app.dependency_overrides[get_settings] = rule_settings
    try:
        response = TestClient(app).post(
            "/api/v1/chat",
            json={"turnId": "TURN-NEW", "message": "预测当前客户未来30天用气量", "metadata": {"customerCode": "CUS-1"}},
        )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200
    body = response.json()
    assert body["code"] == "0000"
    assert body["data"]["conversationNo"] == "C-NEW"
    assert body["data"]["status"] == "EXECUTING"
    assert body["data"]["forecastBatchNo"] == "FORECAST-1"
    names = [name for name, _ in fake.calls]
    assert names[0] == "conversation_prepare"
    assert names[-1] == "conversation_reply_append"
    assert names.count("forecast_start") == 1


def test_old_agent_http_entry_is_not_exposed() -> None:
    response = TestClient(app).post("/api/v1/conversations/C-1/messages", json={"message": "查询客户"})
    assert response.status_code == 404


def test_auth_failure_from_java_is_preserved() -> None:
    app.dependency_overrides[get_mcp_client] = lambda: FailingPrepareMcpClient()
    app.dependency_overrides[get_settings] = rule_settings
    try:
        response = TestClient(app).post("/api/v1/chat", json={"turnId": "TURN-AUTH", "message": "查询客户"})
    finally:
        app.dependency_overrides.clear()
    assert response.status_code == 401
    assert response.json()["code"] == "401"


def test_reply_archive_retries_with_same_turn_id() -> None:
    fake = RetryReplyMcpClient()
    app.dependency_overrides[get_mcp_client] = lambda: fake
    app.dependency_overrides[get_settings] = rule_settings
    try:
        response = TestClient(app).post(
            "/api/v1/chat", json={"turnId": "TURN-RETRY", "conversationNo": "C-4", "message": "查询客户"}
        )
    finally:
        app.dependency_overrides.clear()
    assert response.status_code == 200
    assert fake.reply_attempts == 3
    reply_calls = [arguments for name, arguments in fake.calls if name == "conversation_reply_append"]
    assert {arguments["turnId"] for arguments in reply_calls} == {"TURN-RETRY"}


def test_customer_query_uses_read_only_skill() -> None:
    fake = FakeMcpClient()
    app.dependency_overrides[get_mcp_client] = lambda: fake
    app.dependency_overrides[get_settings] = rule_settings
    try:
        response = TestClient(app).post(
            "/api/v1/chat",
            json={"turnId": "TURN-2", "conversationNo": "C-2", "message": "查询浙江城市燃气客户"},
        )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200
    body = response.json()["data"]
    assert body["skillName"] == "business-data-query"
    assert body["executedTool"] == "customer_list"
    assert "industryName" not in body["parameters"]
    assert "regionName" not in body["parameters"]
    assert [name for name, _ in fake.calls if name == "customer_list"] == ["customer_list"]


def test_task_retry_requires_confirmation_before_tool_call(monkeypatch) -> None:
    fake = FakeMcpClient()
    app.dependency_overrides[get_mcp_client] = lambda: fake
    app.dependency_overrides[get_settings] = rule_settings
    try:
        response = TestClient(app).post(
            "/api/v1/chat",
            json={"turnId": "TURN-3A", "conversationNo": "C-3", "message": "重试任务 TASK-123"},
        )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200
    assert response.json()["data"]["status"] == "NEED_CONFIRMATION"
    assert all(name != "task_retry" for name, _ in fake.calls)

    async def confirm_pending(self, message, skill_name, pending_action=None):
        return RequestSemantics(dialogue_act=DialogueAct.CONFIRM_PENDING)

    monkeypatch.setattr(
        "gas_forecast_agent.main.RequestSemanticExtractor.extract", confirm_pending,
    )

    app.dependency_overrides[get_mcp_client] = lambda: fake
    app.dependency_overrides[get_settings] = rule_settings
    try:
        confirmed = TestClient(app).post(
            "/api/v1/chat",
            json={"turnId": "TURN-3B", "conversationNo": "C-3", "message": "确认"},
        )
    finally:
        app.dependency_overrides.clear()

    assert confirmed.status_code == 200
    assert confirmed.json()["data"]["skillName"] == "model-training-management"
    assert confirmed.json()["data"]["executedTool"] == "task_retry"
    assert [name for name, _ in fake.calls].count("task_retry") == 1
