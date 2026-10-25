"""Python 内部会话接口的协议测试。

这些测试只覆盖 Java -> Python 的当前协议：会话记忆只包含
``pendingOperation`` 和 ``currentBusinessContext``；澄清答案始终使用用户的
普通文本消息，不再使用 ``answers`` 或旧的 ``pendingAction``。
"""

from __future__ import annotations

from collections.abc import Callable, Iterator
from typing import Any

import pytest
from fastapi.testclient import TestClient

from gas_forecast_agent.config import Settings, get_settings
from gas_forecast_agent.main import (
    _chat_references,
    app,
    get_internal_agent_client,
    get_mcp_client,
)
from gas_forecast_agent.mcp import McpClientError
from gas_forecast_agent.operation_models import OperationParameters
from gas_forecast_agent.schemas import (
    AgentRuntimeMetadata,
    AgentRuntimeRequest,
    ClarificationReason,
)
from gas_forecast_agent.skills import (
    DialogueAct,
    RequestSemanticExtractor,
    RequestSemantics,
    SkillIntent,
    TargetSelection,
)
from gas_forecast_agent.workflow import agent_graph, conversation_history, persistent_parameters


def _tool(
    name: str,
    *,
    properties: dict[str, dict[str, object]],
    required: list[str] | None = None,
    read_only: bool = True,
) -> dict[str, object]:
    return {
        "name": name,
        "description": name,
        "inputSchema": {
            "type": "object",
            "properties": properties,
            "required": required or [],
        },
        "annotations": {
            "readOnlyHint": read_only,
            "destructiveHint": not read_only,
        },
    }


class FakeMcpClient:
    """仅模拟当前七个 MCP 业务工具，不再保留实体或训练专用工具。"""

    def __init__(self) -> None:
        self.calls: list[tuple[str, dict[str, Any]]] = []

    async def list_tools(self) -> list[dict[str, object]]:
        common_page = {
            "keyword": {"type": "string"},
            "pageNo": {"type": "integer"},
            "pageSize": {"type": "integer"},
            "fetchAll": {"type": "boolean"},
        }
        return [
            _tool("region_list", properties=common_page),
            _tool("industry_list", properties=common_page),
            _tool("customer_list", properties={
                **common_page,
                "regionCode": {"type": "string"},
                "industryCode": {"type": "string"},
                "customerCode": {"type": "string"},
            }),
            _tool("forecast_result_query", properties={
                "customerCode": {"type": "string"},
                "regionCode": {"type": "string"},
                "industryCode": {"type": "string"},
                "agentCode": {"type": "string"},
                "includePoints": {"type": "boolean"},
            }),
            _tool("task_status_get", properties={
                "taskNo": {"type": "string"},
                "includeRecovery": {"type": "boolean"},
            }, required=["taskNo"]),
            _tool("task_steps_get", properties={
                "taskNo": {"type": "string"},
                "includeBusinessSteps": {"type": "boolean"},
                "includeAttempts": {"type": "boolean"},
            }, required=["taskNo"]),
            _tool("forecast_start", properties={
                "conversationNo": {"type": "string"},
                "agentCode": {"type": "string"},
                "dimensionType": {"type": "string"},
                "targets": {"type": "array"},
                "forecastHorizon": {"type": "integer", "minimum": 1},
                "forecastStartDate": {"type": "string"},
                "forecastUnit": {"type": "string"},
                "historyStartDate": {"type": "string"},
                "historyEndDate": {"type": "string"},
                "confirmed": {"type": "boolean"},
                "idempotencyKey": {"type": "string"},
                "autoTrain": {"type": "boolean"},
                "forceRetrain": {"type": "boolean"},
                "trainingPreference": {"type": "string"},
                "bulk": {"type": "boolean"},
            }, required=[
                "conversationNo", "agentCode", "dimensionType", "targets",
                "forecastHorizon", "confirmed",
            ], read_only=False),
            # 不属于任何 Skill 白名单，确保 Agent 不会越权调用。
            _tool("admin_delete", properties={}, read_only=False),
        ]

    async def call_tool(self, name: str, arguments: dict[str, Any]) -> dict[str, object]:
        self.calls.append((name, arguments))
        if name == "forecast_start":
            data: dict[str, object] = {
                "taskNo": "TASK-1",
                "total": 1,
                "status": "PENDING",
                "tasks": [{
                    "detailNo": "DETAIL-1",
                    "forecastNo": "FORECAST-1",
                    "customerCode": "CUS-1",
                    "status": "PENDING",
                }],
            }
            status = "EXECUTING"
            result_type = "TASK_CREATED"
            message = "预测任务已创建"
        elif name == "customer_list":
            data = {
                "records": [{
                    "customerCode": "CUS-1",
                    "customerName": "高邮安源",
                    "regionCode": "320000",
                    "regionName": "江苏",
                    "industryCode": "CITY_GAS",
                    "industryName": "城燃",
                }],
                "total": 1,
            }
            status = "COMPLETED"
            result_type = "PAGE"
            message = "查询完成"
        elif name == "task_status_get":
            data = {
                "taskNo": arguments["taskNo"],
                "status": "RUNNING",
                "summary": {"total": 1, "pending": 0, "running": 1, "success": 0, "failed": 0},
                "details": [],
            }
            status = "COMPLETED"
            result_type = "TASK_STATUS"
            message = "任务状态查询完成"
        else:
            data = {"records": [], "total": 0}
            status = "COMPLETED"
            result_type = "PAGE"
            message = "查询完成"
        return {
            "isError": False,
            "structuredContent": {
                "success": True,
                "status": status,
                "resultType": result_type,
                "message": message,
                "retryable": False,
                "data": data,
            },
        }


