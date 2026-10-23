"""QuantaAlpha Auto 模式的平台 Handler。"""

from __future__ import annotations

from dataclasses import dataclass
import hashlib
from pathlib import Path
import re
from typing import Any

import joblib
import numpy as np
import pandas as pd

from gas_model_platform.core.config import settings
from gas_model_platform.schemas.modeling import (
    BacktestPoint,
    BacktestResult,
    CandidateEvaluation,
    ForecastPoint,
    MetricSet,
    ModelContext,
    ModelInfo,
    ModelIssue,
    PredictResult,
    RollingBacktestFoldMetric,
    RollingBacktestPoint,
    TrainingDataRange,
    TrainingDataValidationIssue,
    TrainingDataValidationResult,
    TrainResult,
)

from .auto_engine import AutoTrainingOutput, forecast_auto, train_auto


MODEL_CODE = "MODEL_ALPHA_AUTO_V1.0"
MODEL_VERSION = "1.0.0"
ARTIFACT_VERSION = "1.0"


@dataclass
class AlphaAutoArtifact:
    model_code: str
    model_version: str
    artifact_version: str
    history: pd.DataFrame
    selected_model_name: str
    selected_model_params: dict[str, Any]
    metrics: dict[str, Any]
    backtest_rows: list[dict[str, Any]]


