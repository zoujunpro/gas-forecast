import pytest
from pydantic import ValidationError

from gas_forecast_agent.schemas import (
    AgentChatRequest,
    AgentChatResponse,
    AgentStatus,
    AgentType,
    ExecutionResourceResponse,
    ExecutionStatus,
    ExecutionStepDetailResponse,
    MessageReferenceResponse,
    ReferenceRelation,
    ReferenceType,
    ResourceRole,
    ResourceType,
)


def test_agent_chat_request_contains_java_generated_identifiers() -> None:
    schema = AgentChatRequest.model_json_schema(by_alias=True)

    assert set(schema["properties"]) == {
        "conversationNo",
        "turnId",
        "runNo",
        "message",
        "inputType",
        "interactionId",
        "replyToTurnId",
        "answers",
        "agentCode",
        "businessContext",
        "conversationContext",
    }
    assert set(schema["required"]) == {
        "conversationNo", "turnId", "runNo", "message", "agentCode",
        "businessContext", "conversationContext",
    }


def test_agent_chat_request_accepts_business_and_conversation_context() -> None:
    request = AgentChatRequest.model_validate(
        {
            "conversationNo": "C-1",
            "turnId": "TURN-1",
            "runNo": "RUN-1",
            "message": "预测这个客户未来30天的用气量",
            "agentCode": "short-term",
            "businessContext": {
                "regionCode": "320000",
                "regionName": "江苏",
                "industryCode": "CITY_GAS",
                "industryName": "城市燃气",
                "customerCode": "KH001",
                "customerName": "高邮安源",
            },
            "conversationContext": {
                "summary": "用户正在处理高邮安源的短期预测。",
                "memory": {"forecastHorizon": 30},
                "recentMessages": [{
                    "messageId": 10,
                    "turnId": "TURN-0",
                    "role": "ASSISTANT",
                    "content": "已创建训练任务",
                    "messageType": "model-training-management",
                    "createdAt": "2026-10-20T09:30:00+08:00",
                    "references": [{
                        "referenceType": "TRAIN_BATCH",
                        "referenceNo": "TRAIN-1",
                        "relationType": "CREATED",
                    }],
                }],
            },
        }
    )

    assert request.agent_code == AgentType.SHORT_TERM
    assert request.business_context is not None
    assert request.business_context.customer_code == "KH001"
    assert request.business_context.customer_name == "高邮安源"
    assert request.conversation_context.summary is not None
    assert request.conversation_context.memory["forecastHorizon"] == 30
    recent = request.conversation_context.recent_messages[0]
    assert recent.message_type == "model-training-management"
    assert recent.references[0].reference_no == "TRAIN-1"


def test_agent_chat_request_rejects_missing_server_identifiers() -> None:
    with pytest.raises(ValidationError):
        AgentChatRequest.model_validate(
            {
                "message": "分析当前预测结果",
                "agentCode": "short-term",
            }
        )


def test_structured_reply_requires_interaction_and_source_turn() -> None:
    payload = {
        "conversationNo": "C-1",
        "turnId": "TURN-2",
        "runNo": "RUN-2",
        "message": "选择第一个",
        "inputType": "CLARIFICATION_REPLY",
        "answers": {"customerName": "candidate-1"},
        "agentCode": "general",
        "businessContext": {},
        "conversationContext": {},
    }

    with pytest.raises(ValidationError, match="interactionId"):
        AgentChatRequest.model_validate(payload)

    payload.update({"interactionId": "INT-1", "replyToTurnId": "TURN-1"})
    request = AgentChatRequest.model_validate(payload)
    assert request.input_type.value == "CLARIFICATION_REPLY"
    assert request.answers == {"customerName": "candidate-1"}


def test_agent_chat_response_supports_multiple_message_references() -> None:
    response = AgentChatResponse(
        conversation_no="C-1",
        turn_id="TURN-1",
        run_no="RUN-1",
        agent_code=AgentType.SHORT_TERM,
        status=AgentStatus.SUCCESS,
        message="已创建训练和预测任务。",
        references=[
            MessageReferenceResponse(
                reference_type=ReferenceType.EXECUTION_TASK,
                reference_no="T-1",
                relation_type=ReferenceRelation.CREATED,
                status=ExecutionStatus.PENDING,
            ),
            MessageReferenceResponse(
                reference_type=ReferenceType.EXECUTION_TASK,
                reference_no="T-2",
                relation_type=ReferenceRelation.CREATED,
                status=ExecutionStatus.WAITING,
            ),
        ],
    )

    payload = response.model_dump(mode="json", by_alias=True)
    assert payload["agentCode"] == "short-term"
    assert [item["referenceNo"] for item in payload["references"]] == ["T-1", "T-2"]


def test_execution_step_detail_uses_nullable_progress_and_multiple_resources() -> None:
    detail = ExecutionStepDetailResponse(
        step_no="S-1",
        task_no="T-1",
        attempt_no=1,
        step_code="DATA_PREPARE",
        step_name="历史数据准备",
        step_order=1,
        status=ExecutionStatus.RUNNING,
        progress_percent=None,
        message="正在准备训练数据",
        has_detail=True,
        resources=[
            ExecutionResourceResponse(
                resource_ref="dataset:history-sales:1",
                resource_code="HISTORY_SALES",
                resource_name="历史销量数据",
                resource_role=ResourceRole.INPUT,
                resource_type=ResourceType.DATASET,
                summary={"recordCount": 730},
            ),
            ExecutionResourceResponse(
                resource_ref="dataset:weather:1",
                resource_code="WEATHER_FEATURES",
                resource_name="气象特征数据",
                resource_role=ResourceRole.INPUT,
                resource_type=ResourceType.DATASET,
                summary={"recordCount": 730},
            ),
        ],
    )

    assert detail.progress_percent is None
    assert [resource.resource_code for resource in detail.resources] == [
        "HISTORY_SALES",
        "WEATHER_FEATURES",
    ]