class FailingMcpClient(FakeMcpClient):
    async def list_tools(self) -> list[dict[str, object]]:
        raise McpClientError("unauthorized", 401)


class FakeEntityResolver:
    """Java 内部实体解析接口的替身，实体解析不再通过 MCP 工具完成。"""

    def __init__(self) -> None:
        self.calls: list[dict[str, Any]] = []

    async def resolve_entities(self, arguments: dict[str, Any]) -> dict[str, object]:
        self.calls.append(arguments)
        customer_names = list(arguments.get("customerNames") or [])
        if "不存在的客户" in customer_names:
            return {
                "status": "NOT_FOUND",
                "message": "没有找到匹配的客户",
                "items": [{
                    "entityType": "CUSTOMER",
                    "inputName": "不存在的客户",
                    "status": "NOT_FOUND",
                    "message": "没有找到匹配的客户",
                    "candidates": [],
                }],
            }

        parameters: dict[str, object] = {}
        if customer_names:
            codes = ["CUS-2" if name == "扬州中燃" else f"CUS-{index}"
                     for index, name in enumerate(customer_names, start=1)]
            parameters["customerCodes"] = codes
            parameters["customerNames"] = customer_names
        region_names = list(arguments.get("regionNames") or [])
        if region_names:
            parameters["regionCodes"] = ["320000" for _ in region_names]
            parameters["regionNames"] = region_names
        industry_names = list(arguments.get("industryNames") or [])
        if industry_names:
            parameters["industryCodes"] = ["CITY_GAS" for _ in industry_names]
            parameters["industryNames"] = industry_names
        return {
            "status": "UNIQUE",
            "message": "实体解析成功",
            "proposedParameters": parameters,
            "items": [],
        }


def rule_settings() -> Settings:
    return Settings(GAS_SKILL_ROUTING_MODE="rules", LLM_API_KEY=None)


@pytest.fixture
def api_client() -> Iterator[tuple[TestClient, FakeMcpClient, FakeEntityResolver]]:
    mcp = FakeMcpClient()
    resolver = FakeEntityResolver()
    app.dependency_overrides[get_mcp_client] = lambda: mcp
    app.dependency_overrides[get_internal_agent_client] = lambda: resolver
    app.dependency_overrides[get_settings] = rule_settings
    try:
        with TestClient(app) as client:
            yield client, mcp, resolver
    finally:
        app.dependency_overrides.clear()


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
        "businessContext": {"customerCode": "CUS-1", "customerName": "高邮安源"},
        "conversationContext": {
            "summary": None,
            "memory": {},
            "recentMessages": [],
        },
    }


