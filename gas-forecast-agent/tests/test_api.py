import hashlib
from typing import Any

from fastapi.testclient import TestClient
import pytest

from gas_forecast_agent.config import Settings, get_settings
from gas_forecast_agent.main import _chat_references, app, get_mcp_client
from gas_forecast_agent.mcp import McpClientError
from gas_forecast_agent.schemas import AgentRuntimeRequest
from gas_forecast_agent.skills import RequestSemantics, SemanticExtractionError, SkillIntent
from gas_forecast_agent.workflow import (
    _resolved_clarification_parameters,
    agent_graph,
    conversation_history,
    persistent_parameters,
)


TOOLS = [
    "region_list", "industry_list", "region_search", "industry_search", "entity_batch_resolve",
    "customer_list", "customer_search", "task_status_get", "task_steps_get",
    "forecast_start", "forecast_result_query", "training_start",
    "training_status_get", "training_result_get", "training_latest_get",
    "training_history_get",
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
                    "required": [],
                    "properties": {
                        "conversationNo": {"type": "string"},
                        "customerCode": {"type": "string"},
                        "customerName": {"type": "string"},
                        "customerCodes": {"type": "array", "items": {"type": "string"}},
                        "customerNames": {"type": "array", "items": {"type": "string"}},
                        "regionCodes": {"type": "array", "items": {"type": "string"}},
                        "regionCode": {"type": "string"},
                        "regionName": {"type": "string"},
                        "regionNames": {"type": "array", "items": {"type": "string"}},
                        "industryCodes": {"type": "array", "items": {"type": "string"}},
                        "industryCode": {"type": "string"},
                        "industryName": {"type": "string"},
                        "industryNames": {"type": "array", "items": {"type": "string"}},
                        "taskNo": {"type": "string"},
                        "confirmed": {"type": "boolean"},
                        "idempotencyKey": {"type": "string"},
                        "autoTrain": {"type": "boolean"},
                        "forceRetrain": {"type": "boolean"},
                        "trainingPreference": {"type": "string"},
                        "bulk": {"type": "boolean"},
                    },
                },
                "annotations": {
                    "readOnlyHint": name not in {
                        "forecast_start", "training_start",
                    },
                    "destructiveHint": name in {"forecast_start", "training_start"},
                },
            }
            for name in TOOLS
        ] + [{"name": "admin_delete", "description": "禁止工具", "inputSchema": {}}]

    async def call_tool(self, name: str, arguments: dict[str, Any]) -> dict[str, Any]:
        self.calls.append((name, arguments))
        if name == "memory_update":
            self.memory.update(arguments["values"])
            data = dict(self.memory)
        elif name == "forecast_start":
            data = {
                "executionBatchNo": "EXEC-1",
                "taskNo": "TASK-1",
                "forecastBatchNo": "FORECAST-1",
            }
        elif name == "customer_list":
            data = {
                "records": [{"customerCode": "C-1", "customerName": "杭州燃气"}],
                "total": 1,
            }
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
        elif name == "customer_search":
            data = {"status": "UNIQUE", "candidates": [{
                "type": "CUSTOMER", "code": arguments.get("customerCode", "CUS-1"),
                "name": arguments.get("customerName", "高邮安源"),
                "regionCode": "320000", "regionName": "江苏",
                "industryCode": "CITY_GAS", "industryName": "城燃",
            }]}
        elif name == "entity_batch_resolve":
            names = arguments.get("customerNames") or []
            data = {
                "status": "UNIQUE",
                "message": "批量实体解析成功",
                "items": [],
                "proposedParameters": {
                    "customerCodes": [f"CUS-{index}" for index, _ in enumerate(names, start=1)],
                    "customerNames": names,
                },
            }
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


class FailingMcpClient(FakeMcpClient):
    async def list_tools(self) -> list[dict[str, Any]]:
        raise McpClientError("unauthorized", 401)


