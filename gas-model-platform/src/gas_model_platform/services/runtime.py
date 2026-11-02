from gas_model_platform.models.base import ModelHandler
from gas_model_platform.models.registry import ModelRegistry
from gas_model_platform.services.model_event_reporter import ModelEventReporter
from contextlib import nullcontext
from gas_model_platform.schemas.modeling import (
    BacktestRequest,
    BacktestPoint,
    BacktestResult,
    ForecastExecuteRequest,
    ForecastExecuteResult,
    ForecastExecutionBacktestResult,
    ForecastExecutionForecastResult,
    ForecastExecutionTrainingResult,
    ModelContext,
    PredictRequest,
    PredictResult,
    TrainingDataValidationRequest,
    TrainingDataValidationResult,
    TrainRequest,
    TrainResult,
)


class ModelRuntime:
    def __init__(self, registry: ModelRegistry) -> None:
        self.registry = registry

    def train(self, request: TrainRequest) -> TrainResult:
        handler = self.registry.resolve(request.model_code)
        context = self._context_for_handler(request, handler)
        validation = handler.validate_training_data(context)
        if not validation.valid:
            messages = "；".join(issue.message for issue in validation.errors)
            raise ValueError(f"训练数据不满足模型要求：{messages}")
        result = handler.train(context)
        result.metadata.update(self._selection_metadata(request.model_code, handler.info.model_code))
        return result

    def validate_training_data(
        self, request: TrainingDataValidationRequest
    ) -> TrainingDataValidationResult:
        handler = self.registry.resolve(request.model_code)
        context = self._context_for_handler(request, handler)
        return handler.validate_training_data(context)

    def backtest(self, request: BacktestRequest) -> BacktestResult:
        handler = self.registry.resolve(request.model_code)
        context = self._context_for_handler(request, handler)
        result = handler.backtest(context)
        result.metadata.update(self._selection_metadata(request.model_code, handler.info.model_code))
        return result

    def predict(self, request: PredictRequest) -> PredictResult:
        handler = self.registry.resolve(request.model_code)
        context = self._context_for_handler(request, handler)
        result = handler.predict(context)
        result.metadata.update(self._selection_metadata(request.model_code, handler.info.model_code))
        return result

    def execute_forecast(self, request: ForecastExecuteRequest, reporter: ModelEventReporter | None = None) -> ForecastExecuteResult:
        """以一个 forecast_no 完成训练、回测和预测。

        处理器内部仍复用既有 train/predict 能力，但两次调用均使用 forecast_no，
        因而模型产物只会落在 artifact_root/{forecast_no} 下。
        """
        common = request.model_dump(exclude={"history_dataset", "future_dataset", "forecast_no"})
        train_request = TrainRequest(
            **common,
            train_batch_no=request.forecast_no,
            dataset=request.history_dataset,
        )
        handler = self.registry.resolve(request.model_code)
        context = self._context_for_handler(train_request, handler)

        validation_event = self._start(reporter, "MODEL_VALIDATE", "正在校验模型输入数据。")
        try:
            validation = handler.validate_training_data(context)
            validation_data = {
                "kind": "DATA_VALIDATION",
                "valid": validation.valid,
                "summary": validation.summary,
                "errors": [issue.model_dump(mode="json") for issue in validation.errors],
                "warnings": [issue.model_dump(mode="json") for issue in validation.warnings],
            }
            if not validation.valid:
                messages = "；".join(issue.message for issue in validation.errors)
                raise ValueError(f"训练数据不满足模型要求：{messages}")
            self._success(
                reporter,
                validation_event,
                self._validation_success_message(validation.summary),
                validation_data,
            )
        except Exception as exc:
            self._failure(reporter, validation_event, "模型输入数据校验未通过。", exc)
            raise

        train_event = self._start(reporter, "MODEL_TRAIN", "正在构建特征并训练候选模型。")
        try:
            # 训练器尚未暴露候选循环时，至少持续刷新同一 RUNNING 事件，避免服务中断后永久挂起。
            # 后续模型提供真实候选/回测进度回调时，可直接改为 reporter.progress，不改变事件模型。
            with reporter.heartbeat(train_event, "正在构建特征并训练候选模型。") if reporter else nullcontext():
                training = handler.train(context)
            training.metadata.update(self._selection_metadata(request.model_code, handler.info.model_code))
            training_data = {
                "kind": "MODEL_TRAINING",
                "candidateCount": training.requested_candidate_count,
                "successfulCandidateCount": training.successful_candidate_count,
                "featureCount": len(training.feature_names),
            }
            self._success(reporter, train_event, self._training_success_message(training_data), training_data)
        except Exception as exc:
            self._failure(reporter, train_event, "候选模型训练失败。", exc)
            raise

        backtest_event = self._start(reporter, "MODEL_BACKTEST", "正在汇总回测结果和预测误差。")
        backtest_data = {
            "kind": "BACKTEST",
            "metrics": training.metrics.model_dump(mode="json"),
            "pointCount": len(training.rolling_backtest_results),
        }
        self._success(reporter, backtest_event, self._backtest_success_message(backtest_data), backtest_data)

        selection_event = self._start(reporter, "MODEL_SELECT", "正在选择本次预测使用的模型。")
        selection_data = {
            "kind": "MODEL_SELECTION",
            "selectedModelName": training.selected_model_name,
            "selectionReason": training.selection_reason,
            "candidateCount": training.ranked_candidate_count,
            "metrics": training.metrics.model_dump(mode="json"),
        }
        self._success(reporter, selection_event, self._selection_success_message(selection_data), selection_data)

        artifact_event = self._start(reporter, "MODEL_ARTIFACT", "正在保存本次模型产物。")
        self._success(reporter, artifact_event, "本次模型产物已保存，可用于后续预测。", {
            "kind": "MODEL_ARTIFACT",
            "artifactRef": request.forecast_no,
            "modelCode": training.model_code,
            "modelVersion": handler.info.model_version,
        })
        predict_request = PredictRequest(
            **common,
            train_batch_no=request.forecast_no,
            forecast_batch_no=request.forecast_no,
            dataset=request.future_dataset,
        )
        predict_event = self._start(reporter, "MODEL_PREDICT", "正在生成未来预测值。")
        try:
            prediction_context = self._context_for_handler(predict_request, handler)
            with reporter.heartbeat(predict_event, "正在生成未来预测值。") if reporter else nullcontext():
                prediction = handler.predict(prediction_context)
            prediction.metadata.update(self._selection_metadata(request.model_code, handler.info.model_code))
            forecast_data = {
                "kind": "FORECAST",
                "forecastStartDate": str(prediction.points[0].forecast_date) if prediction.points else None,
                "pointCount": len(prediction.points),
            }
            self._success(reporter, predict_event, self._forecast_success_message(forecast_data), forecast_data)
        except Exception as exc:
            self._failure(reporter, predict_event, "未来预测值生成失败。", exc)
            raise
        return ForecastExecuteResult(
            forecast_no=request.forecast_no,
            agent_code=training.agent_code,
            model_code=training.model_code,
            training=ForecastExecutionTrainingResult(
                metrics=training.metrics,
                feature_names=training.feature_names,
                selected_model_name=training.selected_model_name,
                selection_reason=training.selection_reason,
                requested_candidate_count=training.requested_candidate_count,
                successful_candidate_count=training.successful_candidate_count,
                ranked_candidate_count=training.ranked_candidate_count,
                selected_model_params=training.selected_model_params,
                candidate_evaluations=training.candidate_evaluations,
                issues=training.issues,
                metadata=training.metadata,
            ),
            backtest=ForecastExecutionBacktestResult(
                metrics=training.metrics,
                points=[BacktestPoint(
                    stat_date=item.stat_date,
                    actual_value=item.actual_value,
                    predicted_value=item.predicted_value,
                ) for item in training.rolling_backtest_results],
                fold_metrics=training.rolling_backtest_fold_metrics,
            ),
            forecast=ForecastExecutionForecastResult(
                points=prediction.points,
                metadata=prediction.metadata,
            ),
        )

    @staticmethod
    def _start(reporter: ModelEventReporter | None, code: str, message: str):
        return reporter.start(code, message) if reporter else None

    @staticmethod
    def _success(reporter: ModelEventReporter | None, handle, message: str, data: dict) -> None:
        if reporter:
            reporter.success(handle, message, data)

    @staticmethod
    def _failure(reporter: ModelEventReporter | None, handle, message: str, error: Exception) -> None:
        if reporter:
            reporter.failure(handle, message, error)

    @staticmethod
    def _validation_success_message(summary: dict) -> str:
        rows = summary.get("row_count", summary.get("unique_date_count", 0))
        start = summary.get("start_date", "-")
        end = summary.get("end_date", "-")
        missing = summary.get("missing_date_count", 0)
        duplicate = summary.get("duplicate_date_count", 0)
        return f"模型数据校验通过：有效{rows}条，日期{start}至{end}，缺失{missing}天，重复{duplicate}条。"

    @staticmethod
    def _training_success_message(data: dict) -> str:
        return (
            f"候选模型训练完成：共{data.get('candidateCount', 0)}个，"
            f"成功{data.get('successfulCandidateCount', 0)}个，"
            f"使用{data.get('featureCount', 0)}项特征。"
        )

    @staticmethod
    def _backtest_success_message(data: dict) -> str:
        metrics = data.get("metrics") or {}
        items = []
        if metrics.get("mape") is not None:
            items.append(f"MAPE {metrics['mape']:.2f}%")
        if metrics.get("rmse") is not None:
            items.append(f"RMSE {metrics['rmse']:.2f}")
        metric_text = "、".join(items) if items else "误差指标已计算"
        return f"回测完成：{data.get('pointCount', 0)}个回测点，{metric_text}。"

    @staticmethod
    def _selection_success_message(data: dict) -> str:
        metrics = data.get("metrics") or {}
        suffix = f"；MAPE {metrics['mape']:.2f}%" if metrics.get("mape") is not None else ""
        return f"已选择最优模型：{data.get('selectedModelName') or '未命名模型'}{suffix}。"

    @staticmethod
    def _forecast_success_message(data: dict) -> str:
        start = data.get("forecastStartDate") or "-"
        return f"未来预测已生成：{data.get('pointCount', 0)}个预测点，起始日期{start}。"

    def _context_for_handler(
        self,
        request: (
            TrainRequest
            | TrainingDataValidationRequest
            | BacktestRequest
            | PredictRequest
        ),
        handler: ModelHandler,
    ) -> ModelContext:
        if request.agent_code and request.agent_code != handler.info.agent_code:
            raise ValueError(
                f"agent_code={request.agent_code} 与 model_code={request.model_code} 不匹配"
            )
        data = request.model_dump()
        data["agent_code"] = handler.info.agent_code
        data["model_code"] = handler.info.model_code
        return ModelContext(**data)

    def _selection_metadata(self, requested_model_code: str | None, selected_model_code: str) -> dict[str, str | bool]:
        return {
            "auto_selected": False,
            "selection_scope": "model_code",
            "requested_model_code": requested_model_code or "auto",
            "selected_model_code": selected_model_code,
        }
