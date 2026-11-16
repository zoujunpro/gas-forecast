from gas_forecast_agent.presentation import PresentationPlanner


def test_records_result_without_page_type_is_presented_as_list() -> None:
    planned = PresentationPlanner.plan("customer_list", {
        "success": True,
        "status": "COMPLETED",
        "message": "查询成功",
        "data": {"total": 2, "records": [
            {"customerCode": "CUS-1", "customerName": "客户一"},
            {"customerCode": "CUS-2", "customerName": "客户二"},
        ]},
    })

    assert planned["presentation"] == {
        "type": "LIST", "dataSource": "executionResult.data.records"
    }


def test_unified_forecast_result_generates_chart_and_keeps_execution_snapshots() -> None:
    planned = PresentationPlanner.plan("forecast_result_query", {
        "resultType": "RESULT",
        "data": {
            "record": {"forecastNo": "F-1", "customerName": "测试客户"},
            # 统一预测任务的训练、回测和预测快照均由 forecast_result_query 返回。
            "training": '{"batchNo":"T-1","bestModel":"XGBoost"}',
            "backtest": '{"points":[{"date":"2026-10-01","actualValue":10,"predictedValue":9}]}',
            "forecast": '{"points":[{"forecastDate":"2026-10-16","forecastValue":10},'
            '{"forecastDate":"2026-10-17","forecastValue":20}]}',
        },
    })
    assert planned["presentation"]["type"] == "FORECAST_CHART"
    assert planned["presentation"]["chartType"] == "LINE"
    assert planned["forecast_result"]["average"] == 15
    assert planned["forecast_result"]["points"][0] == {"date": "2026-10-16", "value": 10}
    assert planned["forecast_result"]["training"] == {"batchNo": "T-1", "bestModel": "XGBoost"}
    assert planned["forecast_result"]["backtest"]["points"][0]["actualValue"] == 10


def test_empty_forecast_does_not_create_fake_chart() -> None:
    planned = PresentationPlanner.plan(
        "forecast_result_query",
        {"resultType": "RESULT", "data": {"record": {}, "forecast": '{"points": []}'}},
    )
    assert planned["presentation"]["type"] == "TEXT"
    assert "forecast_result" not in planned


def test_pending_unified_forecast_task_builds_visible_progress() -> None:
    planned = PresentationPlanner.plan("forecast_start", {
        "resultType": "TASK",
        "data": {"taskNo": "TASK-1", "status": "PENDING", "tasks": [{"status": "PENDING"}]},
    })
    assert planned["progress"]["currentStep"] == 2
    assert planned["progress"]["steps"][1]["detail"] == "PENDING"
    assert planned["progress"]["steps"][2]["status"] == "WAITING"
    assert planned["progress"]["totalSteps"] == 3
    assert planned["progress"]["title"] == "预测任务执行情况"


def test_successful_unified_task_status_is_terminal() -> None:
    planned = PresentationPlanner.plan("task_status_get", {
        "resultType": "PROGRESS", "status": "COMPLETED",
        "data": {"taskNo": "TASK-1", "status": "SUCCESS"},
    })
    progress = planned["progress"]
    assert progress["title"] == "预测任务执行情况"
    assert progress["currentStep"] == 3
    assert all(step["status"] == "SUCCESS" for step in progress["steps"])


def test_blocks_include_real_forecast_rows():
    from gas_forecast_agent.presentation import build_content_blocks
    result = {"data": {"forecast": {"points": [{"date": "2026-10-01", "value": 12}]}}}
    planned = PresentationPlanner.plan("forecast_result_query", result)
    blocks = build_content_blocks("完成", result, planned)
    chart = next(block for block in blocks if block.type == "CHART")
    assert chart.data["rows"] == [{"date": "2026-10-01", "value": 12}]
    assert result["data"]["forecast"]["points"][0]["value"] == 12


def test_table_preserves_paging_envelope_and_builds_columns():
    from gas_forecast_agent.presentation import build_content_blocks
    result = {"data": {"total": 350, "records": [{"name": "客户一"}]}}
    blocks = build_content_blocks("完成", result, PresentationPlanner.plan("customer_list", result))
    table = blocks[-1]
    assert table.data["rows"] == [{"name": "客户一"}]
    assert table.data["columns"] == [{"field": "name", "title": "name"}]
    assert table.data["total"] == 350
    assert "rows" not in result


def test_empty_result_only_builds_markdown():
    from gas_forecast_agent.presentation import build_content_blocks
    assert [block.type for block in build_content_blocks("请补充客户", {}, {})] == ["MARKDOWN"]


def test_task_and_failure_do_not_create_tables():
    from gas_forecast_agent.presentation import build_content_blocks
    for result in ({"resultType": "TASK", "data": {"taskNo": "T-1"}},
                   {"success": False, "data": {"records": []}}):
        assert [block.type for block in build_content_blocks("说明", result, {})] == ["MARKDOWN"]


def test_invalid_forecast_values_are_skipped():
    planned = PresentationPlanner.plan("forecast_result_query", {"data": {"forecast": {
        "points": [{"date": "today", "value": value} for value in ["bad", "NaN", True, 12]]}}})
    assert planned["forecast_result"]["points"] == [{"date": "today", "value": 12}]


def test_llm_plan_rejects_unknown_fields_and_preserves_rows():
    import pytest
    from gas_forecast_agent.presentation import build_content_blocks
    from gas_forecast_agent.presentation_planner import PresentationPlan, apply_plan
    defaults = build_content_blocks("完成", {"data": {"records": [{"date": "today", "value": 12}]}}, {})
    result = apply_plan(PresentationPlan.model_validate({"blocks": [{"type": "CHART", "source_id": "1", "x_field": "date", "y_fields": ["value"], "chart_type": "bar"}]}), defaults)
    assert result[1].data["chartType"] == "BAR"
    assert result[1].data["rows"] == defaults[1].data["rows"]
    with pytest.raises(ValueError):
        apply_plan(PresentationPlan.model_validate({"blocks": [{"type": "CHART", "source_id": "1", "x_field": "missing", "y_fields": ["value"]}]}), defaults)


def test_model_failure_falls_back(monkeypatch):
    import asyncio
    from gas_forecast_agent.config import Settings
    from gas_forecast_agent.presentation_planner import plan_blocks
    from gas_forecast_agent.presentation import build_content_blocks
    def fail(*args, **kwargs):
        raise RuntimeError("unavailable")
    monkeypatch.setattr("gas_forecast_agent.presentation_planner.build_llm_client", fail)
    defaults = build_content_blocks("完成", {"data": {"records": [{"value": 12}]}}, {})
    settings = Settings(_env_file=None, llm_api_key="test", llm_model="test")
    assert asyncio.run(plan_blocks("柱状图", defaults, settings)) == defaults


def test_chart_to_table_drops_chart_only_properties():
    from gas_forecast_agent.schemas import ContentBlock
    from gas_forecast_agent.presentation_planner import PresentationPlan, apply_plan
    defaults = [ContentBlock(type="CHART", data={"chartType": "LINE", "rows": [{"date": "today", "value": 1}]})]
    plan = PresentationPlan.model_validate({"blocks": [{"type": "TABLE", "source_id": "0"}]})
    table = apply_plan(plan, defaults)[0]
    assert "chartType" not in table.data
    assert table.data["rows"] == defaults[0].data["rows"]