class CorrectableEntityMcpClient(FakeMcpClient):
    """第一次实体查询未命中，用户修正名称后第二次唯一命中。"""

    async def call_tool(self, name: str, arguments: dict[str, Any]) -> dict[str, Any]:
        if name != "customer_search":
            return await super().call_tool(name, arguments)
        self.calls.append((name, arguments))
        attempt = sum(call_name == "customer_search" for call_name, _ in self.calls)
        if attempt == 1:
            return {
                "isError": False,
                "structuredContent": {
                    "success": True,
                    "status": "NOT_FOUND",
                    "message": "没有找到匹配的客户",
                    "retryable": False,
                    "data": {"status": "NOT_FOUND", "candidates": []},
                },
            }
        return {
            "isError": False,
            "structuredContent": {
                "success": True,
                "status": "COMPLETED",
                "message": "客户解析成功",
                "retryable": False,
                "data": {
                    "status": "UNIQUE",
                    "candidates": [{
                        "type": "CUSTOMER",
                        "code": "CUS-2",
                        "name": arguments["customerName"],
                        "regionCode": "320000",
                        "regionName": "江苏",
                        "industryCode": "CITY_GAS",
                        "industryName": "城燃",
                    }],
                },
            },
        }


def rule_settings() -> Settings:
    return Settings(GAS_SKILL_ROUTING_MODE="rules", LLM_API_KEY=None)


@pytest.fixture
def model_understanding(monkeypatch):
    """用确定的结构化结果代替 API 测试中的外部大模型调用。"""
    captured_contexts = []

    async def extract(
        self,
        message,
        pending_action=None,
        pending_interaction=None,
        input_type="TEXT",
        answers=None,
        available_skills=None,
        conversation_context=None,
    ):
        captured_contexts.append(conversation_context)
        if "任务" in message and any(word in message for word in ("状态", "进度")):
            return RequestSemantics(
                intent=SkillIntent.QUERY,
            )
        if "预测" in message:
            return RequestSemantics(
                intent=SkillIntent.FORECAST,
            )
        return RequestSemantics(
            intent=SkillIntent.QUERY,
        )

    monkeypatch.setattr(
        "gas_forecast_agent.workflow.RequestSemanticExtractor.extract",
        extract,
    )
    return captured_contexts


def internal_request(
    message: str,
    *,
    turn_id: str = "TURN-1",
    run_no: str = "RUN-1",
    agent_code: str = "general",
) -> dict[str, Any]:
    return {
        "conversationNo": "C-1",
        "turnId": turn_id,
        "runNo": run_no,
        "message": message,
        "agentCode": agent_code,
        "businessContext": {"customerCode": "CUS-1"},
        "conversationContext": {
            "summary": None,
            "memory": {},
            "recentMessages": [],
        },
    }


def test_skill_endpoint_exposes_skill_tool_allowlists() -> None:
    response = TestClient(app).get("/api/v1/skills")

    assert response.status_code == 200
    body = response.json()
    assert body["total"] == 7
    forecast = next(item for item in body["skills"] if item["name"] == "customer-gas-forecast")
    assert forecast["intent"] == "FORECAST"
    assert forecast["readOnly"] is False
    assert "forecast_start" in forecast["allowed_tools"]
    assert next(item for item in body["skills"] if item["name"] == "general-chat")["allowed_tools"] == []
    assert next(item for item in body["skills"] if item["name"] == "unknown-request")["allowed_tools"] == []


def test_plain_greeting_never_enters_forecast_flow() -> None:
    fake = FakeMcpClient()
    app.dependency_overrides[get_mcp_client] = lambda: fake
    app.dependency_overrides[get_settings] = rule_settings
    request = internal_request(
        "你好",
        turn_id="TURN-CHAT-1",
        run_no="RUN-CHAT-1",
        agent_code="short-term",
    )
    existing_memory = {
        "currentSkill": "customer-gas-forecast",
        "currentParameters": {"forecastHorizon": 50},
        "pendingAction": {
            "tool": "forecast_start",
            "parameters": {"customerCode": "CUS-OLD"},
        },
    }
    request["conversationContext"]["memory"] = existing_memory
    fake.memory.update(existing_memory)
    try:
        response = TestClient(app).post("/api/v1/internal/chat", json=request)
    finally:
        app.dependency_overrides.clear()

    body = response.json()
    assert response.status_code == 200
    assert body["status"] == "SUCCESS"
    assert body["intentCode"] == "CHAT"
    assert body["skillCode"] == "general-chat"
    assert body["clarification"] is None
    assert [name for name, _ in fake.calls].count("forecast_start") == 0
    assert fake.memory["currentSkill"] == "customer-gas-forecast"
    assert fake.memory["currentParameters"] == {"forecastHorizon": 50}
    assert fake.memory["pendingAction"] is None


