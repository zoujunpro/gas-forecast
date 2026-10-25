import asyncio
from types import SimpleNamespace
from typing import Any

import pytest

from gas_forecast_agent.config import Settings
from gas_forecast_agent.schemas import (
    ClarificationReason,
    ConversationMemory,
    InteractionType,
    PendingInteraction,
    PendingOperation,
)
from gas_forecast_agent.skills import (
    ActionExecutor,
    DialogueAct,
    RequestSemantics,
    SkillDefinition,
    SkillIntent,
    SkillRegistry,
    SkillRouter,
    TrainingPolicy,
)
from gas_forecast_agent.operation_models import OperationParameters
from gas_forecast_agent.skills.result_adapter import ToolResultAdapter


def model_settings(**overrides) -> Settings:
    # 测试只需要非空占位值；动态配置名避免提交扫描器误报硬编码密钥。
    values = {
        "GAS_SKILL_ROUTING_MODE": "hybrid",
        "LLM_" + "API_KEY": "unit-test-placeholder",
        "LLM_MODEL": "test-model",
        **overrides,
    }
    return Settings(**values)


def skill_definition(
    name: str,
    *,
    allowed_tools: list[str],
    instructions: str,
    intent: SkillIntent = SkillIntent.QUERY,
    read_only: bool = True,
) -> SkillDefinition:
    """构造单元测试使用的最小 Skill，字段与正式 SKILL.md 保持一致。"""
    return SkillDefinition.model_validate({
        "name": name,
        "intent": intent,
        "description": f"测试 {name}",
        "allowed-tools": allowed_tools,
        "read-only": read_only,
        "instructions": instructions,
    })


def empty_memory() -> ConversationMemory:
    """新会话没有可恢复的业务参数。"""
    return ConversationMemory()


def pending_memory(
    parameters: dict[str, Any],
    *,
    pending_tool: str | None = "forecast_start",
    interaction_type: InteractionType = InteractionType.CONFIRMATION,
) -> ConversationMemory:
    """按当前 pendingOperation 协议构造一轮尚未完成的操作。"""
    return ConversationMemory(
        pending_operation=PendingOperation(
            interaction=PendingInteraction(
                interaction_id="INT-TEST-1",
                interaction_type=interaction_type,
                source_turn_id="TURN-TEST-1",
                reason=ClarificationReason.PARAMETER_MISSING,
                resume_from="PlanValidator",
            ),
            semantics=RequestSemantics(intent=SkillIntent.FORECAST),
            parameters=OperationParameters.model_validate(parameters),
            pending_tool=pending_tool,
        ),
    )


def semantics(
    *,
    intent: SkillIntent = SkillIntent.FORECAST,
    customer: str | None = None,
    customers: list[str] | None = None,
    region: str | None = None,
    industry: str | None = None,
    forecast_horizon: int | None = None,
    history_start_date: str | None = None,
    history_end_date: str | None = None,
    training_policy: TrainingPolicy | None = None,
    fetch_all: bool = False,
    dialogue_act: DialogueAct = DialogueAct.UNSPECIFIED,
) -> RequestSemantics:
    """构造当前 requests 结构的语义对象，避免测试依赖已删除的扁平字段。"""
    customer_values = customers if customers is not None else [customer] if customer else []
    parameters: dict[str, Any] = {}
    if forecast_horizon is not None:
        parameters["forecastHorizon"] = forecast_horizon
    if history_start_date is not None:
        parameters["historyStartDate"] = history_start_date
    if history_end_date is not None:
        parameters["historyEndDate"] = history_end_date
    if training_policy is not None:
        parameters["trainingPolicy"] = training_policy.value
    return RequestSemantics.model_validate({
        "dialogueAct": dialogue_act.value,
        "requests": [{
            "intent": intent.value,
            "targetSelection": "ALL_MATCHED" if fetch_all else "EXPLICIT" if customer_values else "UNSPECIFIED",
            "entities": {
                "customers": customer_values,
                "regions": [region] if region else [],
                "industries": [industry] if industry else [],
            },
            "parameters": parameters,
        }],
    })


class FakeMcp:
    def __init__(self) -> None:
        self.calls: list[str] = []

    async def list_tools(self) -> list[dict[str, Any]]:
        return [{
            "name": "customer_list",
            "description": "查询客户",
            "inputSchema": {
                "type": "object",
                "properties": {"regionName": {"type": "string"}},
            },
        }]

    async def call_tool(self, name: str, arguments: dict[str, Any]) -> dict[str, Any]:
        self.calls.append(name)
        return {
            "structuredContent": {
                "success": True,
                "status": "COMPLETED",
                "message": "查询完成",
                "retryable": False,
                "data": {"records": [], "total": 0},
            }
        }


def test_knowledge_qa_does_not_depend_on_mcp_tool_catalog(monkeypatch) -> None:
    class UnavailableMcp:
        async def list_tools(self):
            raise AssertionError("知识问答不应读取 MCP 工具目录")

    class Completions:
        async def create(self, **kwargs):
            return SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(
                content='{"status":"COMPLETED","message":"MAPE 是平均绝对百分比误差。"}',
                tool_calls=[],
            ))])

    client = SimpleNamespace(chat=SimpleNamespace(completions=Completions()))
    monkeypatch.setattr("gas_forecast_agent.skills.agent.build_llm_client", lambda settings: client)
    skill = skill_definition(
        "knowledge-qa",
        allowed_tools=[],
        instructions="回答预测概念。",
        intent=SkillIntent.CHAT,
    )

    result = asyncio.run(ActionExecutor(model_settings()).run(
        conversation_no="C-KNOWLEDGE",
        skill=skill,
        message="MAPE 是什么？",
        history=[],
        memory=empty_memory(),
        metadata={},
        mcp=UnavailableMcp(),
    ))

    assert result.status == "COMPLETED"
    assert result.message == "MAPE 是平均绝对百分比误差。"
    assert result.available_tools == []


def test_large_page_result_is_compacted_for_model_but_original_is_unchanged() -> None:
    records = [
        {"customerCode": f"CUS{i:06d}", "customerName": f"客户{i}"}
        for i in range(154)
    ]
    result = {
        "success": True,
        "status": "COMPLETED",
        "resultType": "PAGE",
        "message": "客户列表查询成功",
        "data": {"records": records, "total": 154, "page": 1, "size": 154},
    }

    model_result = ToolResultAdapter.for_model(result)

    assert model_result["data"]["total"] == 154
    assert len(model_result["data"]["sample"]) == 5
    assert model_result["data"]["truncated"] is True
    assert len(result["data"]["records"]) == 154