def semantics(
    intent: SkillIntent | None,
    *,
    customers: list[str] | None = None,
    forecast_horizon: int | None = None,
    context_relation: str = "NEW",
    references: dict[str, str] | None = None,
) -> RequestSemantics:
    """构造与模型输出相同的已验证请求语义。"""
    return RequestSemantics.model_validate({
        "dialogueAct": DialogueAct.NEW_REQUEST.value,
        "requests": [{
            "requestId": "R1",
            "intent": intent.value if intent else None,
            "targetSelection": (
                TargetSelection.EXPLICIT.value if customers else TargetSelection.UNSPECIFIED.value
            ),
            "contextRelation": context_relation,
            "entities": {"customers": customers or [], "regions": [], "industries": []},
            "references": references or {},
            "parameters": (
                {"forecastHorizon": forecast_horizon}
                if forecast_horizon is not None else {}
            ),
        }],
    })


def install_extractor(
    monkeypatch: pytest.MonkeyPatch,
    callback: Callable[..., RequestSemantics],
) -> list[dict[str, object] | None]:
    """替换外部模型，保留当前 ``pendingOperation`` 调用契约。"""
    contexts: list[dict[str, object] | None] = []

    async def extract(
        self: RequestSemanticExtractor,
        message: str,
        *,
        pending_operation: dict[str, object] | None = None,
        input_type: str = "TEXT",
        available_skills: list[dict[str, object]] | None = None,
        conversation_context: dict[str, object] | None = None,
    ) -> RequestSemantics:
        contexts.append(conversation_context)
        return callback(
            message=message,
            pending_operation=pending_operation,
            input_type=input_type,
            available_skills=available_skills,
            conversation_context=conversation_context,
        )

    monkeypatch.setattr(RequestSemanticExtractor, "extract", extract)
    return contexts


def test_plain_greeting_does_not_reuse_pending_operation(api_client: tuple[TestClient, FakeMcpClient, FakeEntityResolver]) -> None:
    client, mcp, _ = api_client
    request = internal_request("你好", turn_id="TURN-CHAT-1", run_no="RUN-CHAT-1", agent_code="short-term")
    request["conversationContext"]["memory"] = {
        "pendingOperation": {
            "interaction": {
                "interactionId": "INT-OLD",
                "interactionType": "CLARIFICATION",
                "sourceTurnId": "TURN-OLD",
                "reason": "PARAMETER_MISSING",
                "resumeFrom": "PlanValidator",
            },
            "semantics": semantics(SkillIntent.FORECAST, customers=["高邮安源"]).model_dump(by_alias=True),
            "parameters": {"customerCodes": ["CUS-OLD"], "forecastHorizon": 30},
            "pendingTool": "forecast_start",
        },
    }

    response = client.post("/api/v1/internal/chat", json=request)

    body = response.json()
    assert response.status_code == 200
    assert body["status"] == "SUCCESS"
    assert body["intentCode"] == "CHAT"
    assert body["skillCode"] == "general-chat"
    assert body["clarification"] is None
    assert [name for name, _ in mcp.calls].count("forecast_start") == 0
    # 普通新消息不会恢复旧操作；Java 收到 null 后清理已过期的 pendingOperation。
    assert body["memoryPatch"] == {"pendingOperation": None}