def test_unknown_intent_returns_safe_reply_without_tools(monkeypatch) -> None:
    async def extract(self, message, **kwargs):
        return RequestSemantics(
            intent=SkillIntent.UNKNOWN,
        )

    monkeypatch.setattr(
        "gas_forecast_agent.workflow.RequestSemanticExtractor.extract",
        extract,
    )
    fake = FakeMcpClient()
    app.dependency_overrides[get_mcp_client] = lambda: fake
    app.dependency_overrides[get_settings] = rule_settings
    try:
        response = TestClient(app).post(
            "/api/v1/internal/chat",
            json=internal_request(
                "做一下那个东西",
                turn_id="TURN-UNKNOWN-1",
                run_no="RUN-UNKNOWN-1",
            ),
        )
    finally:
        app.dependency_overrides.clear()

    body = response.json()
    assert response.status_code == 200
    assert body["status"] == "SUCCESS"
    assert body["intentCode"] == "UNKNOWN"
    assert body["skillCode"] == "unknown-request"
    assert body["clarification"] is None
    assert "不能确定" in body["message"]
    assert fake.calls == []


def test_plain_text_receives_summary_history_memory_and_page_context(
    model_understanding,
) -> None:
    fake = FakeMcpClient()
    app.dependency_overrides[get_mcp_client] = lambda: fake
    app.dependency_overrides[get_settings] = rule_settings
    request = internal_request(
        "再预测15天",
        turn_id="TURN-CONTEXT-2",
        run_no="RUN-CONTEXT-2",
        agent_code="short-term",
    )
    request["businessContext"] = {
        "customerCode": "CUS-1",
        "customerName": "高邮安源",
    }
    request["conversationContext"] = {
        "summary": "用户上一轮查询了高邮安源。",
        "memory": {
            "currentSkill": "business-data-query",
            "currentBusinessContext": {
                "customerCode": "CUS-1",
                "customerName": "高邮安源",
            },
            "pendingAction": {"tool": "training_start"},
        },
        "recentMessages": [{
            "messageId": 1,
            "turnId": "TURN-CONTEXT-1",
            "role": "ASSISTANT",
            "content": "已找到高邮安源。",
            "references": [],
        }],
    }
    try:
        response = TestClient(app).post("/api/v1/internal/chat", json=request)
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200
    context = model_understanding[-1]
    assert context["summary"] == "用户上一轮查询了高邮安源。"
    assert context["recentMessages"] == [
        {"role": "assistant", "content": "已找到高邮安源。"}
    ]
    assert context["memory"]["currentSkill"] == "business-data-query"
    assert "pendingAction" not in context["memory"]
    assert context["pageContext"]["customerCode"] == "CUS-1"


def test_semantic_extraction_failure_has_distinct_error_code(monkeypatch) -> None:
    async def extract(self, message, **kwargs):
        raise SemanticExtractionError("model unavailable")

    monkeypatch.setattr(
        "gas_forecast_agent.workflow.RequestSemanticExtractor.extract",
        extract,
    )
    fake = FakeMcpClient()
    app.dependency_overrides[get_mcp_client] = lambda: fake
    app.dependency_overrides[get_settings] = rule_settings
    try:
        response = TestClient(app).post(
            "/api/v1/internal/chat",
            json=internal_request(
                "预测高邮安源未来15天",
                turn_id="TURN-SEMANTIC-FAIL",
                run_no="RUN-SEMANTIC-FAIL",
            ),
        )
    finally:
        app.dependency_overrides.clear()

    body = response.json()
    assert response.status_code == 200
    assert body["status"] == "FAILED"
    assert body["errorCode"] == "SEMANTIC_EXTRACTION_FAILED"
    assert [name for name, _ in fake.calls] == []


def test_only_business_parameters_are_persisted_between_turns() -> None:
    assert persistent_parameters({
        "customerCode": "CUS-1",
        "forecastHorizon": 30,
        "confirmed": True,
        "turnId": "TURN-1",
        "_clarificationQuestion": "旧问题",
        "_lockedParameters": ["customerCode"],
        "_pendingTool": "forecast_start",
    }) == {"forecastHorizon": 30}


def test_free_text_entity_clarification_must_be_validated_again() -> None:
    resolved = _resolved_clarification_parameters(
        {
            "issues": [{
                "reason": "ENTITY_NOT_FOUND",
                "field": "customerName",
                "details": {"candidateMap": {}, "entityType": "CUSTOMER"},
            }],
        },
        {"customerName": "扬州中燃"},
    )

    assert resolved == {"customerName": "扬州中燃"}