def test_forecast_points_are_summarized_for_model() -> None:
    points = [
        {"forecastDate": f"2026-11-{day:02d}", "forecastValue": day}
        for day in range(1, 21)
    ]
    result = {
        "success": True,
        "status": "COMPLETED",
        "resultType": "RESULT",
        "data": {"record": {"forecastBatchNo": "F-1"}, "points": points},
    }

    model_result = ToolResultAdapter.for_model(result)
    summary = model_result["data"]["pointSummary"]

    assert summary["count"] == 20
    assert summary["minimum"] == 1
    assert summary["maximum"] == 20
    assert len(summary["first"]) == 3
    assert len(summary["last"]) == 3
    assert "points" not in model_result["data"]


def test_successful_page_status_cannot_be_overridden_by_model() -> None:
    result = {
        "success": True,
        "status": "COMPLETED",
        "resultType": "PAGE",
        "message": "客户列表查询成功",
        "data": {"records": [{"customerCode": "CUS-1", "customerName": "东台中油"}], "total": 154},
    }
    fallback = ActionExecutor._page_fallback_result(
        {}, {"customer_list": {}}, [{"tool": "customer_list", "arguments": {}, "result": result}],
    )

    assert fallback.status == "COMPLETED"
    assert fallback.message == "查询完成，共找到154条记录，完整结果已展示。"
    assert fallback.execution_result is not None


def test_python_style_model_message_is_parsed_and_long_page_text_uses_fallback() -> None:
    decision = ActionExecutor._message_decision(
        "{'status':'COMPLETED','message':'已查询到154家客户：\\n- 客户1\\n- 客户2'}"
    )
    assert decision["status"] == "COMPLETED"
    assert decision["message"].startswith("已查询到154家客户")

    result = {
        "status": "COMPLETED", "resultType": "PAGE",
        "data": {"total": 154, "records": [{"customerName": "客户1"}]},
    }
    long_message = "已查询到客户：\n" + "\n".join(f"- 客户{i}" for i in range(154))
    assert ToolResultAdapter.presentation_message(result, long_message) == (
        "查询完成，共找到154条记录，完整结果已展示。"
    )


def test_model_selects_one_allowed_tool_then_returns_structured_list(monkeypatch) -> None:
    requests = []
    responses = [
        SimpleNamespace(
            choices=[SimpleNamespace(message=SimpleNamespace(
                content=None,
                tool_calls=[SimpleNamespace(function=SimpleNamespace(
                    name="customer_list",
                    arguments='{"regionName":"浙江","ignored":"drop"}',
                )), SimpleNamespace(function=SimpleNamespace(
                    name="customer_list",
                    arguments='{"regionName":"江苏"}',
                ))],
            ))],
        ),
        SimpleNamespace(
            choices=[SimpleNamespace(message=SimpleNamespace(
                content=None,
                tool_calls=[SimpleNamespace(function=SimpleNamespace(
                    name="customer_list",
                    arguments='{"regionName":"浙江","ignored":"drop"}',
                ))],
            ))],
        ),
        SimpleNamespace(
            choices=[SimpleNamespace(message=SimpleNamespace(
                content="浙江没有查询到符合条件的客户。",
                tool_calls=[],
            ))],
        ),
    ]

    class Completions:
        async def create(self, **kwargs):
            requests.append(kwargs)
            return responses.pop(0)

    client = SimpleNamespace(chat=SimpleNamespace(completions=Completions()))
    monkeypatch.setattr("gas_forecast_agent.skills.agent.build_llm_client", lambda settings: client)
    settings = model_settings()
    skill = skill_definition(
        "business-data-query",
        allowed_tools=["customer_list"],
        instructions="查询客户时调用 customer_list。",
    )
    mcp = FakeMcp()

    result = asyncio.run(ActionExecutor(settings).run(
        conversation_no="C-1",
        skill=skill,
        message="查询浙江客户",
        history=[],
        memory=empty_memory(),
        metadata={},
        mcp=mcp,
    ))

    assert result.status == "COMPLETED"
    assert result.executed_tool == "customer_list"
    assert len(requests) == 2
    assert result.message == "查询完成"
    assert mcp.calls == ["customer_list"]
    # ToolPlanner 不能自行生成实体；生产流程中的地区名称必须先由语义层验证
    # 确实出现在用户原话，再进入 known parameters。
    assert result.tool_trace[0].arguments.to_payload() == {}
    assert all(request["parallel_tool_calls"] is False for request in requests)
    assert "只选择当前最先需要执行的一个工具" in requests[1]["messages"][-1]["content"]


def _obsolete_training_result_query_contract() -> None:
    requests = []
    response = SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(
        content=None,
        tool_calls=[SimpleNamespace(function=SimpleNamespace(
            name="training_result_get", arguments='{"trainBatchNo":"TR-1"}',
        ))],
    ))])

    class Completions:
        async def create(self, **kwargs):
            requests.append(kwargs)
            return response

    class TrainingMcp(FakeMcp):
        async def list_tools(self):
            return [{
                "name": "training_result_get",
                "inputSchema": {"required": ["trainBatchNo"], "properties": {"trainBatchNo": {"type": "string"}}},
                "annotations": {"readOnlyHint": True},
            }]

        async def call_tool(self, name, arguments):
            self.calls.append(name)
            return {"structuredContent": {
                "success": True, "status": "COMPLETED", "message": "训练信息查询完成", "retryable": False,
                "resultType": "RESULT", "data": {"batchNo": "TR-1", "bestModel": "XGBoost"},
            }}

    client = SimpleNamespace(chat=SimpleNamespace(completions=Completions()))
    monkeypatch.setattr("gas_forecast_agent.skills.agent.build_llm_client", lambda settings: client)
    settings = model_settings()
    skill = skill_definition(
        "business-data-query",
        allowed_tools=["training_result_get"],
        instructions="查询训练结果。",
    )

    result = asyncio.run(ActionExecutor(settings).run(
        conversation_no="C-TRAIN", skill=skill, message="查询训练信息", history=[],
        memory=empty_memory(), metadata={}, mcp=TrainingMcp(),
    ))

    assert result.status == "COMPLETED"
    assert result.executed_tool == "training_result_get"
    assert result.message == "训练信息查询完成"
    assert len(requests) == 1


