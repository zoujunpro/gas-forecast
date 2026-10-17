from typing import Any

from fastapi.testclient import TestClient

from gas_forecast_agent.main import app, get_mcp_client
from gas_forecast_agent.conversation import ConversationResponder


class FakeMcpClient:
    def __init__(self) -> None:
        self.memory: dict[str, Any] = {}

    async def list_tools(self) -> list[dict[str, Any]]:
        return [
            {"name": "forecast_start", "description": "发起预测", "inputSchema": {}},
            {"name": "task_status_get", "description": "查询任务", "inputSchema": {}},
            {"name": "unknown_tool", "description": "未授权工具", "inputSchema": {}},
        ]

    async def call_tool(self, name: str, arguments: dict[str, Any]) -> dict[str, Any]:
        if name == "memory_get":
            data = dict(self.memory)
        elif name == "memory_update":
            self.memory.update(arguments["values"])
            data = dict(self.memory)
        elif name == "customer_search":
            data = {
                "status": "UNIQUE",
                "candidates": [{
                    "type": "CUSTOMER",
                    "code": "C-100",
                    "name": "杭州燃气集团",
                    "regionCode": "ZJ",
                    "regionName": "浙江",
                    "industryCode": "CITY_GAS",
                    "industryName": "城燃",
                }],
            }
        elif name == "customer_list":
            data = {"records": [{"customerCode": "C-100", "customerName": "杭州燃气集团"}], "total": 1, "page": 1, "size": 20}
        elif name in {"forecast_latest_get", "forecast_start", "training_start", "region_list", "industry_list"}:
            data = {"tool": name, "arguments": arguments}
        else:
            raise AssertionError(f"unexpected tool: {name}")
        return {
            "isError": False,
            "structuredContent": {
                "success": True,
                "status": "COMPLETED",
                "message": "ok",
                "retryable": False,
                "data": data,
            },
        }


def test_chat_routes_and_filters_mcp_tools() -> None:
    app.dependency_overrides[get_mcp_client] = lambda: FakeMcpClient()
    try:
        response = TestClient(app).post(
            "/api/v1/conversations/C-001/messages",
            json={"message": "预测下个月销量"},
        )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200
    body = response.json()
    assert body["agent_type"] == "monthly-sales"
    assert body["capability_code"] == "MONTHLY_SALES_FORECAST_START"
    assert body["status"] == "CAPABILITY_UNAVAILABLE"
    assert body["memory_loaded"] is True
    assert "forecast_start" in body["available_tools"]


def test_bound_agent_and_scope_override_text_inference() -> None:
    app.dependency_overrides[get_mcp_client] = lambda: FakeMcpClient()
    try:
        response = TestClient(app).post(
            "/api/v1/conversations/C-BOUND/messages",
            json={
                "message": "预测当前客户未来30天销量",
                "agent_type": "short-term",
                "metadata": {
                    "regionCode": "REG-JS",
                    "regionName": "江苏",
                    "industryCode": "IND-CNG",
                    "industryName": "CNG",
                    "customerCode": "CUS-ZJDY",
                    "customerName": "镇江东源",
                    "forecastHorizon": 30,
                },
            },
        )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200
    body = response.json()
    assert body["agent_type"] == "short-term"
    assert body["capability_code"] == "SHORT_TERM_FORECAST_START"
    assert body["parameters"]["customerCode"] == "CUS-ZJDY"
    assert body["parameters"]["customerName"] == "镇江东源"
    assert body["parameters"]["forecastHorizon"] == 30


def test_chat_loads_and_updates_memory_before_routing() -> None:
    fake = FakeMcpClient()
    fake.memory["customerCode"] = "C-100"
    app.dependency_overrides[get_mcp_client] = lambda: fake
    try:
        response = TestClient(app).post(
            "/api/v1/conversations/C-002/messages",
            json={"message": "查询这个客户最新的短期预测"},
        )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200
    body = response.json()
    assert body["capability_code"] == "SHORT_TERM_FORECAST_LATEST"
    assert body["status"] == "COMPLETED"
    assert body["executed_tool"] == "forecast_latest_get"
    assert body["parameters"]["customerCode"] == "C-100"
    assert fake.memory["currentCapability"] == "SHORT_TERM_FORECAST_LATEST"


def test_capability_endpoint_exposes_readiness() -> None:
    response = TestClient(app).get("/api/v1/capabilities")

    assert response.status_code == 200
    body = response.json()
    assert body["total"] == 26
    assert body["available"] == 20
    assert "SHORT_TERM" in body["scenarios"]


def test_greeting_is_answered_by_agent_without_selecting_forecast_agent(monkeypatch) -> None:
    fake = FakeMcpClient()

    async def answer(self, message, capability, history=None):
        return "你好，我可以帮你查询数据或处理预测任务。"

    monkeypatch.setattr(ConversationResponder, "respond", answer)
    app.dependency_overrides[get_mcp_client] = lambda: fake
    try:
        response = TestClient(app).post(
            "/api/v1/conversations/C-GREETING/messages",
            json={"message": "你好"},
        )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200
    body = response.json()
    assert body["capability_code"] == "GREETING"
    assert body["agent_type"] is None
    assert body["status"] == "COMPLETED"
    assert "短期客户智能体" not in body["message"]
    assert fake.memory == {}


def test_chat_resolves_raw_customer_before_ready() -> None:
    fake = FakeMcpClient()
    app.dependency_overrides[get_mcp_client] = lambda: fake
    try:
        response = TestClient(app).post(
            "/api/v1/conversations/C-003/messages",
            json={"message": "查询杭州燃气最新预测"},
        )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "COMPLETED"
    assert body["executed_tool"] == "forecast_latest_get"
    assert body["parameters"]["customerCode"] == "C-100"
    assert body["entity_resolution"]["resolved_entities"]["customer"]["name"] == "杭州燃气集团"


def test_customer_list_response_contains_business_records(monkeypatch) -> None:
    fake = FakeMcpClient()
    async def fail_llm(self, message, capability, result):
        return self._format_result(result)
    monkeypatch.setattr(ConversationResponder, "respond_with_result", fail_llm)
    app.dependency_overrides[get_mcp_client] = lambda: fake
    try:
        response = TestClient(app).post(
            "/api/v1/conversations/C-LIST/messages",
            json={"message": "查询所有客户"},
        )
    finally:
        app.dependency_overrides.clear()
    assert response.status_code == 200
    assert "杭州燃气集团" in response.json()["message"]
