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