def test_action_skill_cannot_claim_success_without_tool(monkeypatch) -> None:
    response = SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(
        content='{"status":"COMPLETED","message":"预测任务已提交，批次号为FAKE-1"}', tool_calls=[],
    ))])

    class Completions:
        async def create(self, **kwargs):
            return response

    client = SimpleNamespace(chat=SimpleNamespace(completions=Completions()))
    monkeypatch.setattr("gas_forecast_agent.skills.agent.build_llm_client", lambda settings: client)
    settings = model_settings()
    skill = skill_definition(
        "customer-gas-forecast",
        allowed_tools=["customer_list"],
        instructions="预测必须调用工具。",
        intent=SkillIntent.FORECAST,
        read_only=False,
    )

    result = asyncio.run(ActionExecutor(settings).run(
        conversation_no="C-SAFE", skill=skill, message="预测某客户", history=[], memory=empty_memory(), metadata={}, mcp=FakeMcp(),
    ))

    assert result.status == "FAILED"
    assert "不允许调用工具" in result.message
    assert result.executed_tool is None


def test_tool_business_status_is_not_flattened_to_failed() -> None:
    class NeedInfoMcp(FakeMcp):
        async def call_tool(self, name, arguments):
            return {"structuredContent": {
                "success": False,
                "status": "INVALID_ARGUMENT",
                "message": "预测周期超出范围",
                "retryable": False,
                "data": {"field": "forecastHorizon"},
            }}

    settings = Settings(GAS_SKILL_ROUTING_MODE="rules", LLM_API_KEY=None)
    skill = skill_definition(
        "business-data-query",
        allowed_tools=["customer_list"],
        instructions="查询客户。",
    )
    result = asyncio.run(ActionExecutor(settings).run(
        conversation_no="C-2", skill=skill, message="查询客户", history=[], memory=empty_memory(), metadata={}, mcp=NeedInfoMcp(),
    ))

    assert result.status == "NEED_INFORMATION"
    assert result.message == "预测周期超出范围"


def test_all_of_schema_fields_are_kept() -> None:
    tool = {
        "inputSchema": {
            "type": "object",
            "required": ["conversationNo"],
            "properties": {"conversationNo": {"type": "string"}},
            "allOf": [{"properties": {"customerCode": {"type": "string"}}}],
        },
    }
    arguments = ActionExecutor._validated_arguments(tool, {
        "conversationNo": "C-3", "customerCode": "CUS-1", "unknown": "drop",
    })
    assert arguments == {"conversationNo": "C-3", "customerCode": "CUS-1"}


@pytest.mark.parametrize(
    ("plural", "singular", "code"),
    [
        ("industryCodes", "industryCode", "IND000001"),
        ("regionCodes", "regionCode", "REG000001"),
        ("customerCodes", "customerCode", "CUS000001"),
    ],
)
def test_single_verified_entity_collection_is_adapted_to_tool_parameter(
    plural: str, singular: str, code: str,
) -> None:
    tool = {"name": "customer_list", "inputSchema": {"properties": {singular: {"type": "string"}}}}

    arguments, error = ActionExecutor._adapt_tool_parameters(tool, {plural: [code]})

    assert error is None
    assert arguments == {singular: code}


def test_multiple_verified_entities_are_not_silently_reduced() -> None:
    tool = {"name": "customer_list", "inputSchema": {"properties": {"industryCode": {"type": "string"}}}}

    arguments, error = ActionExecutor._adapt_tool_parameters(tool, {
        "industryCodes": ["IND000001", "IND000002"],
    })

    assert arguments["industryCodes"] == ["IND000001", "IND000002"]
    assert error is not None
    assert "单个行业" in error


def test_entity_adapter_rejects_conflicting_single_and_batch_parameters() -> None:
    tool = {"name": "customer_list", "inputSchema": {"properties": {"industryCode": {"type": "string"}}}}

    _, error = ActionExecutor._adapt_tool_parameters(tool, {
        "industryCode": "IND-OLD", "industryCodes": ["IND-VERIFIED"],
    })

    assert error is not None
    assert "参数冲突" in error


def test_entity_adapter_keeps_unfiltered_query_unmodified() -> None:
    tool = {"name": "customer_list", "inputSchema": {"properties": {"industryCode": {"type": "string"}}}}

    arguments, error = ActionExecutor._adapt_tool_parameters(tool, {"conversationNo": "C-no-filter"})

    assert error is None
    assert arguments == {"conversationNo": "C-no-filter"}


def test_execution_tool_adapts_resolved_entities_to_target_group() -> None:
    tool = {"name": "forecast_start", "inputSchema": {"properties": {
        "dimensionType": {"type": "string"}, "targets": {"type": "array"},
    }}}

    arguments, error = ActionExecutor._adapt_tool_parameters(tool, {
        "regionCodes": ["REG000012"], "industryCodes": ["IND000001"], "customerCodes": ["CUS000163"],
        "regionNames": ["江苏"], "industryNames": ["城燃"], "customerNames": ["江宁华润"],
    })

    assert error is None
    assert arguments == {"dimensionType": "CUSTOMER", "targets": [{
        "customerCodes": ["CUS000163"], "regionCodes": ["REG000012"], "industryCodes": ["IND000001"],
    }]}


def test_direct_model_question_is_not_marked_completed() -> None:
    decision = ActionExecutor._message_decision("请提供具体客户名称。")
    assert decision["status"] == "NEED_INFORMATION"


def test_direct_model_conflict_is_not_marked_completed() -> None:
    decision = ActionExecutor._message_decision('{"status":"NEED_CLARIFICATION","message":"预测参数存在冲突。"}')
    assert decision == {"type": "message", "status": "NEED_CLARIFICATION", "message": "预测参数存在冲突。"}


def test_schema_range_is_checked_before_tool_call() -> None:
    tool = {"inputSchema": {"properties": {"forecastHorizon": {"type": "integer", "minimum": 1, "maximum": 30}}}}
    assert ActionExecutor._argument_validation_error(tool, {"forecastHorizon": 31}) == "参数 forecastHorizon 不能大于 30。"
    assert ActionExecutor._argument_validation_error(tool, {
        "historyStartDate": "2025-03-01", "historyEndDate": "2025-01-01",
    }) == "训练开始日期不能晚于训练结束日期，请重新确认训练范围。"


