"""使用真实 LangGraph 和 MCP Schema，仅替换网络依赖，验证对象化后的完整链路。"""

import copy
from pathlib import Path
from typing import Any, get_args, get_origin

import pytest
import yaml
from fastapi.testclient import TestClient
from pydantic import BaseModel, ValidationError

from gas_forecast_agent.config import Settings, get_settings
from gas_forecast_agent.main import app, get_internal_agent_client, get_mcp_client
from gas_forecast_agent.schemas import ConversationMemory, PendingOperation
from gas_forecast_agent.skills import RequestSemanticExtractor, RequestSemantics, SemanticExtractionError
from gas_forecast_agent.tool_results import CustomerRecord, TaskCreatedResult, ToolExecutionResult
from gas_forecast_agent.workflow_nodes.state import AgentGraphState


class BusinessTools:
    def __init__(self):
        schema_file = Path(__file__).parents[2] / "gas-forecast-mcp/src/main/resources/mcp-tools.yml"
        self.tools = yaml.safe_load(schema_file.read_text())["tools"]
        self.calls = []
        self.created = []

    async def list_tools(self):
        return self.tools

    async def call_tool(self, name, arguments):
        self.calls.append((name, copy.deepcopy(arguments)))
        assert name == "forecast_start"
        if not arguments.get("confirmed"):
            status = "NEED_CONFIRMATION"
            data = {"status": status, "affectedCount": 1, "proposedParameters": {"confirmed": True}}
        else:
            self.created.append(copy.deepcopy(arguments))
            status = "EXECUTING"
            data = {"taskNo": "TASK-1", "total": 1, "status": "PENDING", "tasks": [
                {"detailNo": "TASK-1-1", "status": "PENDING", "dimensionType": "CUSTOMER", "customerCode": "CUS-1"},
            ]}
        return {"structuredContent": {
            "success": True, "status": status, "resultType": "TASK", "errorType": None,
            "errorCode": None, "message": "请确认" if not arguments.get("confirmed") else "已创建",
            "retryable": False, "data": data,
        }, "isError": False}


class Entities:
    def __init__(self):
        self.calls = []

    async def resolve_entities(self, arguments):
        self.calls.append(arguments)
        names = arguments["customerNames"]
        if names == ["安源"]:
            return {"status": "MULTIPLE", "message": "请选择客户", "proposedParameters": {}, "items": [{
                "entityType": "CUSTOMER", "inputName": "安源", "status": "MULTIPLE", "message": "请选择客户",
                "candidates": [
                    {"type": "CUSTOMER", "code": "CUS-1", "name": "高邮安源", "regionName": "江苏", "industryName": "城燃"},
                    {"type": "CUSTOMER", "code": "CUS-2", "name": "客户二", "regionName": "浙江", "industryName": "工业"},
                ],
            }]}
        codes = ["CUS-1" if name == "高邮安源" else "CUS-2" for name in names]
        return {"status": "UNIQUE", "items": [], "proposedParameters": {"customerCodes": codes, "customerNames": names}}


class Conversation:
    """模拟 Java 读取记忆并应用最终补丁；不连接数据库或外部模型。"""

    def __init__(self, client):
        self.client = client
        self.memory = {}
        self.last = None
        self.turn = 0

    def send(self, message, input_type="TEXT", **overrides):
        self.turn += 1
        body = {
            "conversationNo": "C-1", "turnId": f"TURN-{self.turn}", "runNo": f"RUN-{self.turn}",
            "agentCode": "short-term", "businessContext": {}, "message": message, "inputType": input_type,
            "conversationContext": {"memory": self.memory, "recentMessages": []},
        }
        if input_type != "TEXT":
            interaction = self.last["clarification"]
            body.update(interactionId=interaction["interactionId"], replyToTurnId=interaction["replyToTurnId"])
        body.update(overrides)
        response = self.client.post("/api/v1/internal/chat", json=body)
        assert response.status_code == 200, response.text
        self.last = response.json()
        patch = self.last["memoryPatch"]
        if patch is not None:
            if patch["pendingOperation"] is None:
                self.memory.pop("pendingOperation", None)
            else:
                self.memory["pendingOperation"] = patch["pendingOperation"]
        return self.last

    @property
    def pending(self) -> PendingOperation:
        return ConversationMemory.model_validate(self.memory).pending_operation


@pytest.fixture
def conversation(monkeypatch):
    tools, entities = BusinessTools(), Entities()
    async def extract(self, message, **kwargs):
        if message == "模型故障":
            raise SemanticExtractionError("模拟故障")
        customers = {
            "预测安源": ["安源"], "预测高邮安源": ["高邮安源"],
            "预测客户二": ["客户二"], "预测两个客户30天": ["高邮安源", "客户二"],
        }.get(message, [])
        return RequestSemantics.model_validate({"requests": [{
            "intent": "FORECAST", "entities": {"customers": customers},
            "parameters": {"forecastHorizon": 30} if "30天" in message else {},
        }]})
    monkeypatch.setattr(RequestSemanticExtractor, "extract", extract)
    app.dependency_overrides[get_mcp_client] = lambda: tools
    app.dependency_overrides[get_internal_agent_client] = lambda: entities
    app.dependency_overrides[get_settings] = lambda: Settings(GAS_SKILL_ROUTING_MODE="rules", LLM_API_KEY=None)
    try:
        with TestClient(app) as client:
            yield Conversation(client), tools, entities
    finally:
        app.dependency_overrides.clear()