class ModelAlphaAutoV1Handler:
    info = ModelInfo(
        agent_code="short-term",
        model_code=MODEL_CODE,
        model_version=MODEL_VERSION,
        model_name="Alpha Auto 自动选模 V1",
        description=(
            "从 QuantaAlpha ForecastAgent Auto 模式迁移的单变量日销量自动选模模型；"
            "按时间划分训练、验证、测试集，执行清洗、选模、调参和独立回测。"
        ),
        capabilities=["train", "backtest", "predict"],
        training_data_range=TrainingDataRange(
            type="history_length",
            frequency="day",
            minimum=365,
            recommended=730,
            continuous=True,
            description="至少需要365天连续日销量并覆盖完整年度，建议730天以上；默认使用最近90天进行独立回测。",
        ),
    )

    def validate_training_data(self, context: ModelContext) -> TrainingDataValidationResult:
        errors: list[TrainingDataValidationIssue] = []
        warnings: list[TrainingDataValidationIssue] = []
        try:
            frame = self._prepare(context.dataset)
        except ValueError as exc:
            errors.append(TrainingDataValidationIssue(
                code="INVALID_DATASET", message=str(exc)
            ))
            return TrainingDataValidationResult(
                agent_code=self.info.agent_code, model_code=self.info.model_code,
                valid=False, errors=errors,
            )

        dates = pd.DatetimeIndex(frame["date"])
        duplicate_count = int(dates.duplicated().sum())
        missing_dates = pd.date_range(dates.min(), dates.max(), freq="D").difference(dates)
        if len(frame) < self.info.training_data_range.minimum:
            errors.append(TrainingDataValidationIssue(
                code="INSUFFICIENT_HISTORY",
                message=f"Alpha Auto 模型至少需要{self.info.training_data_range.minimum}天日销量数据",
                expected=self.info.training_data_range.minimum, actual=len(frame),
            ))
        if duplicate_count:
            errors.append(TrainingDataValidationIssue(
                code="DUPLICATE_DATES", message=f"训练数据存在{duplicate_count}个重复日期",
                expected=0, actual=duplicate_count,
            ))
        if len(missing_dates):
            errors.append(TrainingDataValidationIssue(
                code="NON_CONTINUOUS_DATES", message=f"训练数据缺少{len(missing_dates)}个日期",
                expected=0, actual=len(missing_dates),
            ))
        if (frame["gas_sales"] < 0).any():
            errors.append(TrainingDataValidationIssue(
                code="NEGATIVE_GAS_SALES", message="gas_sales 不能为负数",
                expected=">= 0", actual=int((frame["gas_sales"] < 0).sum()),
            ))
        if len(frame) < (self.info.training_data_range.recommended or 0):
            warnings.append(TrainingDataValidationIssue(
                code="HISTORY_BELOW_RECOMMENDED",
                message=f"建议提供至少{self.info.training_data_range.recommended}天数据以提高选模稳定性",
                expected=self.info.training_data_range.recommended, actual=len(frame),
            ))
        return TrainingDataValidationResult(
            agent_code=self.info.agent_code,
            model_code=self.info.model_code,
            valid=not errors,
            summary={
                "row_count": len(frame),
                "start_date": str(dates.min().date()),
                "end_date": str(dates.max().date()),
                "missing_date_count": len(missing_dates),
                "duplicate_date_count": duplicate_count,
            },
            errors=errors,
            warnings=warnings,
        )

    def train(self, context: ModelContext) -> TrainResult:
        if not context.train_batch_no:
            raise ValueError("Alpha Auto 模型训练需要 train_batch_no")
        frame = self._prepare(context.dataset)
        params = context.params or {}
        output = train_auto(
            frame,
            holdout_size=int(params.get("holdout_size", 90)),
            candidate_models=self._candidate_models(params),
            tune_iterations=int(params.get("tune_iterations", 12)),
            metric=str(params.get("selection_metric", "rmse")),
        )
        backtest_rows = self._backtest_rows(output)
        artifact = AlphaAutoArtifact(
            model_code=self.info.model_code,
            model_version=self.info.model_version,
            artifact_version=ARTIFACT_VERSION,
            history=output.history,
            selected_model_name=output.best_model,
            selected_model_params=output.tuned_params,
            metrics=output.test_metrics,
            backtest_rows=backtest_rows,
        )
        path = self._artifact_path(context.train_batch_no)
        if path.exists():
            raise ValueError(f"训练批次已存在，禁止覆盖: {context.train_batch_no}")
        path.parent.mkdir(parents=True, exist_ok=False)
        joblib.dump(artifact, path)

        ranked = self._candidate_evaluations(output)
        rolling = [self._rolling_point(row, output.best_model) for row in backtest_rows]
        issues = [ModelIssue(
            severity="warning", stage=item["stage"], model_name=output.best_model,
            category="AUTO_PIPELINE_WARNING", message=item["message"],
        ) for item in output.issues]
        return TrainResult(
            agent_code=self.info.agent_code,
            model_code=self.info.model_code,
            train_batch_no=context.train_batch_no,
            metrics=self._metric_set(output.test_metrics),
            feature_names=["历史日销量", "日期派生特征", "滞后与滚动特征（按候选模型使用）"],
            selected_model_name=output.best_model,
            selection_reason="验证集按指定指标自动选择模型，独立测试集仅用于最终评估。",
            requested_candidate_count=len(self._candidate_models(params)),
            successful_candidate_count=len(output.candidate_metrics),
            ranked_candidate_count=len(ranked),
            selected_model_params=output.tuned_params,
            candidate_evaluations=ranked,
            rolling_backtest_results=rolling,
            rolling_backtest_fold_metrics=[RollingBacktestFoldMetric(
                model_name=output.best_model,
                season=(
                    f"{pd.Timestamp(output.test['date'].min()).date()}~"
                    f"{pd.Timestamp(output.test['date'].max()).date()}"
                ),
                stage="independent_test",
                metrics=self._metric_set(output.test_metrics),
            )],
            issues=issues,
            metadata={
                "algorithm_source": "quanta-alpha/agent_core/agent/forecast_agent.py::_run_auto",
                "artifact_version": ARTIFACT_VERSION,
                "artifact_sha256": self._sha256(path),
                "train_rows": len(output.train),
                "validation_rows": len(output.validation),
                "test_rows": len(output.test),
                "outlier_bounds": output.outlier_bounds,
            },
        )

    def backtest(self, context: ModelContext) -> BacktestResult:
        if context.dataset:
            validation = self.validate_training_data(context)
            if not validation.valid:
                raise ValueError("回测数据不满足要求：" + "；".join(x.message for x in validation.errors))
            output = train_auto(
                self._prepare(context.dataset),
                holdout_size=int((context.params or {}).get("holdout_size", 90)),
                candidate_models=self._candidate_models(context.params or {}),
                tune_iterations=int((context.params or {}).get("tune_iterations", 12)),
                metric=str((context.params or {}).get("selection_metric", "rmse")),
            )
            rows = self._backtest_rows(output)
            metrics = output.test_metrics
            model_name = output.best_model
        else:
            artifact = self._load_artifact(context.train_batch_no or "")
            rows = artifact.backtest_rows
            metrics = artifact.metrics
            model_name = artifact.selected_model_name
        return BacktestResult(
            agent_code=self.info.agent_code,
            model_code=self.info.model_code,
            metrics=self._metric_set(metrics),
            points=[BacktestPoint(
                stat_date=pd.Timestamp(row["date"]).date(),
                actual_value=float(row["actual"]),
                predicted_value=float(row["prediction"]),
            ) for row in rows],
            metadata={"selected_model_name": model_name, "train_batch_no": context.train_batch_no},
        )

    def predict(self, context: ModelContext) -> PredictResult:
        if context.forecast_unit != "day":
            raise ValueError(f"{self.info.model_code} 的 forecast_unit 必须是 day")
        artifact = self._load_artifact(context.train_batch_no or "")
        requested = pd.DatetimeIndex(pd.to_datetime(
            [row.get("date") for row in context.dataset], errors="coerce"
        ))
        if len(requested) != context.forecast_horizon or requested.isna().any():
            raise ValueError("预测数据集必须为每个预测周期提供有效 date")
        if requested.has_duplicates or not requested.is_monotonic_increasing:
            raise ValueError("预测数据集 date 必须递增且不能重复")
        last_date = pd.Timestamp(artifact.history["date"].max())
        if requested.min() <= last_date:
            raise ValueError(f"预测开始日期必须晚于训练数据结束日期 {last_date.date()}")
        periods = int((requested.max() - last_date).days)
        forecast = forecast_auto(
            artifact.history,
            model_name=artifact.selected_model_name,
            params=artifact.selected_model_params,
            horizon=periods,
        )
        forecast["date"] = pd.to_datetime(forecast["date"])
        forecast = forecast.set_index("date").reindex(requested)
        if forecast["gas_sales"].isna().any():
            raise ValueError("模型未能生成全部请求日期的预测值")
        lower_col = "gas_sales_lower"
        upper_col = "gas_sales_upper"
        points = []
        for date, row in forecast.iterrows():
            lower = self._finite_or_none(row.get(lower_col))
            upper = self._finite_or_none(row.get(upper_col))
            points.append(ForecastPoint(
                forecast_date=date.date(),
                prediction=float(row["gas_sales"]),
                lower_value=lower,
                upper_value=upper,
                extra={"selected_model_name": artifact.selected_model_name},
            ))
        return PredictResult(
            agent_code=self.info.agent_code,
            model_code=self.info.model_code,
            forecast_batch_no=context.forecast_batch_no or "",
            points=points,
            metadata={
                "train_batch_no": context.train_batch_no,
                "selected_model_name": artifact.selected_model_name,
                "selected_model_params": artifact.selected_model_params,
            },
        )

    @staticmethod
    def _prepare(rows: list[dict[str, Any]]) -> pd.DataFrame:
        if not rows:
            raise ValueError("dataset 不能为空")
        frame = pd.DataFrame(rows)
        missing = [name for name in ("date", "gas_sales") if name not in frame.columns]
        if missing:
            raise ValueError(f"训练数据缺少字段：{', '.join(missing)}")
        frame = frame[["date", "gas_sales"]].copy()
        frame["date"] = pd.to_datetime(frame["date"], errors="coerce")
        frame["gas_sales"] = pd.to_numeric(frame["gas_sales"], errors="coerce")
        if frame["date"].isna().any():
            raise ValueError("date 存在无法解析的日期")
        if frame["gas_sales"].isna().any():
            raise ValueError("gas_sales 存在空值或非数值")
        return frame.sort_values("date").reset_index(drop=True)

    @staticmethod
    def _candidate_models(params: dict[str, Any]) -> list[str]:
        from .tools.forecast.optimization_tools import DEFAULT_SELECT_MODELS
        raw = params.get("candidate_models")
        if raw is None:
            return list(DEFAULT_SELECT_MODELS)
        if not isinstance(raw, list) or not raw or not all(isinstance(x, str) for x in raw):
            raise ValueError("params.candidate_models 必须是非空字符串数组")
        unknown = sorted(set(raw) - set(DEFAULT_SELECT_MODELS))
        if unknown:
            raise ValueError(f"不支持的候选模型：{', '.join(unknown)}")
        return list(dict.fromkeys(raw))

    @staticmethod
    def _backtest_rows(output: AutoTrainingOutput) -> list[dict[str, Any]]:
        return [{
            "date": pd.Timestamp(date).date().isoformat(),
            "actual": float(actual),
            "prediction": float(prediction),
        } for date, actual, prediction in zip(
            output.test["date"], output.test["gas_sales"], output.test_predictions
        )]

    @staticmethod
    def _metric_set(values: dict[str, Any]) -> MetricSet:
        def finite(name: str) -> float | None:
            value = values.get(name)
            return float(value) if value is not None and np.isfinite(float(value)) else None
        return MetricSet(
            mape=finite("mape"), wmape=finite("wmape"), smape=finite("smape"),
            rmse=finite("rmse"), mae=finite("mae"), r2=finite("r2"),
        )

    def _candidate_evaluations(self, output: AutoTrainingOutput) -> list[CandidateEvaluation]:
        ranked = sorted(output.candidate_metrics.items(), key=lambda item: item[1].get("rmse", float("inf")))
        return [CandidateEvaluation(
            rank=index,
            model_name=name,
            model_type="quanta-alpha-auto-candidate",
            selected=name == output.best_model,
            metrics=self._metric_set(metrics),
        ) for index, (name, metrics) in enumerate(ranked, 1)]

    @staticmethod
    def _rolling_point(row: dict[str, Any], model_name: str) -> RollingBacktestPoint:
        actual = float(row["actual"])
        predicted = float(row["prediction"])
        error = predicted - actual
        return RollingBacktestPoint(
            model_name=model_name,
            season="independent_test",
            stat_date=pd.Timestamp(row["date"]).date(),
            actual_value=actual,
            predicted_value=predicted,
            error_value=error,
            absolute_error=abs(error),
            error_rate=abs(error) / abs(actual) * 100 if actual else None,
        )

    def _load_artifact(self, batch_no: str) -> AlphaAutoArtifact:
        path = self._artifact_path(batch_no)
        if not path.exists():
            raise FileNotFoundError(f"训练产物不存在: {batch_no}")
        artifact = joblib.load(path)
        if not isinstance(artifact, AlphaAutoArtifact):
            raise ValueError("训练产物类型不正确")
        if artifact.model_code != self.info.model_code:
            raise ValueError("训练产物模型类型与当前 model_code 不一致")
        if artifact.model_version != self.info.model_version or artifact.artifact_version != ARTIFACT_VERSION:
            raise ValueError("训练产物版本与当前模型不兼容")
        return artifact

    @staticmethod
    def _artifact_path(batch_no: str) -> Path:
        if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,64}", batch_no):
            raise ValueError("train_batch_no 格式不正确")
        return settings.artifact_root / batch_no / "model.joblib"

    @staticmethod
    def _sha256(path: Path) -> str:
        digest = hashlib.sha256()
        with path.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
        return digest.hexdigest()

    @staticmethod
    def _finite_or_none(value: Any) -> float | None:
        return float(value) if value is not None and np.isfinite(float(value)) else None