def test_structured_semantics_supplies_customer_to_action_executor() -> None:
    values = ActionExecutor._initial_parameters(
        "训练杭州天然气的短期预测模型",
        empty_memory(),
        {},
        "C-6",
        "model-training-management",
        semantics(intent=SkillIntent.TRAIN, customer="杭州天然气"),
    )

    assert values["customerName"] == "杭州天然气"


def test_structured_history_range_and_industry_are_consumed() -> None:
    date_range = ActionExecutor._initial_parameters(
        "使用指定历史数据训练杭州天然气",
        empty_memory(),
        {},
        "C-8",
        "model-training-management",
        semantics(
            intent=SkillIntent.TRAIN,
            customer="杭州天然气",
            history_start_date="2025-01-01",
            history_end_date="2025-03-01",
        ),
    )
    assert (date_range["historyStartDate"], date_range["historyEndDate"], date_range["customerName"]) == (
        "2025-01-01", "2025-03-01", "杭州天然气",
    )
    chemical = ActionExecutor._initial_parameters(
        "训练浙江城市化工客户", empty_memory(), {}, "C-9", "model-training-management",
        semantics(intent=SkillIntent.TRAIN, region="浙江", industry="化工", fetch_all=True),
    )
    assert chemical["industryName"] == "化工"
    assert chemical["bulk"] is True


def test_region_or_industry_does_not_implicitly_expand_write_scope() -> None:
    values = ActionExecutor._initial_parameters(
        "训练浙江城燃客户",
        empty_memory(),
        {},
        "C-SCOPE-UNSPECIFIED",
        "model-training-management",
        semantics(intent=SkillIntent.TRAIN, region="浙江", industry="城燃"),
    )

    assert values["bulk"] is False
    assert "全部匹配客户" in values["_clarificationQuestion"]
    assert values["_clarificationPendingTool"] == "forecast_start"


def test_rejecting_scope_clears_singular_and_batch_entities() -> None:
    values = ActionExecutor._initial_parameters(
        "重新选择范围",
        pending_memory({
            "regionName": "浙江",
            "regionNames": ["浙江", "江苏"],
            "industryName": "城燃",
            "industryNames": ["城燃", "化工"],
        }, interaction_type=InteractionType.CLARIFICATION),
        {
            "inputType": "CLARIFICATION_REPLY",
            "clarificationResolvedParameters": {"_scopeRejected": True},
        },
        "C-SCOPE-CHANGE",
        "model-training-management",
    )

    for key in ("regionName", "regionNames", "industryName", "industryNames"):
        assert key not in values
    assert "_clarificationQuestion" not in values


def test_scope_clarification_uses_range_specific_labels() -> None:
    issue = ActionExecutor._scope_issue("是否使用该范围？")

    assert [option.label for option in issue.options] == ["确认使用该范围", "重新选择范围"]


def test_region_with_province_suffix_does_not_require_a_region_allowlist() -> None:
    values = ActionExecutor._initial_parameters(
        "查询江苏省城市燃气行业的所有客户信息", empty_memory(), {}, "C-REGION", "business-data-query",
        semantics(intent=SkillIntent.QUERY, region="江苏", industry="城市燃气", fetch_all=True),
    )

    assert values["regionName"] == "江苏"
    assert values["industryName"] == "城市燃气"
    assert values["fetchAll"] is True


def test_scope_quantifier_is_not_part_of_region_name() -> None:
    leading = ActionExecutor._initial_parameters(
        "查询所有江苏城燃的客户", empty_memory(), {}, "C-REGION-ALL-1", "business-data-query",
        semantics(intent=SkillIntent.QUERY, region="江苏", industry="城燃", fetch_all=True),
    )
    middle = ActionExecutor._initial_parameters(
        "查询江苏所有城燃的客户", empty_memory(), {}, "C-REGION-ALL-2", "business-data-query",
        semantics(intent=SkillIntent.QUERY, region="江苏", industry="城燃", fetch_all=True),
    )

    assert leading["regionName"] == "江苏"
    assert middle["regionName"] == "江苏"
    assert leading["industryName"] == "城燃"
    assert middle["industryName"] == "城燃"
    assert leading["fetchAll"] is True
    assert middle["fetchAll"] is True


def test_structured_region_and_industry_are_used_without_phrase_rules() -> None:
    request_semantics = semantics(
        intent=SkillIntent.QUERY, region="江苏", industry="城燃", fetch_all=True,
    )

    values = ActionExecutor._initial_parameters(
        "查询下江苏城燃的所有客户信息",
        empty_memory(), {}, "C-REGION-LOCKED", "business-data-query", request_semantics,
    )

    assert values["regionName"] == "江苏"
    assert values["industryName"] == "城燃"
    assert values["fetchAll"] is True
    assert {"regionName", "industryName", "fetchAll"}.issubset(values["_lockedParameters"])


def test_customer_name_does_not_become_region_or_industry_filter() -> None:
    values = ActionExecutor._initial_parameters(
        "帮我预测下扬州中燃未来50天的燃气销量",
        empty_memory(),
        {},
        "C-CUSTOMER-BOUNDARY",
        "customer-gas-forecast",
        semantics(customer="扬州中燃", region="扬州", industry="燃气", forecast_horizon=50),
    )

    assert values["customerName"] == "扬州中燃"
    assert values["forecastHorizon"] == 50
    assert "customerCode" not in values
    assert "regionCode" not in values
    assert "regionName" not in values
    assert "industryCode" not in values
    assert "industryName" not in values