def test_batch_entity_correction_preserves_unique_matches_but_revalidates_names() -> None:
    resolved = _resolved_clarification_parameters(
        {
            "issues": [{
                "reason": "ENTITY_NOT_FOUND",
                "field": "customerNames:1",
                "details": {
                    "candidateMap": {},
                    "entityType": "CUSTOMER",
                    "resolvedParameters": {
                        "customerCodes": ["CUS-1"],
                        "customerNames": ["高邮安源"],
                    },
                },
            }],
        },
        {"customerNames:1": "扬州中燃"},
    )

    assert resolved["customerCodes"] == ["CUS-1"]
    assert resolved["customerNames"] == ["高邮安源", "扬州中燃"]
    assert resolved["_validatedEntityParameters"] == ["customerCodes"]
    assert "_entityBatchResolved" not in resolved


def test_agent_graph_has_readable_business_nodes() -> None:
    nodes = set(agent_graph.get_graph().nodes)

    assert {
        "load_memory",
        "understand_request",
        "select_skill",
        "execute_action",
        "save_memory",
        "build_response",
    }.issubset(nodes)


def test_compressed_summary_is_kept_with_recent_messages() -> None:
    request = AgentRuntimeRequest(
        turn_id="TURN-1",
        conversation_no="C-1",
        message="继续",
        conversation_summary="用户此前选择了浙江地区。",
        history=[
            {"role": "user", "content": f"历史消息 {index}"}
            for index in range(10)
        ],
    )

    history = conversation_history(request)

    assert len(history) == 8
    assert history[0]["role"] == "assistant"
    assert "浙江地区" in history[0]["content"]
    assert history[1]["content"] == "历史消息 3"
    assert history[-1]["content"] == "历史消息 9"


def test_one_agent_result_can_reference_multiple_business_batches() -> None:
    references = _chat_references(
        {
            "status": "EXECUTING",
            "tasks": [
                {"forecastBatchNo": "FORECAST-1"},
                {"forecastBatchNo": "FORECAST-2"},
                {"forecastBatchNo": "FORECAST-1"},
            ],
        },
        "forecast_start",
    )

    assert [item.reference_no for item in references] == ["FORECAST-1", "FORECAST-2"]


def test_internal_chat_asks_before_using_current_page_business_context(model_understanding) -> None:
    fake = FakeMcpClient()
    app.dependency_overrides[get_mcp_client] = lambda: fake
    app.dependency_overrides[get_settings] = rule_settings
    request = internal_request(
        "预测当前客户未来30天用气量",
        turn_id="TURN-1",
        run_no="RUN-1",
        agent_code="short-term",
    )
    try:
        response = TestClient(app).post("/api/v1/internal/chat", json=request)
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200
    body = response.json()
    assert body["conversationNo"] == "C-1"
    assert body["turnId"] == "TURN-1"
    assert body["runNo"] == "RUN-1"
    assert body["status"] == "WAITING"
    assert body["intentCode"] == "FORECAST"
    assert "当前业务对象" in body["message"]
    assert "CUS-1" in body["message"]
    assert body["references"] == []
    assert body["skillCode"] == "customer-gas-forecast"
    assert body["resultData"]["executedTool"] is None
    assert body["clarification"]["interactionType"] == "CLARIFICATION"
    assert body["clarification"]["reason"] == "SCOPE_AMBIGUOUS"
    assert body["clarification"]["replyToTurnId"] == "TURN-1"
    assert body["clarification"]["fields"][0]["parameterCode"] == "businessScope"
    assert [name for name, _ in fake.calls].count("forecast_start") == 0
    assert fake.memory["pendingAction"] is None
    assert fake.memory["pendingInteraction"]["pendingTool"] == "forecast_start"
    assert fake.memory["pendingInteraction"]["parameters"]["customerCode"] == "CUS-1"
    assert fake.memory["pendingInteraction"]["parameters"]["idempotencyKey"] == hashlib.sha256(
        b"TURN-1"
    ).hexdigest()[:32]
    assert "currentBusinessContext" not in fake.memory
    assert "admin_delete" not in [name for name, _ in fake.calls]
    assert "memory_get" not in [name for name, _ in fake.calls]
    assert "conversation_prepare" not in [name for name, _ in fake.calls]
    assert "conversation_reply_append" not in [name for name, _ in fake.calls]


