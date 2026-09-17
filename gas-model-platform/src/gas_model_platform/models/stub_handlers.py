from datetime import date, timedelta
from statistics import mean

from gas_model_platform.schemas.modeling import (
    BacktestPoint,
    BacktestResult,
    ForecastPoint,
    MetricSet,
    ModelContext,
    ModelInfo,
    PredictResult,
    TrainResult,
)


class BaselineHandler:
    info: ModelInfo
    date_step_days = 1

    def train(self, context: ModelContext) -> TrainResult:
        values = self._target_values(context)
        baseline = mean(values) if values else 0.0
        return TrainResult(
            agent_code=context.agent_code,
            model_code=context.model_code,
            train_batch_no=self._batch_no("TRAIN"),
            metrics=MetricSet(mae=0.0 if values else None),
            feature_names=self._feature_names(),
            metadata={"baseline": baseline, "sample_count": len(values)},
        )

    def backtest(self, context: ModelContext) -> BacktestResult:
        rows = context.dataset[-min(len(context.dataset), 12):]
        values = self._target_values(context)
        baseline = mean(values) if values else 0.0
        points = [
            BacktestPoint(
                stat_date=self._row_date(row, fallback_index=index),
                actual_value=self._row_value(row),
                predicted_value=baseline,
            )
            for index, row in enumerate(rows)
        ]
        return BacktestResult(
            agent_code=context.agent_code,
            model_code=context.model_code,
            metrics=MetricSet(mae=self._mae(points)),
            points=points,
            metadata={"baseline": baseline, "sample_count": len(values)},
        )

    def predict(self, context: ModelContext) -> PredictResult:
        values = self._target_values(context)
        baseline = mean(values) if values else 0.0
        start = self._prediction_start(context)
        step_days = {"day": 1, "tenday": 10, "month": 30}.get(
            context.forecast_unit,
            self.date_step_days,
        )
        points = [
            ForecastPoint(
                forecast_date=start + timedelta(days=index * step_days),
                prediction=round(baseline, 4),
            )
            for index in range(context.forecast_horizon)
        ]
        return PredictResult(
            agent_code=context.agent_code,
            model_code=context.model_code,
            forecast_batch_no=self._batch_no("FC"),
            points=points,
            metadata={"baseline": baseline, "sample_count": len(values)},
        )

    def _prediction_start(self, context: ModelContext) -> date:
        if context.dataset:
            row = context.dataset[0]
            value = row.get("date", row.get("forecast_date", row.get("stat_date")))
            if isinstance(value, date):
                return value
            if isinstance(value, str):
                return date.fromisoformat(value[:10])
        return date.today()

    def _feature_names(self) -> list[str]:
        return ["stat_date", "gas_sales"]

    def _target_values(self, context: ModelContext) -> list[float]:
        values: list[float] = []
        for row in context.dataset:
            value = self._row_value(row)
            if value is not None:
                values.append(value)
        return values

    def _row_value(self, row: dict) -> float | None:
        value = row.get("gas_sales", row.get("gasSales", row.get("value")))
        return float(value) if value is not None else None

    def _row_date(self, row: dict, fallback_index: int) -> date:
        value = row.get("stat_date", row.get("statDate", row.get("forecast_date")))
        if isinstance(value, date):
            return value
        if isinstance(value, str):
            return date.fromisoformat(value[:10])
        return date.today() + timedelta(days=fallback_index * self.date_step_days)

    def _mae(self, points: list[BacktestPoint]) -> float | None:
        errors = [
            abs(point.actual_value - point.predicted_value)
            for point in points
            if point.actual_value is not None
        ]
        return round(mean(errors), 4) if errors else None

    def _batch_no(self, suffix: str) -> str:
        return f"{self.info.agent_code.upper().replace('-', '_')}-{self.info.model_code}-{suffix}"


class WinterSupplyBaselineHandler(BaselineHandler):
    date_step_days = 10
    info = ModelInfo(
        agent_code="winter-supply",
        model_code="baseline-mean",
        model_name="冬季保供基线模型",
        description="旬度销量均值基线，用作冬季保供模型适配示例。",
        capabilities=["train", "backtest", "predict"],
    )

    def _feature_names(self) -> list[str]:
        return ["stat_date", "gas_sales", "avg_temp", "hdd", "extreme_cold_days"]


class MonthlySalesBaselineHandler(BaselineHandler):
    date_step_days = 31
    info = ModelInfo(
        agent_code="monthly-sales",
        model_code="MONTHLY_MODEL_001",
        model_name="月度销量基线模型",
        description="月销量均值基线，用作月度销量模型适配示例。",
        capabilities=["train", "backtest", "predict"],
    )

    def _feature_names(self) -> list[str]:
        return ["stat_date", "gas_sales", "region_code", "industry_code"]


class ShortTermBaselineHandler(BaselineHandler):
    date_step_days = 1
    info = ModelInfo(
        agent_code="short-term",
        model_code="SHORT_MODEL_001",
        model_name="短期客户基线模型",
        description="日用气均值基线，用作短期客户预测模型适配示例。",
        capabilities=["train", "backtest", "predict"],
    )

    def _feature_names(self) -> list[str]:
        return ["stat_date", "gas_sales", "region_code", "industry_code", "customer_code"]