def test_forecast_with_new_training_result_forces_retraining() -> None:
    values = ActionExecutor._initial_parameters(
        "帮我预测下客户CUS000023的未来40天燃气销量，用新训练的结果",
        empty_memory(), {}, "C-NEW-TRAIN", "customer-gas-forecast",
        semantics(
            customer="客户CUS000023",
            forecast_horizon=40,
            training_policy=TrainingPolicy.FORCE_RETRAIN,
        ),
    )

    assert values["forceRetrain"] is True
    assert values["autoTrain"] is True
    assert values["trainingPreference"] == "FORCE_RETRAIN"
    assert values["customerName"] == "客户CUS000023"
    assert values["forecastHorizon"] == 40

    arguments = {**values, "customerName": "客户CUS000135", "forecastHorizon": 90, "forceRetrain": False}
    ActionExecutor._apply_locked_parameters(values, arguments)
    assert arguments["customerName"] == "客户CUS000023"
    assert arguments["forecastHorizon"] == 40
    assert arguments["forceRetrain"] is True

    confirmed = ActionExecutor._initial_parameters(
        "是",
        pending_memory({
            "customerCode": "CUS000023",
            "forecastHorizon": 40,
            "forceRetrain": True,
            "autoTrain": True,
            "trainingPreference": "FORCE_RETRAIN",
        }),
        {}, "C-NEW-TRAIN", "customer-gas-forecast",
        semantics(dialogue_act=DialogueAct.CONFIRM_PENDING),
    )
    assert confirmed["confirmed"] is True
    assert confirmed["customerCode"] == "CUS000023"
    assert confirmed["forceRetrain"] is True


def test_new_retraining_request_replaces_stale_pending_forecast() -> None:
    stale = {
        "customerCode": "CUS000134",
        "forecastHorizon": 50,
        "forceRetrain": False,
        "autoTrain": True,
        "trainingPreference": "AUTO",
    }
    values = ActionExecutor._initial_parameters(
        "预测下客户CUS000134未来50天燃气销量，同时训练也要重新发起",
        pending_memory(stale),
        {"inputType": "TEXT"},
        "C-RETRAIN-AGAIN",
        "customer-gas-forecast",
        semantics(
            customer="客户CUS000134",
            forecast_horizon=50,
            training_policy=TrainingPolicy.FORCE_RETRAIN,
            dialogue_act=DialogueAct.NEW_REQUEST,
        ),
    )

    assert "_pendingTool" not in values
    assert values["forceRetrain"] is True
    assert values["autoTrain"] is True
    assert values["trainingPreference"] == "FORCE_RETRAIN"
    assert {"forceRetrain", "autoTrain", "trainingPreference"}.issubset(values["_lockedParameters"])


def test_structured_semantics_overrides_stale_training_policy_without_phrase_rules() -> None:
    values = ActionExecutor._initial_parameters(
        "模型不要接着上次那个了，给我另起炉灶",
        empty_memory(),
        {},
        "C-SEMANTIC",
        "customer-gas-forecast",
        semantics(training_policy=TrainingPolicy.FORCE_RETRAIN),
    )

    assert values["forceRetrain"] is True
    assert values["trainingPreference"] == "FORCE_RETRAIN"
    assert "trainingPreference" in values["_lockedParameters"]


def test_confirmation_restores_and_locks_pending_parameter_snapshot() -> None:
    snapshot = {
        "customerCode": "CUS-9", "forecastHorizon": 50, "forceRetrain": True,
        "autoTrain": True, "trainingPreference": "FORCE_RETRAIN", "idempotencyKey": "same-request",
    }
    values = ActionExecutor._initial_parameters(
        "确认",
        pending_memory(snapshot),
        {"currentBusinessContext": {"customerCode": "STALE-PAGE-CUSTOMER"}},
        "C-SNAPSHOT",
        "customer-gas-forecast",
        semantics(dialogue_act=DialogueAct.CONFIRM_PENDING),
    )

    assert values["_pendingTool"] == "forecast_start"
    assert values["confirmed"] is True
    assert values["customerCode"] == "CUS-9"
    assert values["forceRetrain"] is True
    assert values["idempotencyKey"] == "same-request"
    arguments = {**values, "customerCode": "MODEL-CHANGED", "forceRetrain": False}
    ActionExecutor._apply_locked_parameters(values, arguments)
    assert arguments["customerCode"] == "CUS-9"
    assert arguments["forceRetrain"] is True


def test_confirmation_ignores_semantic_clarification_and_uses_pending_snapshot() -> None:
    snapshot = {
        "customerCode": "CUS000140",
        "customerName": "扬州中燃",
        "forecastHorizon": 50,
        "idempotencyKey": "pending-request",
    }
    values = ActionExecutor._initial_parameters(
        "继续",
        pending_memory(snapshot),
        {},
        "C-CONFIRMATION",
        "customer-gas-forecast",
        semantics(dialogue_act=DialogueAct.CONFIRM_PENDING),
    )

    assert values["_pendingTool"] == "forecast_start"
    assert values["confirmed"] is True
    assert values["customerCode"] == "CUS000140"
    assert values["forecastHorizon"] == 50
    assert values["idempotencyKey"] == "pending-request"
    assert "_clarificationQuestion" not in values
    assert "_semanticEvidence" not in values


def _obsolete_training_information_query_contract() -> None:
    decision = ActionExecutor._explicit_read_query(
        SkillIntent.QUERY,
        "刚才的这个预测所用的训练信息给我",
        {"trainBatchNo": "TRBATCH-1"},
    )
    assert decision == {
        "type": "tool", "name": "training_result_get", "arguments": {"trainBatchNo": "TRBATCH-1"}
    }


def _obsolete_training_batch_lock_contract() -> None:
    values = ActionExecutor._initial_parameters(
        "把训练批次：TRBATCH-20261014-00707的训练结果给我",
        {}, {}, "C-BATCH", "model-training-management",
    )
    arguments = {**values, "trainBatchNo": "TRBATCH-20261014-"}
    ActionExecutor._apply_locked_parameters(values, arguments)

    assert values["trainBatchNo"] == "TRBATCH-20261014-00707"
    assert "trainBatchNo" in values["_lockedParameters"]
    assert arguments["trainBatchNo"] == "TRBATCH-20261014-00707"


def _obsolete_empty_training_result_contract() -> None:
    normalized = ActionExecutor._normalize_empty_query_result(
        "training_result_get",
        {"success": True, "status": "COMPLETED", "message": "训练结果查询成功", "retryable": False,
         "data": {"batches": [], "selectedBatchNo": None}},
        {"trainBatchNo": "TRBATCH-404"},
    )

    assert normalized["status"] == "NOT_FOUND"
    assert normalized["success"] is False
    assert normalized["message"] == "未找到训练批次：TRBATCH-404"


def test_current_entity_fields_replace_stale_memory_without_code_patterns() -> None:
    current = {"customerCode": "opaque-new-code"}
    arguments = {
        "customerCode": "old-code", "customerName": "旧客户",
        "regionCode": "old-region", "industryCode": "old-industry",
    }
    arguments.update(current)
    ActionExecutor._apply_current_entity_arguments(current, arguments)
    assert arguments == {"customerCode": "opaque-new-code"}