def test_structured_scope_reply_then_write_confirmation_resumes_original_request(model_understanding) -> None:
    fake = FakeMcpClient()
    app.dependency_overrides[get_mcp_client] = lambda: fake
    app.dependency_overrides[get_settings] = rule_settings
    client = TestClient(app)
    try:
        first = client.post(
            "/api/v1/internal/chat",
            json=internal_request(
                "预测当前客户未来30天用气量",
                turn_id="TURN-SCOPE-1",
                run_no="RUN-SCOPE-1",
                agent_code="short-term",
            ),
        ).json()
        first_interaction = first["clarification"]

        second_request = internal_request(
            "使用当前业务对象",
            turn_id="TURN-SCOPE-2",
            run_no="RUN-SCOPE-2",
            agent_code="short-term",
        )
        second_request.update({
            "inputType": "CLARIFICATION_REPLY",
            "interactionId": first_interaction["interactionId"],
            "replyToTurnId": first_interaction["replyToTurnId"],
            "answers": {"businessScope": "accept"},
        })
        second_request["conversationContext"]["memory"] = dict(fake.memory)
        second = client.post("/api/v1/internal/chat", json=second_request).json()

        assert second["status"] == "WAITING"
        assert second["intentCode"] == "FORECAST"
        assert second["clarification"]["interactionType"] == "CONFIRMATION"
        assert second["clarification"]["reason"] == "WRITE_CONFIRMATION_REQUIRED"
        assert fake.memory["pendingAction"]["tool"] == "forecast_start"

        confirmation = second["clarification"]
        third_request = internal_request(
            "确认执行",
            turn_id="TURN-SCOPE-3",
            run_no="RUN-SCOPE-3",
            agent_code="short-term",
        )
        third_request.update({
            "inputType": "CONFIRMATION_REPLY",
            "interactionId": confirmation["interactionId"],
            "replyToTurnId": confirmation["replyToTurnId"],
            "answers": {},
        })
        third_request["conversationContext"]["memory"] = dict(fake.memory)
        third = client.post("/api/v1/internal/chat", json=third_request).json()
    finally:
        app.dependency_overrides.clear()

    assert third["status"] == "SUCCESS"
    assert third["intentCode"] == "FORECAST"
    assert third["clarification"] is None
    assert [name for name, _ in fake.calls].count("forecast_start") == 1
    forecast_arguments = next(arguments for name, arguments in fake.calls if name == "forecast_start")
    assert forecast_arguments["customerCode"] == "CUS-1"
    assert forecast_arguments["confirmed"] is True


def test_multiple_customer_names_are_batch_validated_before_forecast(monkeypatch) -> None:
    async def extract(self, message, **kwargs):
        return RequestSemantics.model_validate({
            "dialogueAct": "NEW_REQUEST",
            "requests": [{
                "requestId": "R1",
                "intent": "FORECAST",
                "targetSelection": "EXPLICIT",
                "entities": {
                    "regions": [],
                    "industries": [],
                    "customers": ["高邮安源", "扬州中燃"],
                },
                "references": {},
                "parameters": {"forecastHorizon": 30},
                "dependsOn": [],
            }],
        })

    monkeypatch.setattr(
        "gas_forecast_agent.workflow.RequestSemanticExtractor.extract",
        extract,
    )
    fake = FakeMcpClient()
    app.dependency_overrides[get_mcp_client] = lambda: fake
    app.dependency_overrides[get_settings] = rule_settings
    try:
        response = TestClient(app).post(
            "/api/v1/internal/chat",
            json=internal_request(
                "预测高邮安源和扬州中燃未来30天用气量",
                turn_id="TURN-BATCH-1",
                run_no="RUN-BATCH-1",
                agent_code="short-term",
            ),
        )
    finally:
        app.dependency_overrides.clear()

    body = response.json()
    assert body["status"] == "WAITING"
    assert body["clarification"]["interactionType"] == "CONFIRMATION"
    assert [name for name, _ in fake.calls].count("entity_batch_resolve") == 1
    assert [name for name, _ in fake.calls].count("forecast_start") == 0
    assert fake.memory["pendingAction"]["parameters"]["customerCodes"] == ["CUS-1", "CUS-2"]
    assert fake.memory["pendingAction"]["parameters"]["bulk"] is True


