"""根据真实工具结果生成受控的前端展示结构。"""

from __future__ import annotations

from decimal import Decimal
from typing import Any


class PresentationPlanner:
    """图表类型和字段由程序白名单决定，模型不能修改业务数值。"""

    @classmethod
    def plan(cls, tool_name: str | None, result: dict[str, Any]) -> dict[str, Any]:
        data = result.get("data") if isinstance(result.get("data"), dict) else {}
        if tool_name == "forecast_result_query":
            forecast = cls._forecast_result(data)
            if forecast:
                return {
                    "presentation": {
                        "type": "FORECAST_CHART", "chartType": "LINE", "title": "用气预测趋势",
                        "xField": "date", "yFields": ["value"], "dataSource": "forecastResult.points",
                    },
                    "forecast_result": forecast,
                }
        if tool_name == "training_result_query":
            training = cls._training_result(data)
            if training:
                return {
                    "presentation": {
                        "type": "TRAINING_BACKTEST",
                        "title": "训练信息与回测效果",
                        "trainingResult": training,
                    },
                    "training_result": training,
                }
        # 部分 MCP 列表工具返回 records，但旧版本没有设置 resultType=PAGE。
        # 展示层应依据真实数据结构兜底识别，避免完整列表退化成几条文本示例。
        if str(result.get("resultType") or "").upper() == "PAGE" or isinstance(data.get("records"), list):
            return {"presentation": {"type": "LIST", "dataSource": "executionResult.data.records"}}
        if str(result.get("resultType") or "").upper() in {"TASK", "PROGRESS"} and str(result.get("status") or "").upper() not in {"NEED_CONFIRMATION", "NEED_CLARIFICATION"}:
            progress = cls._progress(tool_name, data)
            return {"presentation": {"type": "PROGRESS", "dataSource": "progress"}, "progress": progress}
        return {"presentation": {"type": "TEXT"}}

    @staticmethod
    def _training_progress(data: dict[str, Any]) -> dict[str, Any]:
        batch = data.get("record") if isinstance(data.get("record"), dict) else data
        status = str(batch.get("status") or "UNKNOWN").upper()
        batch_no = batch.get("batchNo") or batch.get("trainBatchNo") or "训练批次"
        failed = status in {"FAILED", "FAILED_FINAL", "PREPARE_FAILED", "CANCELLED"}
        completed = status == "SUCCESS"
        running = status in {"WAITING", "PENDING", "CLAIMED", "PREPARING", "RUNNING"}
        step_status = "SUCCESS" if completed else "FAILED" if failed else "RUNNING" if running else "WAITING"
        save_status = "SUCCESS" if completed else "SKIPPED" if failed else "WAITING"
        steps = [
            {"index": 1, "name": "确认训练任务", "status": "SUCCESS", "detail": str(batch_no)},
            {"index": 2, "name": "执行模型训练", "status": step_status, "detail": str(batch.get("message") or status)},
            {"index": 3, "name": "保存模型与评估指标", "status": save_status,
             "detail": "已完成" if completed else "因训练失败未执行" if failed else "等待训练完成"},
        ]
        current = 3 if completed else 2
        return {"title": "模型训练状态", "currentStep": current, "totalSteps": 3, "steps": steps}

    @staticmethod
    def _training_result(data: dict[str, Any]) -> dict[str, Any] | None:
        batches = data.get("batches") if isinstance(data.get("batches"), list) else []
        batch = next((item for item in batches if isinstance(item, dict)), None)
        if batch is None:
            # 兼容工具直接返回单个训练记录的情况。
            batch = data.get("record") if isinstance(data.get("record"), dict) else data
        if not isinstance(batch, dict) or not (batch.get("batchNo") or batch.get("trainBatchNo")):
            return None

        result_json = batch.get("resultJson") if isinstance(batch.get("resultJson"), dict) else {}
        raw_points = result_json.get("rolling_backtest_results")
        if not isinstance(raw_points, list):
            raw_points = result_json.get("rollingBacktestResults")
        if not isinstance(raw_points, list):
            raw_points = []

        def value(item: dict[str, Any], snake: str, camel: str) -> Any:
            return item.get(snake) if item.get(snake) is not None else item.get(camel)

        points = []
        for item in raw_points:
            if not isinstance(item, dict):
                continue
            date = value(item, "stat_date", "statDate") or item.get("date")
            actual = value(item, "actual_value", "actualValue")
            predicted = value(item, "predicted_value", "predictedValue")
            if date is None or (actual is None and predicted is None):
                continue
            points.append({
                "date": str(date),
                "actualValue": actual,
                "predictedValue": predicted,
                "absoluteError": value(item, "absolute_error", "absoluteError"),
                "errorRate": value(item, "error_rate", "errorRate"),
            })

        return {
            "batchNo": batch.get("batchNo") or batch.get("trainBatchNo"),
            "status": batch.get("status"),
            "agentName": batch.get("agentName"),
            "customerName": batch.get("customerName"),
            "regionName": batch.get("regionName"),
            "industryName": batch.get("industryName"),
            "trainStartDate": batch.get("trainStartDate"),
            "trainEndDate": batch.get("trainEndDate"),
            "bestModel": batch.get("bestModel") or result_json.get("selected_model_name") or result_json.get("selectedModelName"),
            "modelVersion": batch.get("modelVersion"),
            "trainDurationSeconds": batch.get("trainDurationSeconds"),
            "mape": batch.get("mape"),
            "wmape": batch.get("wmape"),
            "smape": batch.get("smape"),
            "mae": batch.get("mae"),
            "rmse": batch.get("rmse"),
            "r2": batch.get("r2"),
            "createdAt": batch.get("createdAt"),
            "updatedAt": batch.get("updatedAt"),
            "backtestPoints": points,
        }

    @staticmethod
    def _forecast_result(data: dict[str, Any]) -> dict[str, Any] | None:
        record = data.get("record") if isinstance(data.get("record"), dict) else {}
        raw_points = data.get("points") if isinstance(data.get("points"), list) else []
        points = [
            {"date": str(item.get("forecastDate")), "value": item.get("forecastValue")}
            for item in raw_points
            if isinstance(item, dict) and item.get("forecastDate") and item.get("forecastValue") is not None
        ]
        if not points:
            return None
        values = [Decimal(str(item["value"])) for item in points]
        total = sum(values, Decimal("0"))
        average = total / Decimal(len(values))
        return {
            "batchNo": record.get("forecastBatchNo") or raw_points[0].get("forecastBatchNo"),
            "customerName": record.get("customerName"),
            "average": average, "minimum": min(values), "maximum": max(values),
            "training": data.get("training") if isinstance(data.get("training"), dict) else None,
            "points": points, "historyPoints": [], "lastYearPoints": [],
            "backtestPoints": data.get("backtestPoints") if isinstance(data.get("backtestPoints"), list) else [],
        }

    @staticmethod
    def _progress(tool_name: str | None, data: dict[str, Any]) -> dict[str, Any]:
        tasks = data.get("tasks") if isinstance(data.get("tasks"), list) else []
        task = next((item for item in tasks if isinstance(item, dict)), data)
        status = str(task.get("status") or data.get("status") or "EXECUTING").upper()
        train_batch = task.get("trainBatchNo") or data.get("trainBatchNo")
        forecast_batch = task.get("forecastBatchNo") or data.get("forecastBatchNo")
        failed = status.startswith("FAILED")
        if tool_name == "training_start":
            steps = [
                {"index": 1, "name": "确认训练范围和参数", "status": "SUCCESS", "detail": "已完成"},
                {"index": 2, "name": "执行模型训练", "status": "FAILED" if failed else "RUNNING", "detail": train_batch or "训练任务已提交"},
                {"index": 3, "name": "保存模型与评估指标", "status": "WAITING", "detail": "等待训练完成"},
            ]
            return {"title": "模型训练", "currentStep": 2, "totalSteps": 3, "steps": steps}
        waiting_training = status == "WAITING_TRAINING"
        steps = [
            {"index": 1, "name": "确认客户与预测范围", "status": "SUCCESS", "detail": "已完成"},
            {"index": 2, "name": "训练客户预测模型" if waiting_training else "复用已有训练结果", "status": "RUNNING" if waiting_training else "SUCCESS",
             "detail": train_batch if waiting_training else (f"已复用训练批次：{train_batch}" if train_batch else "已找到匹配的成功训练模型")},
            {"index": 3, "name": "执行用气预测", "status": "WAITING" if waiting_training else ("FAILED" if failed else "RUNNING"), "detail": forecast_batch or "等待训练完成"},
            {"index": 4, "name": "保存并展示预测结果", "status": "WAITING", "detail": "等待预测完成"},
        ]
        return {"title": "训练与预测任务", "currentStep": 2 if waiting_training else 3, "totalSteps": 4, "steps": steps}