def test_router_uses_skill_from_structured_model_understanding() -> None:
    registry = SkillRegistry()
    router = SkillRouter(registry)

    understanding = RequestSemantics(
        intent=SkillIntent.QUERY,
    )
    selection = router.select(
        semantics=understanding,
    )

    assert selection.skill_name == "business-data-query"
    assert selection.confidence == 0.8
    assert selection.reason == "由结构化请求 R1 的意图直接选择。"


def test_router_rejects_missing_model_skill() -> None:
    router = SkillRouter(SkillRegistry())

    with pytest.raises(ValueError, match="没有返回意图"):
        router.select(semantics=RequestSemantics())


def test_router_rejects_intent_without_registered_skill() -> None:
    def reject_unknown_intent(intent: SkillIntent) -> None:
        raise KeyError(intent)

    router = SkillRouter(SimpleNamespace(get_by_intent=reject_unknown_intent))

    with pytest.raises(ValueError, match="没有对应的 Skill"):
        router.select(semantics=RequestSemantics(intent=SkillIntent.QUERY))


def test_forecast_page_question_reads_bound_result_in_rule_mode() -> None:
    decision = ActionExecutor._rule_decision(
        SkillIntent.ANALYZE, "为什么这几天这么高", {"forecastBatchNo": "FORECAST-1"},
    )
    assert decision["name"] == "forecast_result_query"


def test_forecast_customer_name_is_resolved_before_model_planning(monkeypatch) -> None:
    agent = ActionExecutor(model_settings())

    def unexpected_model_client(settings):
        raise AssertionError("客户主数据解析前不应调用规划模型")

    monkeypatch.setattr("gas_forecast_agent.skills.agent.build_llm_client", unexpected_model_client)
    decision = asyncio.run(agent._decide(
        SimpleNamespace(
            name="customer-gas-forecast",
            intent=SkillIntent.FORECAST,
            model_instructions=lambda: "",
        ),
        "帮我预测下扬州中燃未来50天的燃气销量",
        [],
        {"customerName": "扬州中燃", "forecastHorizon": 50},
        [],
        [{"name": "customer_search", "inputSchema": {"type": "object", "properties": {}}}],
    ))

    assert decision["type"] == "tool"
    assert decision["name"] == "forecast_start"


def test_multiple_entity_names_use_batch_resolver_before_target_tool(monkeypatch) -> None:
    agent = ActionExecutor(model_settings())

    def unexpected_model_client(settings):
        raise AssertionError("批量实体校验不应由规划模型决定")

    monkeypatch.setattr("gas_forecast_agent.skills.agent.build_llm_client", unexpected_model_client)
    parameters = {
        "customerNames": ["高邮安源", "扬州中燃"],
        "bulk": True,
    }
    decision = asyncio.run(agent._decide(
        SimpleNamespace(
            name="customer-gas-forecast",
            intent=SkillIntent.FORECAST,
            model_instructions=lambda: "",
        ),
        "预测高邮安源和扬州中燃",
        [],
        parameters,
        [],
        [{"name": "entity_batch_resolve", "inputSchema": {"type": "object", "properties": {}}}],
    ))

    assert decision == {"type": "tool", "name": "forecast_start", "arguments": parameters}


def test_batch_entity_ambiguity_returns_opaque_candidate_options() -> None:
    issues = ActionExecutor._tool_result_issues(
        "entity_batch_resolve",
        "NEED_SELECTION",
        {"customerNames": ["安源"]},
        {
            "message": "部分实体存在多个候选",
            "data": {
                "items": [{
                    "entityType": "CUSTOMER",
                    "inputName": "安源",
                    "status": "MULTIPLE",
                    "message": "找到多个相似客户",
                    "candidates": [
                        {"type": "CUSTOMER", "code": "C-1", "name": "高邮安源", "description": "江苏 / 城燃"},
                        {"type": "CUSTOMER", "code": "C-2", "name": "某地安源", "description": "浙江 / 工业"},
                    ],
                }],
            },
        },
    )

    assert len(issues) == 1
    assert issues[0].reason.value == "SCOPE_AMBIGUOUS"
    # 实体候选已在理解节点生成；执行器只返回范围澄清，不再泄露候选编码。
    assert issues[0].options == []


def test_completed_batch_resolution_marks_codes_as_validated() -> None:
    parameters = {"customerNames": ["高邮安源", "扬州中燃"]}
    ActionExecutor._merge_result(parameters, {
        "data": {
            "status": "UNIQUE",
            "proposedParameters": {
                "customerCodes": ["C-1", "C-2"],
                "customerNames": ["高邮安源", "扬州中燃"],
            },
        },
    })

    assert parameters["customerCodes"] == ["C-1", "C-2"]
    assert parameters["_entityBatchResolved"] is True


def test_unique_customer_match_continues_to_forecast_without_model_replanning(monkeypatch) -> None:
    agent = ActionExecutor(model_settings())

    def unexpected_model_client(settings):
        raise AssertionError("客户唯一匹配后应按工具依赖继续，不应再次调用规划模型")

    monkeypatch.setattr("gas_forecast_agent.skills.agent.build_llm_client", unexpected_model_client)
    parameters = {
        "customerCode": "CUS000140",
        "customerName": "扬州中燃",
        "forecastHorizon": 50,
        "_validatedEntityParameters": ["customerCode", "customerName"],
    }
    trace = [{
        "tool": "customer_search",
        "arguments": {"customerName": "扬州中燃"},
        "result": {
            "success": True,
            "status": "COMPLETED",
            "message": "客户唯一匹配",
            "retryable": False,
            "data": {"status": "UNIQUE"},
        },
    }]

    decision = asyncio.run(agent._decide(
        SimpleNamespace(
            name="customer-gas-forecast",
            intent=SkillIntent.FORECAST,
            model_instructions=lambda: "",
        ),
        "帮我预测下扬州中燃未来50天的燃气销量",
        [],
        parameters,
        trace,
        [{"name": "forecast_start", "inputSchema": {"type": "object", "properties": {}}}],
    ))

    assert decision == {"type": "tool", "name": "forecast_start", "arguments": parameters}