def test_corrected_entity_name_is_resolved_again_before_forecast(monkeypatch) -> None:
    async def extract(self, message, *, answers=None, **kwargs):
        customer_name = (answers or {}).get("customerName") or "不存在的客户"
        return RequestSemantics.model_validate({
            "dialogueAct": "NEW_REQUEST",
            "requests": [{
                "requestId": "R1",
                "intent": "FORECAST",
                "targetSelection": "EXPLICIT",
                "entities": {
                    "regions": [],
                    "industries": [],
                    "customers": [customer_name],
                },
                "references": {},
                "parameters": {"forecastHorizon": 30},
                "dependsOn": [],
            }],
        })

    monkeypatch.setattr(
        "gas_forecast_agent.workflow.RequestSemanticExtractor.extract",
        extract,
    )
    fake = CorrectableEntityMcpClient()
    app.dependency_overrides[get_mcp_client] = lambda: fake
    app.dependency_overrides[get_settings] = rule_settings
    client = TestClient(app)
    try:
        first = client.post(
            "/api/v1/internal/chat",
            json=internal_request(
                "预测不存在的客户未来30天用气量",
                turn_id="TURN-ENTITY-1",
                run_no="RUN-ENTITY-1",
                agent_code="short-term",
            ),
        ).json()
        interaction = first["clarification"]

        second_request = internal_request(
            "扬州中燃",
            turn_id="TURN-ENTITY-2",
            run_no="RUN-ENTITY-2",
            agent_code="short-term",
        )
        second_request.update({
            "inputType": "CLARIFICATION_REPLY",
            "interactionId": interaction["interactionId"],
            "replyToTurnId": interaction["replyToTurnId"],
            "answers": {"customerName": "扬州中燃"},
        })
        second_request["conversationContext"]["memory"] = dict(fake.memory)
        second = client.post("/api/v1/internal/chat", json=second_request).json()
    finally:
        app.dependency_overrides.clear()

    assert first["status"] == "WAITING"
    assert interaction["reason"] == "ENTITY_NOT_FOUND"
    assert second["status"] == "WAITING"
    assert second["clarification"]["interactionType"] == "CONFIRMATION"
    assert [name for name, _ in fake.calls].count("customer_search") == 2
    assert [name for name, _ in fake.calls].count("forecast_start") == 0
    assert fake.memory["pendingAction"]["parameters"]["customerCode"] == "CUS-2"


def test_browser_chat_entry_is_no_longer_exposed_by_python() -> None:
    response = TestClient(app).post(
        "/api/v1/chat",
        json={"message": "查询客户"},
    )
    assert response.status_code == 404


def test_internal_chat_requires_java_generated_identifiers() -> None:
    response = TestClient(app).post(
        "/api/v1/internal/chat",
        json={"message": "查询客户"},
    )
    assert response.status_code == 422


def test_mcp_failure_returns_structured_failed_agent_result(model_understanding) -> None:
    app.dependency_overrides[get_mcp_client] = lambda: FailingMcpClient()
    app.dependency_overrides[get_settings] = rule_settings
    try:
        response = TestClient(app).post(
            "/api/v1/internal/chat",
            json=internal_request("查询客户", turn_id="TURN-AUTH", run_no="RUN-AUTH"),
        )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200
    assert response.json()["status"] == "FAILED"
    assert response.json()["errorCode"] == "AGENT_EXECUTION_ERROR"


def test_customer_query_uses_read_only_skill(model_understanding) -> None:
    fake = FakeMcpClient()
    app.dependency_overrides[get_mcp_client] = lambda: fake
    app.dependency_overrides[get_settings] = rule_settings
    try:
        response = TestClient(app).post(
            "/api/v1/internal/chat",
            json=internal_request("查询浙江城市燃气客户", turn_id="TURN-2", run_no="RUN-2"),
        )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "SUCCESS"
    assert body["intentCode"] == "QUERY"
    assert body["references"] == []
    assert body["skillCode"] == "business-data-query"
    semantics = body["intentData"]["entityResolution"]
    assert set(semantics) == {"dialogueAct", "requests"}
    assert semantics["requests"][0]["intent"] == "QUERY"
    assert set(semantics["requests"][0]["entities"]) == {"regions", "industries", "customers"}
    assert "needsClarification" not in semantics
    assert [name for name, _ in fake.calls if name == "customer_list"] == ["customer_list"]


def test_task_status_query_uses_read_only_query_skill(model_understanding) -> None:
    fake = FakeMcpClient()
    app.dependency_overrides[get_mcp_client] = lambda: fake
    app.dependency_overrides[get_settings] = rule_settings
    try:
        response = TestClient(app).post(
            "/api/v1/internal/chat",
            json=internal_request("查询任务 TASK-123 状态", turn_id="TURN-3A", run_no="RUN-3A"),
        )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "SUCCESS"
    assert body["skillCode"] == "business-data-query"
    assert [name for name, _ in fake.calls].count("task_status_get") == 1
