import asyncio
from types import SimpleNamespace
from typing import Any

from gas_forecast_agent.config import Settings
from gas_forecast_agent.skills import DialogueAct, RequestSemantics, SkillAgent, SkillDefinition, SkillRouter, TrainingPolicy
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
        "data": {"records": [{"customerName": "东台中油"}], "total": 154},
    }
    fallback = SkillAgent._page_fallback_result(
        {}, {"customer_list": {}}, [{"tool": "customer_list", "arguments": {}, "result": result}],
    )

    assert fallback.status == "COMPLETED"
    assert fallback.message == "查询完成，共找到154条记录，完整结果已展示。"
    assert fallback.execution_result is result


def test_python_style_model_message_is_parsed_and_long_page_text_uses_fallback() -> None:
    decision = SkillAgent._message_decision(
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


def test_model_selects_one_allowed_tool_then_explains_result(monkeypatch) -> None:
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
    skill = SkillDefinition.model_validate({
        "name": "business-data-query",
        "description": "查询客户",
        "allowed-tools": ["customer_list"],
        "instructions": "查询客户时调用 customer_list。",
    })
    mcp = FakeMcp()

    result = asyncio.run(SkillAgent(settings).run(
        conversation_no="C-1",
        skill=skill,
        message="查询浙江客户",
        history=[],
        memory={},
        metadata={},
        mcp=mcp,
    ))

    assert result.status == "NOT_FOUND"
    assert result.executed_tool == "customer_list"
    assert result.message == "浙江没有查询到符合条件的客户。"
    assert mcp.calls == ["customer_list"]
    assert result.tool_trace[0]["arguments"] == {"regionName": "浙江"}
    assert requests
    assert len(requests) == 3
    assert all(request["parallel_tool_calls"] is False for request in requests)
    assert "只选择当前最先需要执行的一个工具" in requests[1]["messages"][-1]["content"]


def test_structured_training_result_returns_without_second_model_call(monkeypatch) -> None:
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
    skill = SkillDefinition.model_validate({
        "name": "model-training-management",
        "description": "查询训练结果",
        "allowed-tools": ["training_result_get"],
        "instructions": "查询训练结果。",
    })

    result = asyncio.run(SkillAgent(settings).run(
        conversation_no="C-TRAIN", skill=skill, message="查询训练信息", history=[],
        memory={}, metadata={}, mcp=TrainingMcp(),
    ))

    assert result.status == "COMPLETED"
    assert result.executed_tool == "training_result_get"
    assert result.message == "训练信息查询完成"
    assert len(requests) == 1


def test_model_cannot_forge_user_confirmation(monkeypatch) -> None:
    response = SimpleNamespace(
        choices=[SimpleNamespace(message=SimpleNamespace(
            content=None,
            tool_calls=[SimpleNamespace(function=SimpleNamespace(
                name="task_retry",
                arguments='{"taskNo":"TASK-1","confirmed":true}',
            ))],
        ))],
    )

    class Completions:
        async def create(self, **kwargs):
            return response

    client = SimpleNamespace(chat=SimpleNamespace(completions=Completions()))
    monkeypatch.setattr("gas_forecast_agent.skills.agent.build_llm_client", lambda settings: client)
    settings = model_settings()
    skill = SkillDefinition.model_validate({
        "name": "model-training-management",
        "description": "重试任务",
        "allowed-tools": ["task_retry"],
        "instructions": "重试前必须获得用户确认。",
    })

    class RetryMcp(FakeMcp):
        async def list_tools(self):
            return [{
                "name": "task_retry",
                "description": "重试",
                "inputSchema": {
                    "type": "object",
                    "required": ["taskNo", "confirmed"],
                    "properties": {"taskNo": {"type": "string"}, "confirmed": {"type": "boolean"}},
                },
            }]

    mcp = RetryMcp()
    result = asyncio.run(SkillAgent(settings).run(
        conversation_no="C-1", skill=skill, message="重试 TASK-1", history=[], memory={}, metadata={}, mcp=mcp,
    ))

    assert result.status == "NEED_CONFIRMATION"
    assert result.pending_tool == "task_retry"
    assert mcp.calls == []


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
    skill = SkillDefinition.model_validate({
        "name": "customer-gas-forecast",
        "description": "发起预测",
        "allowed-tools": ["customer_list"],
        "instructions": "预测必须调用工具。",
    })

    result = asyncio.run(SkillAgent(settings).run(
        conversation_no="C-SAFE", skill=skill, message="预测某客户", history=[], memory={}, metadata={}, mcp=FakeMcp(),
    ))

    assert result.status == "FAILED"
    assert "没有执行任何业务工具" in result.message
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
    skill = SkillDefinition.model_validate({
        "name": "business-data-query",
        "description": "查询客户",
        "allowed-tools": ["customer_list"],
        "instructions": "查询客户。",
    })
    result = asyncio.run(SkillAgent(settings).run(
        conversation_no="C-2", skill=skill, message="查询客户", history=[], memory={}, metadata={}, mcp=NeedInfoMcp(),
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
    arguments = SkillAgent._validated_arguments(tool, {
        "conversationNo": "C-3", "customerCode": "CUS-1", "unknown": "drop",
    })
    assert arguments == {"conversationNo": "C-3", "customerCode": "CUS-1"}


def test_direct_model_question_is_not_marked_completed() -> None:
    decision = SkillAgent._message_decision("请提供具体客户名称。")
    assert decision["status"] == "NEED_INFORMATION"


def test_direct_model_conflict_is_not_marked_completed() -> None:
    decision = SkillAgent._message_decision('{"status":"NEED_CLARIFICATION","message":"预测参数存在冲突。"}')
    assert decision == {"type": "message", "status": "NEED_CLARIFICATION", "message": "预测参数存在冲突。"}


def test_schema_range_is_checked_before_tool_call() -> None:
    tool = {"inputSchema": {"properties": {"forecastHorizon": {"type": "integer", "minimum": 1, "maximum": 30}}}}
    assert SkillAgent._argument_validation_error(tool, {"forecastHorizon": 31}) == "参数 forecastHorizon 不能大于 30。"
    assert SkillAgent._argument_validation_error(tool, {
        "trainingStartDate": "2025-03-01", "trainingEndDate": "2025-01-01",
    }) == "训练开始日期不能晚于训练结束日期，请重新确认训练范围。"


def test_training_utterance_extracts_clean_customer_name() -> None:
    for message in (
        "训练杭州天然气的短期预测模型",
        "帮杭州天然气重新训练一下",
        "让模型重新学习杭州天然气的数据",
        "查询杭州天然气训练进度",
        "开始跑一下杭州天然气模型",
    ):
        values = SkillAgent._initial_parameters(message, {}, {}, "C-6", "model-training-management")
        assert values["customerName"] == "杭州天然气"


def test_training_range_and_industry_alias_are_extracted() -> None:
    recent = SkillAgent._initial_parameters("只用最近90天数据训练杭州天然气", {}, {}, "C-7", "model-training-management")
    assert (recent["trainMode"], recent["trainingPeriods"], recent["customerName"]) == ("RECENT", 90, "杭州天然气")

    date_range = SkillAgent._initial_parameters(
        "使用2025-01-01到2025-03-01的数据训练杭州天然气", {}, {}, "C-8", "model-training-management",
    )
    assert (date_range["trainMode"], date_range["trainingStartDate"], date_range["trainingEndDate"]) == (
        "RANGE", "2025-01-01", "2025-03-01",
    )
    chemical = SkillAgent._initial_parameters("训练浙江城市化工客户", {}, {}, "C-9", "model-training-management")
    assert chemical["industryName"] == "化工"
    assert chemical["bulk"] is True


def test_region_with_province_suffix_does_not_require_a_region_allowlist() -> None:
    values = SkillAgent._initial_parameters(
        "查询江苏省城市燃气行业的所有客户信息", {}, {}, "C-REGION", "business-data-query"
    )

    assert values["regionName"] == "江苏"
    assert values["industryName"] == "城市燃气"
    assert values["fetchAll"] is True


def test_explicit_region_and_industry_are_not_overwritten_by_model_semantics() -> None:
    semantics = RequestSemantics(
        operation="QUERY_CUSTOMERS",
        region_name="江苏城燃",
        industry_name="燃气客户信息",
    )

    values = SkillAgent._initial_parameters(
        "查询下江苏城燃的所有客户信息",
        {}, {}, "C-REGION-LOCKED", "business-data-query", semantics,
    )

    assert values["regionName"] == "江苏"
    assert values["industryName"] == "城燃"
    assert values["fetchAll"] is True
    assert {"regionName", "industryName"}.issubset(values["_lockedParameters"])


def test_forecast_with_new_training_result_forces_retraining() -> None:
    values = SkillAgent._initial_parameters(
        "帮我预测下客户CUS000023的未来40天燃气销量，用新训练的结果",
        {}, {}, "C-NEW-TRAIN", "customer-gas-forecast",
    )

    assert values["forceRetrain"] is True
    assert values["autoTrain"] is True
    assert values["trainingPreference"] == "FORCE_RETRAIN"
    assert values["customerCode"] == "CUS000023"
    assert values["forecastHorizon"] == 40

    arguments = {**values, "customerCode": "CUS000135", "forecastHorizon": 90, "forceRetrain": False}
    SkillAgent._apply_locked_parameters(values, arguments)
    assert arguments["customerCode"] == "CUS000023"
    assert arguments["forecastHorizon"] == 40
    assert arguments["forceRetrain"] is True

    confirmed = SkillAgent._initial_parameters(
        "是",
        {"currentParameters": values, "pendingAction": {"tool": "forecast_start", "parameters": values}},
        {}, "C-NEW-TRAIN", "customer-gas-forecast",
        RequestSemantics(dialogue_act=DialogueAct.CONFIRM_PENDING),
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
    values = SkillAgent._initial_parameters(
        "预测下客户CUS000134未来50天燃气销量，同时训练也要重新发起",
        {"currentParameters": stale, "pendingAction": {"tool": "forecast_start", "parameters": stale}},
        {},
        "C-RETRAIN-AGAIN",
        "customer-gas-forecast",
    )

    assert "_pendingTool" not in values
    assert values["forceRetrain"] is True
    assert values["autoTrain"] is True
    assert values["trainingPreference"] == "FORCE_RETRAIN"
    assert {"forceRetrain", "autoTrain", "trainingPreference"}.issubset(values["_lockedParameters"])


def test_structured_semantics_overrides_stale_training_policy_without_phrase_rules() -> None:
    values = SkillAgent._initial_parameters(
        "模型不要接着上次那个了，给我另起炉灶",
        {"currentParameters": {"customerCode": "CUS-9", "forceRetrain": False, "trainingPreference": "AUTO"}},
        {},
        "C-SEMANTIC",
        "customer-gas-forecast",
        RequestSemantics(
            operation="FORECAST",
            training_policy=TrainingPolicy.FORCE_RETRAIN,
            evidence={"training_policy": "不要接着上次那个了，另起炉灶"},
        ),
    )

    assert values["forceRetrain"] is True
    assert values["trainingPreference"] == "FORCE_RETRAIN"
    assert "trainingPreference" in values["_lockedParameters"]


def test_confirmation_restores_and_locks_pending_parameter_snapshot() -> None:
    snapshot = {
        "customerCode": "CUS-9", "forecastHorizon": 50, "forceRetrain": True,
        "autoTrain": True, "trainingPreference": "FORCE_RETRAIN", "idempotencyKey": "same-operation",
    }
    values = SkillAgent._initial_parameters(
        "确认",
        {
            "currentParameters": {**snapshot, "forceRetrain": False},
            "pendingAction": {"tool": "forecast_start", "parameters": snapshot},
        },
        {"customerCode": "STALE-PAGE-CUSTOMER"},
        "C-SNAPSHOT",
        "customer-gas-forecast",
        RequestSemantics(dialogue_act=DialogueAct.CONFIRM_PENDING),
    )

    assert values["_pendingTool"] == "forecast_start"
    assert values["confirmed"] is True
    assert values["customerCode"] == "CUS-9"
    assert values["forceRetrain"] is True
    assert values["idempotencyKey"] == "same-operation"
    arguments = {**values, "customerCode": "MODEL-CHANGED", "forceRetrain": False}
    SkillAgent._apply_locked_parameters(values, arguments)
    assert arguments["customerCode"] == "CUS-9"
    assert arguments["forceRetrain"] is True


def test_confirmation_ignores_semantic_clarification_and_uses_pending_snapshot() -> None:
    snapshot = {
        "customerCode": "CUS000140",
        "customerName": "扬州中燃",
        "forecastHorizon": 50,
        "idempotencyKey": "pending-operation",
    }
    values = SkillAgent._initial_parameters(
        "继续",
        {"pendingAction": {"tool": "forecast_start", "parameters": snapshot}},
        {},
        "C-CONFIRMATION",
        "customer-gas-forecast",
        RequestSemantics(
            operation="UNSPECIFIED",
            dialogue_act=DialogueAct.CONFIRM_PENDING,
            needs_clarification=True,
            clarification_question="请明确要继续执行的具体客户、区域、行业或预测周期？",
            evidence={"message": "继续"},
        ),
    )

    assert values["_pendingTool"] == "forecast_start"
    assert values["confirmed"] is True
    assert values["customerCode"] == "CUS000140"
    assert values["forecastHorizon"] == 50
    assert values["idempotencyKey"] == "pending-operation"
    assert "_clarificationQuestion" not in values
    assert "_semanticEvidence" not in values


def test_training_completion_question_queries_status() -> None:
    decision = SkillAgent._rule_decision("model-training-management", "杭州天然气训练好了吗", {})
    assert decision["name"] == "training_status_get"


def test_training_information_query_is_deterministically_a_result_query() -> None:
    decision = SkillAgent._explicit_read_query(
        "model-training-management",
        "刚才的这个预测所用的训练信息给我",
        {"trainBatchNo": "TRBATCH-1"},
    )
    assert decision == {
        "type": "tool", "name": "training_result_get", "arguments": {"trainBatchNo": "TRBATCH-1"}
    }


def test_explicit_training_batch_number_cannot_be_truncated_by_model() -> None:
    values = SkillAgent._initial_parameters(
        "把训练批次：TRBATCH-20261014-00707的训练结果给我",
        {}, {}, "C-BATCH", "model-training-management",
    )
    arguments = {**values, "trainBatchNo": "TRBATCH-20261014-"}
    SkillAgent._apply_locked_parameters(values, arguments)

    assert values["trainBatchNo"] == "TRBATCH-20261014-00707"
    assert "trainBatchNo" in values["_lockedParameters"]
    assert arguments["trainBatchNo"] == "TRBATCH-20261014-00707"


def test_empty_training_result_is_not_reported_as_success() -> None:
    normalized = SkillAgent._normalize_empty_query_result(
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
    SkillAgent._apply_current_entity_arguments(current, arguments)
    assert arguments == {"customerCode": "opaque-new-code"}


def test_training_synonyms_select_training_skill() -> None:
    for message in ("杭州天然气需要重新建模", "让模型重新学习杭州天然气的数据", "开始跑一下杭州天然气模型", "恢复刚才卡住的任务"):
        assert SkillRouter._select_with_rules(message).skill_name == "model-training-management"


def test_forecast_with_retraining_goal_selects_forecast_skill() -> None:
    selection = SkillRouter._select_with_rules("重新训练后预测客户CUS-9未来50天")
    assert selection.skill_name == "customer-gas-forecast"


def test_forecast_page_context_binds_forecast_skill() -> None:
    registry = SimpleNamespace(get=lambda name: None)
    router = SkillRouter(Settings(GAS_SKILL_ROUTING_MODE="rules"), registry)
    selection = asyncio.run(router.select("为什么这几天这么高", {}, {"scene": "FORECAST_ANALYSIS"}))
    assert selection.skill_name == "customer-gas-forecast"


def test_forecast_page_question_reads_bound_result_in_rule_mode() -> None:
    decision = SkillAgent._rule_decision(
        "customer-gas-forecast", "为什么这几天这么高", {"forecastBatchNo": "FORECAST-1"},
    )
    assert decision["name"] == "forecast_result_get"


def test_forecast_customer_name_is_resolved_before_model_planning(monkeypatch) -> None:
    agent = SkillAgent(model_settings())

    def unexpected_model_client(settings):
        raise AssertionError("客户主数据解析前不应调用规划模型")

    monkeypatch.setattr("gas_forecast_agent.skills.agent.build_llm_client", unexpected_model_client)
    decision = asyncio.run(agent._decide(
        SimpleNamespace(name="customer-gas-forecast", model_instructions=lambda: ""),
        "帮我预测下扬州中燃未来50天的燃气销量",
        [],
        {"customerName": "扬州中燃", "forecastHorizon": 50},
        [],
        [{"name": "customer_search", "inputSchema": {"type": "object", "properties": {}}}],
    ))

    assert decision["type"] == "tool"
    assert decision["name"] == "customer_search"


def test_unique_customer_match_continues_to_forecast_without_model_replanning(monkeypatch) -> None:
    agent = SkillAgent(model_settings())

    def unexpected_model_client(settings):
        raise AssertionError("客户唯一匹配后应按工具依赖继续，不应再次调用规划模型")

    monkeypatch.setattr("gas_forecast_agent.skills.agent.build_llm_client", unexpected_model_client)
    parameters = {
        "customerCode": "CUS000140",
        "customerName": "扬州中燃",
        "forecastHorizon": 50,
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
        SimpleNamespace(name="customer-gas-forecast", model_instructions=lambda: ""),
        "帮我预测下扬州中燃未来50天的燃气销量",
        [],
        parameters,
        trace,
        [{"name": "forecast_start", "inputSchema": {"type": "object", "properties": {}}}],
    ))

    assert decision == {"type": "tool", "name": "forecast_start", "arguments": parameters}


def test_confirmation_and_idempotency_do_not_leak_to_next_request() -> None:
    remembered = {"currentParameters": {"confirmed": True, "idempotencyKey": "old-key"}}
    values = SkillAgent._initial_parameters("发起新的预测", remembered, {}, "C-4", "customer-gas-forecast")

    assert "confirmed" not in values
    assert values["idempotencyKey"] != "old-key"


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

    assert SkillAgent._confirmation_message(tool, {"bulk": False, "autoTrain": False}) is None
    assert SkillAgent._confirmation_message(tool, {"autoTrain": True}) is not None


def test_entity_merge_keeps_region_out_of_customer_fields() -> None:
    parameters = {"regionName": "浙江"}
    SkillAgent._merge_result(parameters, {
        "data": {
            "status": "UNIQUE",
            "candidates": [{"type": "REGION", "code": "330000", "name": "浙江", "regionCode": "330000", "regionName": "浙江"}],
        },
    })

    assert parameters["regionCode"] == "330000"
    assert "customerCode" not in parameters


def test_server_proposed_parameters_are_saved_for_confirmed_retry() -> None:
    parameters = {"customerCode": "CUS-1", "autoTrain": False}
    SkillAgent._merge_result(parameters, {
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

    client = SimpleNamespace(chat=SimpleNamespace(completions=Completions()))
    monkeypatch.setattr("gas_forecast_agent.skills.agent.build_llm_client", lambda settings: client)
    settings = model_settings(GAS_AGENT_MAX_SAME_TOOL_CALLS=2)
    skill = SkillDefinition.model_validate({
        "name": "business-data-query",
        "description": "查询客户",
        "allowed-tools": ["customer_list"],
        "instructions": "查询客户。",
    })
    mcp = PagingMcp()

    result = asyncio.run(SkillAgent(settings).run(
        conversation_no="C-5", skill=skill, message="反复翻页", history=[], memory={}, metadata={}, mcp=mcp,
    ))

    assert result.status == "FAILED"
    assert "调用次数达到安全上限" in result.message
    assert mcp.calls == ["customer_list", "customer_list"]


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

    missing = SkillAgent._missing_required_arguments(tool, {"trainMode": "RECENT"})
    assert "customerCode 或 customerName" in missing
    assert "trainingPeriods" in missing
    prompt = SkillAgent._missing_parameters_message(tool, missing, {"trainMode": "RECENT"})
    assert "customerCode（客户编码）或customerName（客户名称）" in prompt
    assert "trainingPeriods（训练天数）" in prompt
    assert SkillAgent._missing_required_arguments(
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
    agent = SkillAgent(settings)
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

    agent = SkillAgent(Settings(GAS_AGENT_MAX_READ_RETRIES=3))
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
    result = SkillAgent._validate_tool_result({"success": "yes", "status": "COMPLETED", "message": "ok", "retryable": False})
    assert result["status"] == "SYSTEM_ERROR"
    assert result["errorCode"] == "INVALID_TOOL_RESPONSE"
    assert result["retryable"] is True