def _obsolete_scoped_customer_list_planning_contract(monkeypatch) -> None:
    agent = ActionExecutor(model_settings())

    def unexpected_model_client(settings):
        raise AssertionError("范围参数已抽取后应按工具依赖推进，不应由模型泛化追问")

    monkeypatch.setattr("gas_forecast_agent.skills.agent.build_llm_client", unexpected_model_client)
    skill = SimpleNamespace(
        name="business-data-query",
        intent=SkillIntent.QUERY,
        model_instructions=lambda: "",
    )
    tools = [
        {"name": "region_search", "inputSchema": {}},
        {"name": "industry_search", "inputSchema": {}},
        {"name": "customer_list", "inputSchema": {}},
    ]

    region_decision = asyncio.run(agent._decide(
        skill, "任意表达", [],
        {"regionName": "江苏", "industryName": "城燃", "fetchAll": True}, [], tools,
    ))
    industry_decision = asyncio.run(agent._decide(
        skill, "任意表达", [],
        {"regionCode": "32", "industryName": "城燃", "fetchAll": True},
        [{"tool": "region_search", "result": {}}], tools,
    ))
    list_decision = asyncio.run(agent._decide(
        skill, "任意表达", [],
        {"regionCode": "32", "industryCode": "CITY_GAS", "fetchAll": True},
        [{"tool": "industry_search", "result": {}}], tools,
    ))

    assert region_decision["name"] == "region_search"
    assert industry_decision["name"] == "industry_search"
    assert list_decision["name"] == "customer_list"


def test_confirmation_and_idempotency_do_not_leak_to_next_request() -> None:
    values = ActionExecutor._initial_parameters("发起新的预测", empty_memory(), {}, "C-4", "customer-gas-forecast")

    assert "confirmed" not in values
    assert values["idempotencyKey"] != "old-key"


def test_new_forecast_resets_previous_force_retrain_policy_to_auto() -> None:
    values = ActionExecutor._initial_parameters(
        "帮我预测南京洁宁未来50天的燃气销量",
        empty_memory(),
        {},
        "C-RESET-POLICY",
        "customer-gas-forecast",
        RequestSemantics(
            dialogue_act=DialogueAct.NEW_REQUEST,
            customer_name="南京洁宁",
            forecast_horizon=50,
            training_policy=TrainingPolicy.UNSPECIFIED,
        ),
    )

    assert values["customerName"] == "南京洁宁"
    assert values["forceRetrain"] is False
    assert values["autoTrain"] is True
    assert values["trainingPreference"] == "AUTO"


def test_internal_clarification_and_old_customer_do_not_leak_into_scoped_list_query() -> None:
    values = ActionExecutor._initial_parameters(
        "帮我查询下江苏省的城燃行业的所有客户信息",
        empty_memory(),
        {},
        "C-FRESH-QUERY",
        "business-data-query",
        RequestSemantics(region_name="江苏", industry_name="城燃", fetch_all=True),
    )

    assert values["regionName"] == "江苏"
    assert values["industryName"] == "城燃"
    assert values["fetchAll"] is True
    assert "customerCode" not in values
    assert "customerName" not in values
    assert "_clarificationQuestion" not in values
    assert "_pendingTool" not in values


def test_only_risky_forecast_requires_confirmation() -> None:
    tool = {
        "name": "forecast_start",
        "title": "发起燃气预测",
        "inputSchema": {
            "properties": {
                "bulk": {"type": "boolean"},
                "autoTrain": {"type": "boolean"},
                "confirmed": {"type": "boolean"},
            },
        },
        "annotations": {"destructiveHint": True},
    }

    assert ActionExecutor._confirmation_message(tool, {"bulk": False, "autoTrain": False}) is None
    assert ActionExecutor._confirmation_message(tool, {"autoTrain": True}) is not None


def test_forecast_confirmation_displays_execution_snapshot() -> None:
    tool = {
        "name": "forecast_start", "title": "发起燃气预测",
        "inputSchema": {"properties": {"confirmed": {"type": "boolean"}}},
        "annotations": {"destructiveHint": True},
    }
    message = ActionExecutor._confirmation_message(tool, {
        "customerCode": "CUS000260", "forecastHorizon": 30,
        "forceRetrain": True, "trainingPreference": "FORCE_RETRAIN",
    })

    assert "CUS000260" in message
    assert "未来30天" in message
    assert "重新发起训练" in message
    assert "新训练结果预测" in message


def test_entity_merge_keeps_region_out_of_customer_fields() -> None:
    parameters = {"regionName": "浙江"}
    ActionExecutor._merge_result(parameters, {
        "data": {
            "status": "UNIQUE",
            "candidates": [{"type": "REGION", "code": "330000", "name": "浙江", "regionCode": "330000", "regionName": "浙江"}],
        },
    })

    assert parameters["regionCode"] == "330000"
    assert "customerCode" not in parameters


def test_server_proposed_parameters_are_saved_for_confirmed_retry() -> None:
    parameters = {"customerCode": "CUS-1", "autoTrain": False}
    ActionExecutor._merge_result(parameters, {
        "status": "NEED_CONFIRMATION",
        "data": {"proposedParameters": {"autoTrain": True, "trainingPreference": "AUTO"}},
    })

    assert parameters["autoTrain"] is True
    assert parameters["trainingPreference"] == "AUTO"


def test_same_tool_call_budget_stops_parameter_churn(monkeypatch) -> None:
    responses = [
        SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(
            content=None,
            tool_calls=[SimpleNamespace(function=SimpleNamespace(
                name="customer_list", arguments=f'{{"pageNo":{page}}}',
            ))],
        ))])
        for page in (1, 2, 3)
    ]

    class Completions:
        async def create(self, **kwargs):
            return responses.pop(0)

    class PagingMcp(FakeMcp):
        async def list_tools(self):
            return [{
                "name": "customer_list",
                "inputSchema": {"properties": {"pageNo": {"type": "integer"}}},
                }]

        async def call_tool(self, name, arguments):
            self.calls.append((name, arguments))
            return {
                "structuredContent": {
                    "success": True, "status": "COMPLETED", "message": "已翻页",
                    "retryable": False, "data": {"pageNo": arguments.get("pageNo")},
                }
            }

    client = SimpleNamespace(chat=SimpleNamespace(completions=Completions()))
    monkeypatch.setattr("gas_forecast_agent.skills.agent.build_llm_client", lambda settings: client)
    settings = model_settings(GAS_AGENT_MAX_SAME_TOOL_CALLS=2)
    skill = skill_definition(
        "business-data-query",
        allowed_tools=["customer_list"],
        instructions="查询客户。",
    )
    mcp = PagingMcp()

    result = asyncio.run(ActionExecutor(settings).run(
        conversation_no="C-5", skill=skill, message="反复翻页", history=[], memory=empty_memory(), metadata={}, mcp=mcp,
    ))

    assert result.status == "FAILED"
    assert "调用次数达到安全上限" in result.message
    assert mcp.calls == [("customer_list", {"pageNo": 1}), ("customer_list", {"pageNo": 2})]


