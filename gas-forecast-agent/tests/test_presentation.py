from gas_forecast_agent.presentation import PresentationPlanner


def test_forecast_result_generates_whitelisted_chart_and_ui_data() -> None:
    planned = PresentationPlanner.plan("forecast_result_get", {
        "resultType": "RESULT",
        "data": {
            "record": {"forecastBatchNo": "F-1", "customerName": "测试客户"},
            "points": [
                {"forecastBatchNo": "F-1", "forecastDate": "2026-10-16", "forecastValue": 10},
                {"forecastBatchNo": "F-1", "forecastDate": "2026-10-17", "forecastValue": 20},
            ],
        },
    })
    assert planned["presentation"]["type"] == "FORECAST_CHART"
    assert planned["presentation"]["chartType"] == "LINE"
    assert planned["forecast_result"]["average"] == 15
    assert planned["forecast_result"]["points"][0] == {"date": "2026-10-16", "value": 10}


def test_empty_forecast_does_not_create_fake_chart() -> None:
    planned = PresentationPlanner.plan("forecast_result_get", {"resultType": "RESULT", "data": {"record": {}, "points": []}})
    assert planned["presentation"]["type"] == "TEXT"
    assert "forecast_result" not in planned


def test_waiting_training_builds_visible_dynamic_progress() -> None:
    planned = PresentationPlanner.plan("forecast_start", {
        "resultType": "TASK",
        "data": {"status": "WAITING_TRAINING", "tasks": [{"status": "WAITING_TRAINING", "trainBatchNo": "T-1"}]},
    })
    assert planned["progress"]["currentStep"] == 2
    assert planned["progress"]["steps"][1]["detail"] == "T-1"
    assert planned["progress"]["steps"][2]["status"] == "WAITING"
    assert planned["progress"]["totalSteps"] == 4
    assert planned["progress"]["title"] == "训练与预测任务"


def test_forecast_result_keeps_training_and_backtest_information() -> None:
    planned = PresentationPlanner.plan("forecast_result_get", {
        "resultType": "RESULT",
        "data": {
            "record": {"forecastBatchNo": "F-1"},
            "training": {"batchNo": "T-1", "bestModel": "XGBoost"},
            "backtestPoints": [{"date": "2026-10-01", "actualValue": 10, "predictedValue": 9}],
            "points": [{"forecastDate": "2026-10-16", "forecastValue": 12}],
        },
    })
    assert planned["forecast_result"]["training"]["batchNo"] == "T-1"
    assert planned["forecast_result"]["backtestPoints"][0]["actualValue"] == 10


def test_training_result_generates_basic_info_and_backtest_chart_data() -> None:
    planned = PresentationPlanner.plan("training_result_get", {
        "resultType": "RESULT",
        "data": {"batches": [{
            "batchNo": "T-1", "status": "SUCCESS", "customerName": "测试客户",
            "trainStartDate": "2026-01-01", "trainEndDate": "2026-09-30",
            "bestModel": "XGBoost", "mape": 3.2, "rmse": 5.6,
            "resultJson": {"rolling_backtest_results": [
                {"stat_date": "2026-09-01", "actual_value": 100, "predicted_value": 98,
                 "absolute_error": 2, "error_rate": 0.02}
            ]},
        }]},
    })
    presentation = planned["presentation"]
    assert presentation["type"] == "TRAINING_BACKTEST"
    assert presentation["trainingResult"]["batchNo"] == "T-1"
    assert presentation["trainingResult"]["customerName"] == "测试客户"
    assert presentation["trainingResult"]["backtestPoints"][0] == {
        "date": "2026-09-01", "actualValue": 100, "predictedValue": 98,
        "absoluteError": 2, "errorRate": 0.02,
    }