def test_unknown_intent_returns_current_clarification_protocol(
    api_client: tuple[TestClient, FakeMcpClient, FakeEntityResolver],
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    client, mcp, _ = api_client
    install_extractor(monkeypatch, lambda **_: RequestSemantics())

    response = client.post("/api/v1/internal/chat", json=internal_request("做一下那个东西"))

    body = response.json()
    assert response.status_code == 200
    assert body["status"] == "WAITING"
    assert body["intentCode"] == "CHAT"
    assert body["skillCode"] == "general-chat"
    assert body["clarification"]["interactionType"] == "CLARIFICATION"
    assert body["clarification"]["reason"] == "INTENT_AMBIGUOUS"
    assert body["memoryPatch"]["pendingOperation"]["issues"][0]["field"] == "intent"
    assert mcp.calls == []


def test_model_receives_summary_history_page_context_and_current_memory(
    api_client: tuple[TestClient, FakeMcpClient, FakeEntityResolver],
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    client, _, _ = api_client
    contexts = install_extractor(monkeypatch, lambda **_: semantics(SkillIntent.QUERY))
    request = internal_request("查询客户", turn_id="TURN-CONTEXT-2", run_no="RUN-CONTEXT-2")
    request["conversationContext"] = {
        "summary": "用户上一轮查询了高邮安源。",
        "memory": {
            "currentBusinessContext": {"customerCode": "CUS-1", "customerName": "高邮安源"},
        },
        "recentMessages": [{
            "messageId": 1,
            "turnId": "TURN-CONTEXT-1",
            "role": "ASSISTANT",
            "content": "已找到高邮安源。",
            "references": [],
        }],
    }

    response = client.post("/api/v1/internal/chat", json=request)

    assert response.status_code == 200
    context = contexts[-1]
    assert context is not None
    assert context["summary"] == "用户上一轮查询了高邮安源。"
    assert context["recentMessages"] == [{"role": "assistant", "content": "已找到高邮安源。"}]
    assert context["memory"] == {
        "currentBusinessContext": {"customerCode": "CUS-1", "customerName": "高邮安源"},
    }
    assert context["pageContext"] == {"customerCode": "CUS-1", "customerName": "高邮安源"}


def test_semantic_extraction_failure_has_distinct_error_code(
    api_client: tuple[TestClient, FakeMcpClient, FakeEntityResolver],
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    client, mcp, _ = api_client

    async def fail(*_: object, **__: object) -> RequestSemantics:
        raise RuntimeError("model unavailable")

    monkeypatch.setattr(RequestSemanticExtractor, "extract", fail)
    response = client.post("/api/v1/internal/chat", json=internal_request("预测高邮安源未来15天"))

    body = response.json()
    # 非 SemanticExtractionError 的内部异常走统一失败分支。
    assert response.status_code == 200
    assert body["status"] == "FAILED"
    assert body["errorCode"] == "AGENT_EXECUTION_ERROR"
    assert mcp.calls == []


def test_only_typed_business_parameters_are_persisted() -> None:
    parameters = OperationParameters(
        customer_code="CUS-1",
        forecast_horizon=30,
        confirmed=True,
        locked_parameters=["customerCode"],
    )

    assert persistent_parameters(parameters) == {
        "customerCode": "CUS-1",
        "forecastHorizon": 30,
    }


def test_agent_graph_has_current_linear_nodes() -> None:
    nodes = set(agent_graph.get_graph().nodes)

    assert {
        "understand_request",
        "execute_action",
        "build_response",
    }.issubset(nodes)
    assert "select_skill" not in nodes


def test_compressed_summary_is_kept_with_recent_messages() -> None:
    request = AgentRuntimeRequest(
        turn_id="TURN-1",
        conversation_no="C-1",
        message="继续",
        conversation_summary="用户此前选择了浙江地区。",
        metadata=AgentRuntimeMetadata(run_no="RUN-1", agent_code="general"),
        history=[{"role": "user", "content": f"历史消息 {index}"} for index in range(10)],
    )

    history = conversation_history(request)

    assert len(history) == 8
    assert history[0]["role"] == "assistant"
    assert "浙江地区" in history[0]["content"]
    assert history[1]["content"] == "历史消息 3"
    assert history[-1]["content"] == "历史消息 9"


def test_task_and_forecast_references_are_extracted_from_current_result_shape() -> None:
    references = _chat_references(
        {
            "status": "EXECUTING",
            "data": {
                "taskNo": "TASK-1",
                "tasks": [
                    {"forecastNo": "FORECAST-1"},
                    {"forecastNo": "FORECAST-2"},
                    {"forecastNo": "FORECAST-1"},
                ],
            },
        },
        "forecast_start",
    )

    assert [(item.reference_type, item.reference_no) for item in references] == [
        ("EXECUTION_TASK", "TASK-1"),
        ("FORECAST", "FORECAST-1"),
        ("FORECAST", "FORECAST-2"),
    ]


def test_text_clarification_then_confirmation_restores_pending_operation(
    api_client: tuple[TestClient, FakeMcpClient, FakeEntityResolver],
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """客户先确认、天数后补充，三轮只经 memoryPatch 传递状态。"""
    client, mcp, resolver = api_client

    def extract(**kwargs: object) -> RequestSemantics:
        message = str(kwargs["message"])
        if "30" in message:
            return semantics(SkillIntent.FORECAST, forecast_horizon=30)
        return semantics(SkillIntent.FORECAST, customers=["高邮安源"])

    install_extractor(monkeypatch, extract)

    first = client.post(
        "/api/v1/internal/chat",
        json=internal_request("预测高邮安源", turn_id="TURN-1", run_no="RUN-1", agent_code="short-term"),
    ).json()
    assert first["status"] == "WAITING"
    assert first["clarification"]["reason"] == "PARAMETER_MISSING"
    assert first["clarification"]["fields"][0]["parameterCode"] == "forecastHorizon"
    pending_after_first = first["memoryPatch"]["pendingOperation"]
    assert pending_after_first["parameters"]["customerCodes"] == ["CUS-1"]
    assert "pendingAction" not in first["memoryPatch"]

    second_request = internal_request("30天", turn_id="TURN-2", run_no="RUN-2", agent_code="short-term")
    second_request.update({
        "inputType": "CLARIFICATION_REPLY",
        "interactionId": first["clarification"]["interactionId"],
        "replyToTurnId": first["clarification"]["replyToTurnId"],
    })
    second_request["conversationContext"]["memory"] = first["memoryPatch"]
    second = client.post("/api/v1/internal/chat", json=second_request).json()

    assert second["status"] == "WAITING"
    assert second["clarification"]["interactionType"] == "CONFIRMATION"
    pending_after_second = second["memoryPatch"]["pendingOperation"]
    assert pending_after_second["pendingTool"] == "forecast_start"
    assert pending_after_second["parameters"]["forecastHorizon"] == 30
    assert pending_after_second["parameters"]["targets"][0]["customerCodes"] == ["CUS-1"]

    third_request = internal_request("确认", turn_id="TURN-3", run_no="RUN-3", agent_code="short-term")
    third_request.update({
        "inputType": "INTERACTION_REPLY",
        "interactionId": second["clarification"]["interactionId"],
        "replyToTurnId": second["clarification"]["replyToTurnId"],
    })
    third_request["conversationContext"]["memory"] = second["memoryPatch"]
    third = client.post("/api/v1/internal/chat", json=third_request).json()

    assert third["status"] == "SUCCESS"
    assert third["clarification"] is None
    assert third["memoryPatch"] == {"pendingOperation": None}
    forecast_arguments = next(arguments for name, arguments in mcp.calls if name == "forecast_start")
    assert forecast_arguments["confirmed"] is True
    assert forecast_arguments["forecastHorizon"] == 30
    assert forecast_arguments["targets"][0]["customerCodes"] == ["CUS-1"]
    assert len(resolver.calls) == 1


def test_corrected_entity_name_is_resolved_again_from_plain_text(
    api_client: tuple[TestClient, FakeMcpClient, FakeEntityResolver],
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    client, mcp, resolver = api_client

    def extract(**kwargs: object) -> RequestSemantics:
        message = str(kwargs["message"])
        customer = "扬州中燃" if "扬州中燃" in message else "不存在的客户"
        return semantics(SkillIntent.FORECAST, customers=[customer], forecast_horizon=30)

    install_extractor(monkeypatch, extract)

    first = client.post(
        "/api/v1/internal/chat",
        json=internal_request("预测不存在的客户未来30天", turn_id="TURN-ENTITY-1", run_no="RUN-ENTITY-1"),
    ).json()
    assert first["status"] == "WAITING"
    assert first["clarification"]["reason"] == "ENTITY_NOT_FOUND"

    second_request = internal_request("扬州中燃", turn_id="TURN-ENTITY-2", run_no="RUN-ENTITY-2")
    second_request.update({
        "inputType": "CLARIFICATION_REPLY",
        "interactionId": first["clarification"]["interactionId"],
        "replyToTurnId": first["clarification"]["replyToTurnId"],
    })
    second_request["conversationContext"]["memory"] = first["memoryPatch"]
    second = client.post("/api/v1/internal/chat", json=second_request).json()

    assert second["status"] == "WAITING"
    assert second["clarification"]["interactionType"] == "CLARIFICATION"
    assert second["memoryPatch"]["pendingOperation"]["parameters"]["customerCodes"] == ["CUS-2"]
    assert [call["customerNames"] for call in resolver.calls] == [["不存在的客户"], ["扬州中燃"]]
    assert [name for name, _ in mcp.calls].count("forecast_start") == 0


def test_multiple_customers_are_validated_by_internal_entity_service_before_confirmation(
    api_client: tuple[TestClient, FakeMcpClient, FakeEntityResolver],
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    client, mcp, resolver = api_client
    install_extractor(
        monkeypatch,
        lambda **_: semantics(
            SkillIntent.FORECAST,
            customers=["高邮安源", "扬州中燃"],
            forecast_horizon=30,
        ),
    )

    response = client.post(
        "/api/v1/internal/chat",
        json=internal_request("预测高邮安源和扬州中燃未来30天", turn_id="TURN-BATCH-1", run_no="RUN-BATCH-1"),
    )

    body = response.json()
    assert response.status_code == 200
    assert body["status"] == "WAITING"
    assert body["clarification"]["interactionType"] == "CONFIRMATION"
    assert resolver.calls == [{"customerNames": ["高邮安源", "扬州中燃"]}]
    assert [name for name, _ in mcp.calls].count("forecast_start") == 0
    assert body["memoryPatch"]["pendingOperation"]["parameters"]["targets"][0]["customerCodes"] == ["CUS-1", "CUS-2"]


def test_legacy_answers_field_is_not_part_of_current_internal_protocol() -> None:
    request = internal_request("30天")
    request["answers"] = {"forecastHorizon": 30}

    response = TestClient(app).post("/api/v1/internal/chat", json=request)

    assert response.status_code == 422


def test_browser_chat_entry_is_not_exposed_by_python() -> None:
    response = TestClient(app).post("/api/v1/chat", json={"message": "查询客户"})

    assert response.status_code == 404


def test_internal_chat_requires_java_generated_identifiers() -> None:
    response = TestClient(app).post("/api/v1/internal/chat", json={"message": "查询客户"})

    assert response.status_code == 422


def test_mcp_failure_returns_structured_failed_agent_result(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    resolver = FakeEntityResolver()
    app.dependency_overrides[get_mcp_client] = lambda: FailingMcpClient()
    app.dependency_overrides[get_internal_agent_client] = lambda: resolver
    app.dependency_overrides[get_settings] = rule_settings
    install_extractor(monkeypatch, lambda **_: semantics(SkillIntent.QUERY))
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


def test_customer_and_task_queries_use_read_only_skill(
    api_client: tuple[TestClient, FakeMcpClient, FakeEntityResolver],
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    client, mcp, _ = api_client

    def extract(**kwargs: object) -> RequestSemantics:
        message = str(kwargs["message"])
        return semantics(SkillIntent.QUERY)

    install_extractor(monkeypatch, extract)
    customer = client.post(
        "/api/v1/internal/chat",
        json=internal_request("查询客户", turn_id="TURN-CUSTOMER", run_no="RUN-CUSTOMER"),
    ).json()
    task = client.post(
        "/api/v1/internal/chat",
        json=internal_request("查询任务 TASK-123 状态", turn_id="TURN-TASK", run_no="RUN-TASK"),
    ).json()

    assert customer["status"] == "SUCCESS"
    assert customer["intentCode"] == "QUERY"
    assert customer["skillCode"] == "business-data-query"
    assert customer["executionResult"]["data"]["records"][0]["customerCode"] == "CUS-1"
    assert task["status"] == "SUCCESS"
    assert task["skillCode"] == "business-data-query"
    assert [name for name, _ in mcp.calls] == ["customer_list", "task_status_get"]
