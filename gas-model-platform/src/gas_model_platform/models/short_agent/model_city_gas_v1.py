"""城市燃气 v3 原算法的平台适配层。"""
from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any
import joblib
import pandas as pd

from gas_model_platform.core.config import settings
from gas_model_platform.models.short_agent import city_gas_v3_engine as engine
from gas_model_platform.schemas.modeling import (
    BacktestPoint, BacktestResult, ForecastPoint, MetricSet, ModelContext,
    ModelInfo, PredictResult, RollingBacktestPoint, TrainingDataRange, TrainingDataValidationIssue,
    TrainingDataValidationResult, TrainResult,
)

PROPHET_PARAMS = {"seasonality_mode": "additive", "changepoint_prior_scale": 0.05,
                  "seasonality_prior_scale": 10.0, "holidays_prior_scale": 20.0}


@dataclass
class CityGasV3Artifact:
    history: pd.DataFrame
    regressors: list[str]
    selected_version: str
    evaluations: dict[str, dict[str, float | None]]
    artifact_version: str = "1.0"


class ModelCityGasV1Handler:
    info = ModelInfo(
        agent_code="short-term", model_code="MODEL_CITY_GAS_V1.0",
        model_version="1.0.0", model_name="城市燃气预测模型 V1",
        description="支持范围：城市燃气行业，支持任意地区和任意客户；完整保留城燃 v3 的 Prophet、LightGBM、AR 备用、节假日、天气特征和滚动回测逻辑。",
        capabilities=["train", "backtest", "predict"],
        training_data_range=TrainingDataRange(
            type="history_length", frequency="day", minimum=365, recommended=730,
            continuous=True, description="至少需要365天连续日数据并覆盖完整年度，推荐730天；避免短历史进行50天外推时趋势失真。"))

    def validate_training_data(self, context: ModelContext) -> TrainingDataValidationResult:
        frame, _ = self._prepare(context.dataset)
        dates = pd.DatetimeIndex(frame["ds"])
        missing = pd.date_range(dates.min(), dates.max(), freq="D").difference(dates)
        duplicate_count = int(dates.duplicated().sum())
        errors = []
        if len(frame) < 365:
            errors.append(TrainingDataValidationIssue(code="INSUFFICIENT_HISTORY",
                message="城市燃气模型至少需要365天连续日数据", expected=365, actual=len(frame)))
        if len(missing):
            errors.append(TrainingDataValidationIssue(code="NON_CONTINUOUS_DATES",
                message=f"训练数据缺少{len(missing)}个日期", expected=0, actual=len(missing)))
        if duplicate_count:
            errors.append(TrainingDataValidationIssue(code="DUPLICATE_DATES",
                message=f"训练数据存在{duplicate_count}个重复日期", expected=0, actual=duplicate_count))
        return TrainingDataValidationResult(agent_code=self.info.agent_code,
            model_code=self.info.model_code, valid=not errors,
            summary={"row_count": len(frame), "start_date": str(dates.min().date()),
                     "end_date": str(dates.max().date()), "missing_date_count": len(missing)},
            errors=errors)

    def train(self, context: ModelContext) -> TrainResult:
        if not context.train_batch_no:
            raise ValueError("城市燃气模型训练需要 train_batch_no")
        frame, regs = self._prepare(context.dataset)
        evaluations = self._evaluate(frame, regs, True)
        winner = min(evaluations, key=lambda x: evaluations[x]["mape"] or float("inf"))
        path = self._artifact_path(context.train_batch_no)
        if path.exists():
            raise ValueError(f"训练批次已存在，禁止覆盖: {context.train_batch_no}")
        path.parent.mkdir(parents=True, exist_ok=True)
        artifact_evaluations = {
            version: {key: value for key, value in evaluation.items() if key != "points"}
            for version, evaluation in evaluations.items()
        }
        joblib.dump(CityGasV3Artifact(frame, regs, winner, artifact_evaluations), path)
        chosen = evaluations[winner]
        rolling = []
        season = f"{chosen['points']['ds'].min().date()}~{chosen['points']['ds'].max().date()}"
        for row in chosen["points"].itertuples():
            actual, predicted = float(row.y), float(row.yhat)
            error = predicted - actual
            rolling.append(RollingBacktestPoint(
                model_name=self._name(winner), season=season,
                stat_date=(row.ds + pd.Timedelta(days=engine.DS_SHIFT_DAYS)).date(),
                actual_value=actual, predicted_value=predicted,
                error_value=error, absolute_error=abs(error),
                error_rate=abs(error) / abs(actual) * 100 if actual else None))
        return TrainResult(agent_code=self.info.agent_code, model_code=self.info.model_code,
            train_batch_no=context.train_batch_no,
            metrics=MetricSet(rmse=chosen["rmse"], mape=chosen["mape"]),
            feature_names=regs, selected_model_name=self._name(winner),
            selection_reason="按原脚本滚动回测 MAPE 在 v1 和 v3 中选择优胜模型。",
            requested_candidate_count=2, successful_candidate_count=2,
            ranked_candidate_count=2,
            rolling_backtest_results=rolling,
            metadata={"algorithm_source": "predict_jiangsu_城燃_v3.py",
                      "selected_version": winner, "ds_shift_days": engine.DS_SHIFT_DAYS})

    def backtest(self, context: ModelContext) -> BacktestResult:
        frame, regs = self._prepare(context.dataset)
        evaluations = self._evaluate(frame, regs, True)
        winner = min(evaluations, key=lambda x: evaluations[x]["mape"] or float("inf"))
        chosen = evaluations[winner]
        return BacktestResult(agent_code=self.info.agent_code, model_code=self.info.model_code,
            metrics=MetricSet(rmse=chosen["rmse"], mape=chosen["mape"]),
            points=[BacktestPoint(stat_date=(r.ds + pd.Timedelta(days=engine.DS_SHIFT_DAYS)).date(),
                actual_value=float(r.y), predicted_value=float(r.yhat),
                lower_value=float(r.yhat_lower), upper_value=float(r.yhat_upper))
                for r in chosen["points"].itertuples()], metadata={"selected_version": winner})

    def predict(self, context: ModelContext) -> PredictResult:
        if context.forecast_unit != "day":
            raise ValueError("MODEL_CITY_GAS_V1.0 的 forecast_unit 必须是 day")
        artifact = joblib.load(self._artifact_path(context.train_batch_no or ""))
        requested_dates = pd.DatetimeIndex(pd.to_datetime(
            [row.get("date") for row in context.dataset], errors="coerce"))
        if len(requested_dates) != context.forecast_horizon or requested_dates.isna().any():
            raise ValueError("预测数据集必须为每个预测周期提供有效 date")
        if requested_dates.has_duplicates or not requested_dates.is_monotonic_increasing:
            raise ValueError("预测数据集 date 必须递增且不能重复")
        last_actual_date = artifact.history["ds"].max() + pd.Timedelta(days=engine.DS_SHIFT_DAYS)
        if requested_dates.min() <= last_actual_date:
            raise ValueError(f"预测开始日期必须晚于训练数据结束日期 {last_actual_date.date()}")
        # 原算法按训练末日向后生成。配置的开始日期可能与训练末日之间存在间隔，
        # 因此先覆盖到请求的最后一天，再严格按 dataset 中的日期返回结果。
        periods = int((requested_dates.max() - last_actual_date).days)
        fn = engine.run_single_forecast_v3 if artifact.selected_version == "v3" else engine.run_single_forecast_v1
        result, _, _ = fn(artifact.history, artifact.regressors, periods,
                          "D", PROPHET_PARAMS, ar_order=4)
        future = result[result["y"].isna()].copy()
        future["ds"] += pd.Timedelta(days=engine.DS_SHIFT_DAYS)
        future = future.set_index("ds").reindex(requested_dates).reset_index(names="ds")
        if future["yhat"].isna().any():
            raise ValueError("模型未能生成全部请求日期的预测值")
        return PredictResult(agent_code=self.info.agent_code, model_code=self.info.model_code,
            forecast_batch_no=context.forecast_batch_no or "",
            points=[ForecastPoint(forecast_date=r.ds.date(), prediction=float(r.yhat),
                lower_value=float(r.yhat_lower), upper_value=float(r.yhat_upper),
                extra={"algorithm": self._name(artifact.selected_version)}) for r in future.itertuples()],
            metadata={"train_batch_no": context.train_batch_no,
                      "selected_version": artifact.selected_version})

    @staticmethod
    def _prepare(rows: list[dict[str, Any]]) -> tuple[pd.DataFrame, list[str]]:
        frame = pd.DataFrame(rows).rename(columns={"date": "ds", "gas_sales": "y"})
        frame["ds"], frame["y"] = pd.to_datetime(frame["ds"]), pd.to_numeric(frame["y"], errors="coerce")
        regs = [c for c in frame if c not in ("ds", "y")]
        frame[regs] = frame[regs].apply(pd.to_numeric, errors="coerce").interpolate(limit_direction="both").ffill().bfill()
        frame, engineered = engine.add_engineered_features(frame, regs)
        regs = list(dict.fromkeys([*regs, *engineered]))
        frame["ds"] -= pd.Timedelta(days=engine.DS_SHIFT_DAYS)
        return frame.dropna(subset=["ds", "y"]).sort_values("ds").reset_index(drop=True), regs

    @staticmethod
    def _evaluate(frame, regs, points=False):
        end = frame["ds"].max()
        start = max(frame["ds"].min() + pd.Timedelta(days=90), end - pd.Timedelta(days=119))
        output = {}
        for version in ("v1", "v3"):
            rows, rmse, mape = engine.rolling_backtest(frame, regs, 50, "D", 90, 50,
                start, end, PROPHET_PARAMS, 4, version)
            output[version] = {"rmse": rmse, "mape": mape, "points": rows if points else None}
        return output

    @staticmethod
    def _name(version):
        return "Prophet+LightGBM(v3)" if version == "v3" else "Prophet+AR(v1)"

    @staticmethod
    def _artifact_path(batch_no: str) -> Path:
        if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,64}", batch_no):
            raise ValueError("train_batch_no 格式不正确")
        return settings.artifact_root / batch_no / "model.joblib"
