from gas_model_platform.models.base import ModelHandler
from gas_model_platform.models.registry import ModelRegistry
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

    def execute_forecast(self, request: ForecastExecuteRequest) -> ForecastExecuteResult:
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
        training = self.train(train_request)
        predict_request = PredictRequest(
            **common,
            train_batch_no=request.forecast_no,
            forecast_batch_no=request.forecast_no,
            dataset=request.future_dataset,
        )
        prediction = self.predict(predict_request)
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
