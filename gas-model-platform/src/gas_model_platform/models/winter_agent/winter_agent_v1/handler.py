from __future__ import annotations

import math
import re
from datetime import date
from pathlib import Path
from typing import Any
from uuid import uuid4

import pandas as pd

from gas_model_platform.core.config import settings
from gas_model_platform.models.winter_agent.winter_agent_v1.engine import (
    load_artifact,
    predict as run_prediction,
    save_artifact,
    train as run_training,
)
from gas_model_platform.models.winter_agent.winter_agent_v1.engine.config import (
    DEFAULT_CONFIG,
    load_config,
)
from gas_model_platform.models.winter_agent.winter_agent_v1.engine.version import (
    MODEL_VERSION,
)
from gas_model_platform.models.winter_agent.winter_agent_v1.result_store import (
    WinterSupplyResult,
    WinterSupplyResultStore,
    parse_date,
)
from gas_model_platform.schemas.modeling import (
    BacktestPoint,
    BacktestResult,
    CandidateEvaluation,
    ForecastPoint,
    MetricSet,
    ModelIssue,
    ModelContext,
    ModelInfo,
    PredictResult,
    RollingBacktestFoldMetric,
    RollingBacktestPoint,
    TrainResult,
)


class WinterAgentV1Handler:
    """冬供旬预测统一入口。

    携带 dataset 时运行迁入的真实算法；没有 dataset 时继续读取既有省份
    JSON，兼容原有演示行为。
    """

    info = ModelInfo(
        agent_code="winter-supply",
        model_code="WINTER_MODEL_001",
        model_version=MODEL_VERSION,
        model_name="冬季保供模型总入口",
        description="训练多类冬供旬预测候选模型，滚动回测后自动选择最佳模型。",
        capabilities=["train", "backtest", "predict"],
    )

    def __init__(self, store: WinterSupplyResultStore | None = None) -> None:
        self.store = store or WinterSupplyResultStore()

    def train(self, context: ModelContext) -> TrainResult:
        if not context.dataset:
            return self._static_train(context)

        if not context.train_batch_no:
            raise ValueError("冬季保供训练需要提供顶层 train_batch_no")
        batch_no = self._safe_batch_no(context.train_batch_no)
        artifact_path = self._artifact_path(batch_no)
        if artifact_path.exists():
            raise ValueError(f"训练批次已存在，禁止覆盖: {batch_no}")

        province = self._province(context)
        trained = run_training(
            pd.DataFrame(context.dataset),
            province=province,
            config=self._training_config(context.params),
        )
        trained.artifact.metadata.update(
            {
                "region_code": context.region_code,
                "region_name": context.region_name,
            }
        )
        saved = save_artifact(
            trained.artifact,
            artifact_path,
        )
        return TrainResult(
            agent_code=context.agent_code,
            model_code=self.info.model_code,
            train_batch_no=batch_no,
            metrics=self._metrics(trained.metrics),
            feature_names=trained.features,
            selected_model_name=trained.best_model,
            selection_reason=trained.selection_reason,
            requested_candidate_count=trained.requested_candidate_count,
            successful_candidate_count=trained.successful_candidate_count,
            ranked_candidate_count=trained.ranked_candidate_count,
            selected_model_params=self._json_safe(trained.selected_model_params),
            candidate_evaluations=self._candidate_evaluations(
                trained.model_ranking,
                trained.best_model,
            ),
            rolling_backtest_results=self._rolling_backtest_results(
                trained.backtest_results
            ),
            rolling_backtest_fold_metrics=self._rolling_backtest_fold_metrics(
                trained.fold_metrics
            ),
            issues=[ModelIssue.model_validate(issue) for issue in trained.issues],
            metadata=self._json_safe(
                {
                    "province": province,
                    "region_code": context.region_code,
                    "region_name": context.region_name,
                    "selected_inner_model": trained.best_model,
                    "selection_reason": trained.selection_reason,
                    "candidate_selection": "rolling_backtest_best_mape",
                    "artifact_sha256": saved.sha256,
                    "model_version": saved.model_version,
                    "artifact_version": saved.artifact_version,
                    "summary": trained.summary,
                    "model_ranking": trained.model_ranking,
                    "fold_metrics": trained.fold_metrics,
                    "clean_log": trained.clean_log,
                    "issues": trained.issues,
                    "errors": trained.errors,
                }
            ),
        )

    def backtest(self, context: ModelContext) -> BacktestResult:
        if not context.dataset:
            return self._static_backtest(context)

        province = self._province(context)
        trained = run_training(
            pd.DataFrame(context.dataset),
            province=province,
            config=self._training_config(context.params),
        )
        points = [
            BacktestPoint(
                stat_date=parse_date(row["date"]),
                actual_value=self._number(row.get("actual")),
                predicted_value=float(row["prediction"]),
            )
            for row in trained.backtest_results
        ]
        return BacktestResult(
            agent_code=context.agent_code,
            model_code=self.info.model_code,
            metrics=self._metrics(trained.metrics),
            points=points,
            issues=[ModelIssue.model_validate(issue) for issue in trained.issues],
            metadata=self._json_safe(
                {
                    "province": province,
                    "selected_inner_model": trained.best_model,
                    "candidate_selection": "rolling_backtest_best_mape",
                    "feature_names": trained.features,
                    "model_ranking": trained.model_ranking,
                    "fold_metrics": trained.fold_metrics,
                    "issues": trained.issues,
                    "errors": trained.errors,
                }
            ),
        )

    def predict(self, context: ModelContext) -> PredictResult:
        if "artifact_path" in context.params:
            raise ValueError(
                "params.artifact_path 不再支持，请传顶层 train_batch_no"
            )
        if "train_batch_no" in context.params:
            raise ValueError(
                "params.train_batch_no 不再支持，请传顶层 train_batch_no"
            )
        train_batch_no = context.train_batch_no
        if not context.dataset:
            raise ValueError("冬季保供预测需要在 dataset 中提供未来旬日期和气象数据")
        if context.forecast_unit != "tenday":
            raise ValueError("冬季保供模型的 forecast_unit 必须是 tenday")
        if len(context.dataset) < context.forecast_horizon:
            raise ValueError(
                "冬季保供预测天气数据条数不能少于 forecast_horizon"
            )
        if train_batch_no:
            artifact_file = self._artifact_path(str(train_batch_no))
        else:
            raise ValueError(
                "冬季保供预测需要提供训练接口返回的顶层 train_batch_no"
            )

        if not artifact_file.exists():
            raise FileNotFoundError(f"训练批次对应的模型产物不存在: {artifact_file}")
        artifact = load_artifact(artifact_file)
        self._validate_artifact_region(context, artifact)
        prediction = run_prediction(artifact, pd.DataFrame(context.dataset))
        point_extra = {"inner_model": prediction.model_name}
        if artifact.province:
            point_extra["province"] = artifact.province
        points = [
            ForecastPoint(
                forecast_date=parse_date(row["date"]),
                prediction=float(row["forecast_value"]),
                lower_value=self._number(row.get("lower_value")),
                upper_value=self._number(row.get("upper_value")),
                extra=point_extra,
            )
            for row in prediction.results
        ]
        return PredictResult(
            agent_code=context.agent_code,
            model_code=self.info.model_code,
            forecast_batch_no=f"WGFC-{uuid4().hex[:16]}",
            points=self._window(points, context.forecast_horizon),
            metadata={
                "province": artifact.province,
                "region_code": artifact.metadata.get("region_code"),
                "region_name": artifact.metadata.get("region_name") or artifact.province,
                "selected_inner_model": prediction.model_name,
                "train_batch_no": str(train_batch_no),
                "model_version": artifact.model_version,
                "artifact_version": artifact.artifact_version,
            },
        )

    def _static_train(self, context: ModelContext) -> TrainResult:
        result = self._stored(context)
        return TrainResult(
            agent_code=context.agent_code,
            model_code=self.info.model_code,
            train_batch_no=f"WGTRAIN-{result.province}",
            metrics=self._metrics(result.metrics),
            feature_names=self._feature_names(result),
            selected_model_name=result.model_name,
            selection_reason="读取已迁移的省份最佳模型结果，未在本次请求中重新训练。",
            requested_candidate_count=1,
            successful_candidate_count=1,
            ranked_candidate_count=1,
            metadata=self._stored_metadata(result, context),
        )

    def _static_backtest(self, context: ModelContext) -> BacktestResult:
        result = self._stored(context)
        raw = result.raw
        dates = raw.get("dates") or []
        actual = raw.get("actual") or []
        predicted = raw.get("predicted") or []
        lower = self._series(raw.get("lower_bound"), len(dates))
        upper = self._series(raw.get("upper_bound"), len(dates))
        points = [
            BacktestPoint(
                stat_date=parse_date(stat_date),
                actual_value=self._at(actual, index),
                predicted_value=self._at(predicted, index) or 0.0,
                lower_value=self._at(lower, index),
                upper_value=self._at(upper, index),
            )
            for index, stat_date in enumerate(dates)
        ]
        return BacktestResult(
            agent_code=context.agent_code,
            model_code=self.info.model_code,
            metrics=self._metrics(result.metrics),
            points=points,
            metadata=self._stored_metadata(result, context),
        )

    def _static_predict(self, context: ModelContext) -> PredictResult:
        result = self._stored(context)
        raw = result.raw
        dates = [parse_date(value) for value in raw.get("future_dates") or []]
        predicted = raw.get("future_predicted") or []
        lower = self._series(raw.get("future_lower"), len(dates))
        upper = self._series(raw.get("future_upper"), len(dates))
        points = [
            ForecastPoint(
                forecast_date=forecast_date,
                prediction=self._at(predicted, index) or 0.0,
                lower_value=self._at(lower, index),
                upper_value=self._at(upper, index),
                extra={"tenday_label": self._tenday_label(forecast_date), "province": result.province},
            )
            for index, forecast_date in enumerate(dates)
        ]
        return PredictResult(
            agent_code=context.agent_code,
            model_code=self.info.model_code,
            forecast_batch_no=f"WGFC-{result.province}",
            points=self._window(points, context.forecast_horizon),
            metrics=self._metrics(result.metrics),
            metadata=self._stored_metadata(result, context),
        )

    def _province(self, context: ModelContext) -> str | None:
        province = (
            context.params.get("province")
            or context.params.get("province_name")
            or context.region_name
            or context.region_code
        )
        return str(province) if province else None

    def _training_config(self, params: dict[str, Any]) -> dict[str, Any]:
        supplied = params.get("config") or {}
        if not isinstance(supplied, dict):
            raise ValueError("params.config 必须是对象")
        profile = supplied.get("profile", params.get("profile", DEFAULT_CONFIG["profile"]))
        config = load_config(profile=str(profile))
        config.update(supplied)
        for key in DEFAULT_CONFIG:
            if key in params:
                config[key] = params[key]
        if "model_names" in params:
            config["model_names"] = params["model_names"]
        return config

    def _artifact_path(self, batch_no: str) -> Path:
        safe_batch_no = self._safe_batch_no(batch_no)
        return (
            settings.artifact_root
            / safe_batch_no
            / "model.joblib"
        )

    def _safe_batch_no(self, value: str) -> str:
        batch_no = str(value).strip()
        if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,64}", batch_no):
            raise ValueError(
                "train_batch_no 格式不正确，只能包含字母、数字、点、下划线和中划线"
            )
        return batch_no

    def _validate_artifact_region(self, context: ModelContext, artifact: Any) -> None:
        """避免预测时误用其他省份训练出的模型。"""
        artifact_code = artifact.metadata.get("region_code")
        artifact_name = artifact.metadata.get("region_name") or artifact.province
        if context.region_code and artifact_code and context.region_code != artifact_code:
            raise ValueError(
                f"预测区域 {context.region_code} 与模型产物区域 {artifact_code} 不一致"
            )
        if context.region_name and artifact_name and context.region_name != artifact_name:
            raise ValueError(
                f"预测省份 {context.region_name} 与模型产物省份 {artifact_name} 不一致"
            )

    def _stored(self, context: ModelContext) -> WinterSupplyResult:
        return self.store.get_by_context(context.region_code, context.region_name, context.params)

    def _metrics(self, metrics: dict[str, Any]) -> MetricSet:
        values = {str(key).lower(): value for key, value in metrics.items()}
        return MetricSet(
            mape=self._number(values.get("mape")),
            wmape=self._number(values.get("wmape")),
            smape=self._number(values.get("smape")),
            mae=self._number(values.get("mae")),
            rmse=self._number(values.get("rmse")),
            r2=self._number(values.get("r2")),
        )

    def _candidate_evaluations(
        self,
        ranking: list[dict[str, Any]],
        selected_model: str,
    ) -> list[CandidateEvaluation]:
        evaluations: list[CandidateEvaluation] = []
        for index, row in enumerate(ranking):
            name = str(row.get("model", "unknown"))
            constituents = row.get("constituents")
            evaluations.append(
                CandidateEvaluation(
                    rank=int(row.get("rank", index + 1)),
                    model_name=name,
                    model_type=str(row.get("type", "unknown")),
                    selected=name == selected_model,
                    metrics=self._metrics(row),
                    constituents=(
                        [part for part in str(constituents).split("|") if part]
                        if constituents
                        else []
                    ),
                )
            )
        return evaluations

    def _rolling_backtest_results(
        self,
        rows: list[dict[str, Any]],
    ) -> list[RollingBacktestPoint]:
        results = []
        for row in rows:
            actual = float(row["actual"])
            predicted = float(row["prediction"])
            error = predicted - actual
            results.append(
                RollingBacktestPoint(
                    model_name=str(row.get("model", "unknown")),
                    season=str(row.get("season", "unknown")),
                    stat_date=parse_date(row["date"]),
                    actual_value=actual,
                    predicted_value=predicted,
                    error_value=error,
                    absolute_error=abs(error),
                    error_rate=(abs(error) / abs(actual) * 100 if actual else None),
                )
            )
        return results

    def _rolling_backtest_fold_metrics(
        self,
        rows: list[dict[str, Any]],
    ) -> list[RollingBacktestFoldMetric]:
        return [
            RollingBacktestFoldMetric(
                model_name=str(row.get("model", "unknown")),
                season=str(row.get("season", "unknown")),
                stage=str(row.get("stage", "unknown")),
                metrics=self._metrics(row),
            )
            for row in rows
        ]

    def _stored_metadata(self, result: WinterSupplyResult, context: ModelContext) -> dict[str, Any]:
        raw = result.raw
        return {
            "province": result.province,
            "selected_inner_model": result.model_name,
            "model_version": MODEL_VERSION,
            "candidate_selection": "migrated_best_result",
            "history_point_count": len(raw.get("history_dates") or []),
            "backtest_point_count": len(raw.get("dates") or []),
            "future_point_count": len(raw.get("future_dates") or []),
            "requested_region_code": context.region_code,
            "requested_region_name": context.region_name,
        }

    def _feature_names(self, result: WinterSupplyResult) -> list[str]:
        features = result.raw.get("features")
        if isinstance(features, list):
            return [str(feature) for feature in features]
        return ["stat_date", "gas_sales", "avg_temp", "max_temp", "min_temp", "hdd", "extreme_cold_days"]

    def _window(
        self,
        points: list[ForecastPoint],
        forecast_horizon: int,
    ) -> list[ForecastPoint]:
        return points[:forecast_horizon]

    def _series(self, value: Any, length: int) -> list[Any]:
        return value if isinstance(value, list) else [None] * length

    def _at(self, values: list[Any], index: int) -> float | None:
        return None if index >= len(values) else self._number(values[index])

    def _number(self, value: Any) -> float | None:
        if value is None:
            return None
        number = float(value)
        return number if math.isfinite(number) else None

    def _json_safe(self, value: Any) -> Any:
        if isinstance(value, dict):
            return {str(key): self._json_safe(item) for key, item in value.items()}
        if isinstance(value, list):
            return [self._json_safe(item) for item in value]
        if isinstance(value, float) and not math.isfinite(value):
            return None
        return value

    def _tenday_label(self, value: date) -> str:
        tenday = min((value.day - 1) // 10 + 1, 3)
        return f"{value.month}月第{tenday}旬"