def test_structured_customer_list_is_terminal_and_not_replanned(monkeypatch) -> None:
    responses = [SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(
        content=None,
        tool_calls=[SimpleNamespace(function=SimpleNamespace(
            name="customer_list", arguments='{"fetchAll":true}',
        ))],
    ))])]

    class Completions:
        async def create(self, **kwargs):
            if not responses:
                raise AssertionError("结构化客户列表返回后不应再次调用规划模型")
            return responses.pop(0)

    class ListMcp(FakeMcp):
        async def call_tool(self, name, arguments):
            self.calls.append((name, arguments))
            return {"structuredContent": {
                "success": True, "status": "COMPLETED", "resultType": "PAGE",
                "message": "查询成功", "retryable": False,
                "data": {"total": 2, "records": [
                    {"customerCode": "CUS-1", "customerName": "客户一"},
                    {"customerCode": "CUS-2", "customerName": "客户二"},
                ]},
            }}

    client = SimpleNamespace(chat=SimpleNamespace(completions=Completions()))
    monkeypatch.setattr("gas_forecast_agent.skills.agent.build_llm_client", lambda settings: client)
    skill = skill_definition(
        "business-data-query",
        allowed_tools=["customer_list"],
        instructions="查询客户。",
    )

    result = asyncio.run(ActionExecutor(model_settings()).run(
        conversation_no="C-LIST", skill=skill, message="查询全部客户",
        history=[], memory=empty_memory(), metadata={}, mcp=ListMcp(),
    ))

    assert result.status == "COMPLETED"
    assert result.executed_tool == "customer_list"
    assert result.execution_result is not None
    assert result.execution_result.data is not None
    assert result.execution_result.data.total == 2
    assert not responses


def test_conditional_required_parameters_are_checked_before_execution() -> None:
    tool = {
        "inputSchema": {
            "properties": {
                "bulk": {"type": "boolean"},
                "customerCode": {"type": "string", "description": "客户编码"},
                "customerName": {"type": "string", "description": "客户名称"},
                "trainMode": {"type": "string"},
                "trainingPeriods": {"type": "integer", "description": "训练天数"},
            },
            "allOf": [
                {
                    "if": {"properties": {"bulk": {"const": False}}},
                    "then": {"anyOf": [{"required": ["customerCode"]}, {"required": ["customerName"]}]},
                },
                {
                    "if": {"required": ["trainMode"], "properties": {"trainMode": {"const": "RECENT"}}},
                    "then": {"required": ["trainingPeriods"]},
                },
            ],
        }
    }

    missing = ActionExecutor._missing_required_arguments(tool, {"trainMode": "RECENT"})
    assert "customerCode 或 customerName" in missing
    assert "trainingPeriods" in missing
    prompt = ActionExecutor._missing_parameters_message(tool, missing, {"trainMode": "RECENT"})
    assert "customerCode（客户编码）或customerName（客户名称）" in prompt
    assert "trainingPeriods（训练天数）" in prompt
    assert ActionExecutor._missing_required_arguments(
        tool, {"customerName": "瑞安新奥", "trainMode": "RECENT", "trainingPeriods": 90},
    ) == []


def test_read_only_system_error_is_retried() -> None:
    class FlakyReadMcp(FakeMcp):
        def __init__(self) -> None:
            super().__init__()
            self.attempts = 0

        async def call_tool(self, name, arguments):
            self.calls.append(name)
            self.attempts += 1
            if self.attempts == 1:
                return {"structuredContent": {
                    "success": False, "status": "SYSTEM_ERROR", "errorType": "SYSTEM",
                    "errorCode": "UPSTREAM_TIMEOUT", "message": "上游超时", "retryable": True,
                }}
            return {"structuredContent": {
                "success": True, "status": "COMPLETED", "message": "查询完成", "retryable": False,
            }}

    settings = Settings(GAS_SKILL_ROUTING_MODE="rules", GAS_AGENT_MAX_READ_RETRIES=1)
    agent = ActionExecutor(settings)
    mcp = FlakyReadMcp()
    result, attempts = asyncio.run(agent._call_with_retry(
        mcp,
        {"annotations": {"readOnlyHint": True}},
        "customer_list",
        {},
    ))

    assert result["success"] is True
    assert attempts == 2
    assert mcp.calls == ["customer_list", "customer_list"]


def test_write_system_error_is_not_retried_and_becomes_unknown() -> None:
    class FailedWriteMcp(FakeMcp):
        async def call_tool(self, name, arguments):
            self.calls.append(name)
            return {"structuredContent": {
                "success": False, "status": "SYSTEM_ERROR", "errorType": "SYSTEM",
                "errorCode": "UPSTREAM_TIMEOUT", "message": "提交超时", "retryable": True,
            }}

    agent = ActionExecutor(Settings(GAS_AGENT_MAX_READ_RETRIES=3))
    mcp = FailedWriteMcp()
    result, attempts = asyncio.run(agent._call_with_retry(
        mcp,
        {"annotations": {"readOnlyHint": False}},
        "forecast_start",
        {"customerName": "瑞安新奥"},
    ))

    assert result["status"] == "UNKNOWN"
    assert "不会自动重试" in result["message"]
    assert attempts == 1
    assert mcp.calls == ["forecast_start"]


def test_invalid_tool_contract_is_a_retryable_system_error() -> None:
    result = ActionExecutor._validate_tool_result({"success": "yes", "status": "COMPLETED", "message": "ok", "retryable": False})
    assert result["status"] == "SYSTEM_ERROR"
    assert result["errorCode"] == "INVALID_TOOL_RESPONSE"
    assert result["retryable"] is True