def test_candidate_then_horizon_then_confirmation_keeps_same_operation(conversation):
    chat, tools, entities = conversation
    assert chat.send("预测安源")["status"] == "WAITING"
    assert chat.pending.issues[0].details.candidates[0].parameters.customer_codes == ["CUS-1"]
    assert chat.send("1", "CLARIFICATION_REPLY")["status"] == "WAITING"
    assert chat.pending.parameters.customer_codes == ["CUS-1"]
    assert chat.pending.parameters.forecast_horizon is None
    assert chat.send("30天", "CLARIFICATION_REPLY")["status"] == "WAITING"
    assert chat.pending.parameters.customer_codes == ["CUS-1"]
    assert chat.pending.parameters.forecast_horizon == 30
    assert len(entities.calls) == 1  # 补天数不再重复查已确认客户。
    assert not tools.created
    reply = chat.send("确认", "INTERACTION_REPLY")
    assert reply["status"] == "SUCCESS", reply
    assert reply["references"][0]["referenceNo"] == "TASK-1"
    assert len(tools.created) == 1
    assert tools.created[0]["targets"][0]["customerCodes"] == ["CUS-1"]
    assert tools.created[0]["forecastHorizon"] == 30
    assert "pendingOperation" not in chat.memory
    assert isinstance(ToolExecutionResult.model_validate(reply["executionResult"]).data, TaskCreatedResult)


def test_new_request_does_not_inherit_pending_or_completed_parameters(conversation):
    chat, tools, _ = conversation
    chat.send("预测两个客户30天")
    assert chat.pending.parameters.forecast_horizon == 30
    chat.send("预测客户二")  # TEXT 新请求，不回答上个操作的确认。
    assert chat.pending.parameters.customer_codes == ["CUS-2"]
    assert chat.pending.parameters.forecast_horizon is None
    chat.send("30天", "CLARIFICATION_REPLY")
    chat.send("确认", "INTERACTION_REPLY")
    assert len(tools.created) == 1
    chat.send("预测高邮安源")
    assert chat.pending.parameters.customer_codes == ["CUS-1"]
    assert chat.pending.parameters.forecast_horizon is None


def test_batch_targets_and_rejection_remain_supported(conversation):
    chat, tools, _ = conversation
    chat.send("预测两个客户30天")
    assert chat.pending.parameters.customer_codes == ["CUS-1", "CUS-2"]
    chat.send("拒绝", "INTERACTION_REPLY")
    assert not tools.created
    assert "pendingOperation" not in chat.memory


def test_ambiguous_confirmation_reply_keeps_pending_operation(conversation):
    chat, tools, _ = conversation
    chat.send("预测两个客户30天")
    reply = chat.send("嗯", "INTERACTION_REPLY")
    assert reply["status"] == "WAITING"
    assert "确认" in reply["message"]
    assert chat.pending.pending_tool == "forecast_start"
    assert not tools.created


def test_failed_turn_preserves_pending_operation_and_stale_reply_cannot_execute(conversation):
    chat, tools, _ = conversation
    chat.send("预测两个客户30天")
    original = copy.deepcopy(chat.memory)
    reply = chat.send("确认", "INTERACTION_REPLY", interactionId="INT-expired")
    assert reply["status"] == "FAILED"
    assert reply["memoryPatch"] is None
    assert chat.memory == original
    reply = chat.send("模型故障")
    assert reply["errorCode"] == "SEMANTIC_EXTRACTION_FAILED"
    assert chat.memory == original
    assert not tools.created


def test_state_recursively_contains_only_explicit_models_and_values():
    visited = set()
    def inspect(annotation, path):
        assert annotation not in (Any, object, dict), path
        assert get_origin(annotation) is not dict, path
        if isinstance(annotation, type) and issubclass(annotation, BaseModel):
            if annotation in visited:
                return
            visited.add(annotation)
            for name, field in annotation.model_fields.items():
                inspect(field.annotation, f"{path}.{name}")
        else:
            for item in get_args(annotation):
                inspect(item, path)
    inspect(AgentGraphState, "state")
    assert len(visited) > 30


def test_tool_data_is_typed_and_preserves_wire_values():
    payload = {"success": True, "status": "COMPLETED", "resultType": "PAGE", "data": {
        "records": [{"customerCode": "C-1", "customerName": "客户", "regionName": "江苏"}],
        "total": 1, "page": 1, "size": 20,
    }}
    result = ToolExecutionResult.model_validate(payload)
    assert isinstance(result.data.records[0], CustomerRecord)
    assert result.data.records[0].region_name == "江苏"
    assert result.to_payload() == payload
    with pytest.raises(ValidationError):
        ToolExecutionResult.model_validate({"data": {"unknownBusinessField": 1}})
