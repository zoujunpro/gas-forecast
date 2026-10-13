"""CNG、化工和工业燃料日级预测模型。

算法迁移自 ``江苏发电/xgbv2_base.py``：Prophet 负责趋势和季节性，
XGBoost 学习 Prophet 残差，并使用最后一段历史数据做留出回测。
三个业务模型共享算法实现，但模型编码、适用行业和模型产物严格隔离。
"""

from __future__ import annotations

from dataclasses import dataclass, field
from hashlib import sha256
from pathlib import Path
import re
from typing import Any

import joblib
import numpy as np
import pandas as pd
from xgboost import XGBRegressor

from gas_model_platform.core.config import settings
from gas_model_platform.schemas.modeling import (
    BacktestPoint,
    BacktestResult,
    CandidateEvaluation,
    ForecastPoint,
    MetricSet,
    ModelContext,
    ModelInfo,
    PredictResult,
    RollingBacktestFoldMetric,
    RollingBacktestPoint,
    TrainingDataRange,
    TrainingDataValidationIssue,
    TrainingDataValidationResult,
    TrainResult,
)


DATE_ALIASES = ("ds", "date", "日期", "时间", "stat_date", "statDate")
TARGET_ALIASES = ("y", "gas_sales", "销量", "用气量")
LAGS = (1, 2, 3, 7)
ROLLS = (3, 7)
PROPHET_REGRESSOR_PRIORITY = (
    "temp", "tempmax", "tempmin", "hdd", "cdd", "humidity", "precip",
    "temp_range", "windgust", "windspeed", "cloudcover", "sealevelpressure",
)


@dataclass(frozen=True)
class SectorModelSpec:
    model_code: str
    model_name: str
    industry_name: str
    description: str
    minimum_history: int = 180
    recommended_history: int = 730


@dataclass
class SectorGasXgbArtifact:
    model_code: str
    prophet_model: Any
    xgb_model: Any
    regressors: list[str]
    xgb_feature_names: list[str]
    history_dates: list[str]
    history_values: list[float]
    selected_params: dict[str, Any]
    residual_lower: float
    residual_upper: float
    train_start: str
    train_end: str
    prophet_regressors: list[str] = field(default_factory=list)
    history_temp_values: list[float] = field(default_factory=list)
    ds_shift_days: int = 0
    model_version: str = "1.0.0"
    artifact_version: str = "1.0"


@dataclass
class SectorTrainingOutput:
    artifact: SectorGasXgbArtifact
    metrics: dict[str, float | None]
    backtest_rows: list[dict[str, Any]]
    candidates: list[dict[str, Any]]
    selected_params: dict[str, Any]


def _prophet_class():
    try:
        from prophet import Prophet
    except ImportError as exc:  # pragma: no cover - 安装依赖时才触发
        raise RuntimeError("行业日级预测模型需要安装 prophet 依赖") from exc
    return Prophet


def _find_column(columns: list[Any], aliases: tuple[str, ...]) -> Any | None:
    normalized = {str(column).strip().lower(): column for column in columns}
    for alias in aliases:
        if alias.lower() in normalized:
            return normalized[alias.lower()]
    return None


def prepare_sector_data(rows: list[dict[str, Any]] | pd.DataFrame) -> pd.DataFrame:
    frame = rows.copy() if isinstance(rows, pd.DataFrame) else pd.DataFrame(rows)
    date_column = _find_column(list(frame.columns), DATE_ALIASES)
    target_column = _find_column(list(frame.columns), TARGET_ALIASES)
    missing = []
    if date_column is None:
        missing.append("date")
    if target_column is None:
        missing.append("gas_sales")
    if missing:
        raise ValueError(f"行业模型训练数据缺少必要字段: {missing}")
    frame = frame.rename(columns={date_column: "ds", target_column: "y"})
    frame["ds"] = pd.to_datetime(frame["ds"], errors="coerce")
    frame["y"] = pd.to_numeric(frame["y"], errors="coerce")
    frame = frame.dropna(subset=["ds", "y"]).sort_values("ds").reset_index(drop=True)

    empty: list[str] = []
    for column in [item for item in frame.columns if item not in {"ds", "y"}]:
        frame[column] = pd.to_numeric(frame[column], errors="coerce")
        if not frame[column].notna().any():
            empty.append(column)
        else:
            frame[column] = frame[column].interpolate(limit_direction="both").ffill().bfill()
    if empty:
        frame = frame.drop(columns=empty)
    return _add_degree_day_features(frame)


def _add_degree_day_features(frame: pd.DataFrame) -> pd.DataFrame:
    result = frame.copy()
    if "temp" in result:
        result["hdd"] = np.maximum(18.0 - result["temp"], 0.0)
        result["cdd"] = np.maximum(result["temp"] - 26.0, 0.0)
    if "tempmax" in result and "tempmin" in result:
        result["temp_range"] = result["tempmax"] - result["tempmin"]
    return result


def _metrics(actual: np.ndarray, predicted: np.ndarray) -> dict[str, float | None]:
    mask = np.isfinite(actual) & np.isfinite(predicted)
    if not mask.any():
        return {"rmse": None, "mae": None, "mape": None, "wmape": None, "r2": None}
    actual, predicted = actual[mask], predicted[mask]
    error = predicted - actual
    nonzero = actual != 0
    denominator = float(np.abs(actual).sum())
    total = float(np.square(actual - actual.mean()).sum())
    return {
        "rmse": float(np.sqrt(np.mean(np.square(error)))),
        "mae": float(np.mean(np.abs(error))),
        "mape": float(np.mean(np.abs(error[nonzero] / actual[nonzero])) * 100) if nonzero.any() else None,
        "wmape": float(np.abs(error).sum() / denominator * 100) if denominator else None,
        "r2": float(1.0 - np.square(error).sum() / total) if total else None,
    }


def _calendar_features(dates: pd.Series, start: pd.Timestamp) -> pd.DataFrame:
    ds = pd.to_datetime(dates)
    output = pd.DataFrame(index=dates.index)
    output["dow"] = ds.dt.dayofweek.astype(float)
    output["month"] = ds.dt.month.astype(float)
    output["day_of_year"] = ds.dt.dayofyear.astype(float)
    output["t"] = (ds - start).dt.days.astype(float)
    output["weekly_sin"] = np.sin(2 * np.pi * output["dow"] / 7.0)
    output["weekly_cos"] = np.cos(2 * np.pi * output["dow"] / 7.0)
    output["yearly_sin"] = np.sin(2 * np.pi * output["day_of_year"] / 365.25)
    output["yearly_cos"] = np.cos(2 * np.pi * output["day_of_year"] / 365.25)
    return output


def _prophet_frame(frame: pd.DataFrame, regressors: list[str]) -> pd.DataFrame:
    return frame[["ds", "y", *regressors]].copy()


def _fit_prophet(frame: pd.DataFrame, regressors: list[str], cps: float):
    Prophet = _prophet_class()
    model = Prophet(
        seasonality_mode="additive",
        changepoint_prior_scale=cps,
        seasonality_prior_scale=10.0,
        holidays_prior_scale=20.0,
        daily_seasonality=False,
        weekly_seasonality=True,
        yearly_seasonality=True,
    )
    for name in regressors:
        model.add_regressor(name)
    model.fit(_prophet_frame(frame, regressors))
    return model


def _select_prophet_regressors(regressors: list[str]) -> list[str]:
    selected = [name for name in PROPHET_REGRESSOR_PRIORITY if name in regressors]
    return selected or regressors


def _prophet_components(model: Any, frame: pd.DataFrame, regressors: list[str]) -> pd.DataFrame:
    forecast = model.predict(frame[["ds", *regressors]])
    result = pd.DataFrame(index=frame.index)
    for name in ("yhat", "trend", "weekly", "yearly", "additive_terms", "multiplicative_terms"):
        result[f"prophet_{name}"] = pd.to_numeric(forecast.get(name, 0.0), errors="coerce").fillna(0.0).to_numpy()
    return result


def _xgb_training_features(
    frame: pd.DataFrame,
    prophet: Any,
    regressors: list[str],
    prophet_regressors: list[str],
) -> tuple[pd.DataFrame, pd.Series]:
    features = pd.concat(
        [
            _prophet_components(prophet, frame, prophet_regressors),
            _calendar_features(frame["ds"], frame["ds"].min()),
            frame[regressors].reset_index(drop=True),
        ],
        axis=1,
    )
    for lag in LAGS:
        features[f"y_lag{lag}"] = frame["y"].shift(lag)
    shifted = frame["y"].shift(1)
    for window in ROLLS:
        features[f"y_roll{window}"] = shifted.rolling(window).mean()
    features["y_diff1"] = features["y_lag1"] - features["y_lag2"]
    features["y_diff7"] = features["y_lag1"] - features["y_lag7"]
    if "temp" in frame:
        features["temp_lag1"] = frame["temp"].shift(1)
        features["temp_diff1"] = frame["temp"] - features["temp_lag1"]
    target = frame["y"] - features["prophet_yhat"]
    valid = features.notna().all(axis=1) & target.notna()
    return features.loc[valid].reset_index(drop=True), target.loc[valid].reset_index(drop=True)


def _fit_models(frame: pd.DataFrame, params: dict[str, Any]):
    regressors = [column for column in frame.columns if column not in {"ds", "y"}]
    prophet_regressors = _select_prophet_regressors(regressors)
    prophet = _fit_prophet(frame, prophet_regressors, float(params["cps"]))
    features, target = _xgb_training_features(
        frame, prophet, regressors, prophet_regressors
    )
    if len(features) < 30:
        raise ValueError("行业模型可用于XGBoost残差训练的数据不足30条")
    xgb = XGBRegressor(
        objective="reg:squarederror",
        n_estimators=int(params["n_estimators"]),
        max_depth=int(params["max_depth"]),
        learning_rate=float(params["learning_rate"]),
        subsample=0.9,
        colsample_bytree=0.9,
        reg_lambda=1.0,
        random_state=42,
        n_jobs=1,
    )
    xgb.fit(features, target)
    residual = target.to_numpy() - xgb.predict(features)
    return (
        prophet, xgb, regressors, prophet_regressors,
        list(features.columns), residual,
    )


def _future_frame(
    future: pd.DataFrame, regressors: list[str], history: pd.DataFrame
) -> pd.DataFrame:
    result = future.copy()
    result["ds"] = pd.to_datetime(result["ds"], errors="coerce")
    missing = [name for name in regressors if name not in result]
    if missing:
        raise ValueError(f"行业模型预测数据缺少未来特征: {missing}")
    for name in regressors:
        result[name] = pd.to_numeric(result[name], errors="coerce")
        if result[name].isna().any():
            raise ValueError(f"行业模型预测特征存在空值或非数值: {name}")
    if result["ds"].isna().any() or result["ds"].duplicated().any():
        raise ValueError("行业模型预测日期无效或重复")
    if not result["ds"].is_monotonic_increasing:
        result = result.sort_values("ds").reset_index(drop=True)
    return result[["ds", *regressors]]


def _recursive_predict(
    prophet: Any,
    xgb: Any,
    feature_names: list[str],
    regressors: list[str],
    prophet_regressors: list[str],
    history: pd.DataFrame,
    future: pd.DataFrame,
) -> pd.DataFrame:
    future = _future_frame(future, regressors, history)
    components = _prophet_components(prophet, future, prophet_regressors)
    calendar = _calendar_features(future["ds"], history["ds"].min()).reset_index(drop=True)
    values = history["y"].astype(float).tolist()
    temp_values = (
        history["temp"].astype(float).tolist() if "temp" in history else []
    )
    rows: list[dict[str, Any]] = []
    for index, row in future.reset_index(drop=True).iterrows():
        record: dict[str, float] = {}
        record.update({name: float(components.iloc[index][name]) for name in components})
        record.update({name: float(calendar.iloc[index][name]) for name in calendar})
        record.update({name: float(row[name]) for name in regressors})
        for lag in LAGS:
            record[f"y_lag{lag}"] = float(values[-lag])
        for window in ROLLS:
            record[f"y_roll{window}"] = float(np.mean(values[-window:]))
        record["y_diff1"] = record["y_lag1"] - record["y_lag2"]
        record["y_diff7"] = record["y_lag1"] - record["y_lag7"]
        if "temp_lag1" in feature_names:
            current_temp = float(row["temp"])
            previous_temp = temp_values[-1] if temp_values else current_temp
            record["temp_lag1"] = previous_temp
            record["temp_diff1"] = current_temp - previous_temp
        matrix = pd.DataFrame([[record[name] for name in feature_names]], columns=feature_names)
        prediction = float(record["prophet_yhat"] + xgb.predict(matrix)[0])
        values.append(prediction)
        if "temp" in row:
            temp_values.append(float(row["temp"]))
        rows.append({"date": row["ds"], "prediction": prediction})
    return pd.DataFrame(rows)


def train_sector_model(frame: pd.DataFrame, model_code: str) -> SectorTrainingOutput:
    holdout_days = min(120, max(30, len(frame) // 5))
    train = frame.iloc[:-holdout_days].reset_index(drop=True)
    holdout = frame.iloc[-holdout_days:].reset_index(drop=True)
    candidates = [
        {"ds_shift_days": shift, "cps": 0.10, "n_estimators": 900,
         "max_depth": depth, "learning_rate": 0.05}
        for shift in (0, 1) for depth in (3, 4)
    ]
    evaluations: list[dict[str, Any]] = []
    for params in candidates:
        shift = int(params["ds_shift_days"])
        shifted_train = train.copy()
        shifted_holdout = holdout.copy()
        shifted_train["ds"] -= pd.Timedelta(days=shift)
        shifted_holdout["ds"] -= pd.Timedelta(days=shift)
        prophet, xgb, regressors, prophet_regressors, feature_names, _ = _fit_models(
            shifted_train, params
        )
        predicted = _recursive_predict(
            prophet, xgb, feature_names, regressors, prophet_regressors,
            shifted_train, shifted_holdout,
        )
        predicted["date"] += pd.Timedelta(days=shift)
        metric = _metrics(holdout["y"].to_numpy(float), predicted["prediction"].to_numpy(float))
        evaluations.append({"params": params, "metrics": metric, "predicted": predicted})
    ranked = sorted(evaluations, key=lambda item: item["metrics"]["rmse"] or float("inf"))
    winner = ranked[0]
    shift = int(winner["params"]["ds_shift_days"])
    shifted_frame = frame.copy()
    shifted_frame["ds"] -= pd.Timedelta(days=shift)
    prophet, xgb, regressors, prophet_regressors, feature_names, residual = _fit_models(
        shifted_frame, winner["params"]
    )
    artifact = SectorGasXgbArtifact(
        model_code=model_code,
        prophet_model=prophet,
        xgb_model=xgb,
        regressors=regressors,
        xgb_feature_names=feature_names,
        history_dates=[str(value.date()) for value in shifted_frame["ds"]],
        history_values=shifted_frame["y"].astype(float).tolist(),
        selected_params=winner["params"],
        residual_lower=float(np.quantile(residual, 0.1)),
        residual_upper=float(np.quantile(residual, 0.9)),
        train_start=str(frame["ds"].min().date()),
        train_end=str(frame["ds"].max().date()),
        prophet_regressors=prophet_regressors,
        history_temp_values=(
            shifted_frame["temp"].astype(float).tolist()
            if "temp" in shifted_frame else []
        ),
        ds_shift_days=shift,
    )
    rows = [
        {
            "date": row.ds,
            "actual": float(row.y),
            "prediction": float(prediction),
        }
        for row, prediction in zip(
            holdout.itertuples(), winner["predicted"]["prediction"], strict=True
        )
    ]
    return SectorTrainingOutput(artifact, winner["metrics"], rows, ranked, winner["params"])


class SectorGasXgbV1Handler:
    spec: SectorModelSpec

    def __init__(self, spec: SectorModelSpec):
        self.spec = spec
        self.info = ModelInfo(
            agent_code="short-term",
            model_code=spec.model_code,
            model_version="1.0.0",
            model_name=spec.model_name,
            description=spec.description,
            capabilities=["train", "backtest", "predict"],
            training_data_range=TrainingDataRange(
                type="history_length",
                frequency="day",
                minimum=spec.minimum_history,
                recommended=spec.recommended_history,
                continuous=True,
                description=(
                    f"{spec.industry_name}模型至少需要{spec.minimum_history}天连续日数据，"
                    f"建议提供{spec.recommended_history}天以上。"
                ),
            ),
        )

    def validate_training_data(self, context: ModelContext) -> TrainingDataValidationResult:
        frame = prepare_sector_data(context.dataset)
        dates = pd.DatetimeIndex(frame["ds"])
        duplicate_count = int(dates.duplicated().sum())
        missing = pd.date_range(dates.min(), dates.max(), freq="D").difference(dates)
        errors: list[TrainingDataValidationIssue] = []
        if len(frame) < self.spec.minimum_history:
            errors.append(TrainingDataValidationIssue(
                code="INSUFFICIENT_HISTORY",
                message=f"{self.spec.industry_name}模型至少需要{self.spec.minimum_history}天数据",
                expected=self.spec.minimum_history,
                actual=len(frame),
            ))
        if duplicate_count:
            errors.append(TrainingDataValidationIssue(
                code="DUPLICATE_DATES", message="训练数据存在重复日期",
                expected=0, actual=duplicate_count,
            ))
        if len(missing):
            errors.append(TrainingDataValidationIssue(
                code="NON_CONTINUOUS_DATES", message=f"训练数据缺少{len(missing)}个日期",
                expected=0, actual=len(missing),
            ))
        return TrainingDataValidationResult(
            agent_code=self.info.agent_code, model_code=self.info.model_code,
            valid=not errors,
            summary={"row_count": len(frame), "start_date": str(dates.min().date()),
                     "end_date": str(dates.max().date()), "missing_date_count": len(missing),
                     "duplicate_date_count": duplicate_count},
            errors=errors,
        )

    def train(self, context: ModelContext) -> TrainResult:
        if not context.train_batch_no:
            raise ValueError(f"{self.spec.industry_name}模型训练需要 train_batch_no")
        path = self._artifact_path(context.train_batch_no)
        if path.exists():
            raise ValueError(f"训练批次已存在，禁止覆盖: {context.train_batch_no}")
        output = train_sector_model(prepare_sector_data(context.dataset), self.info.model_code)
        path.parent.mkdir(parents=True, exist_ok=True)
        joblib.dump(output.artifact, path)
        rolling = self._rolling_points(output.backtest_rows)
        return TrainResult(
            agent_code=self.info.agent_code, model_code=self.info.model_code,
            train_batch_no=context.train_batch_no,
            metrics=MetricSet(**output.metrics),
            feature_names=output.artifact.xgb_feature_names,
            selected_model_name="Prophet+XGBoost",
            selection_reason="按最后120天留出回测RMSE选择Prophet+XGBoost参数。",
            requested_candidate_count=len(output.candidates),
            successful_candidate_count=len(output.candidates),
            ranked_candidate_count=len(output.candidates),
            selected_model_params=output.selected_params,
            candidate_evaluations=[
                CandidateEvaluation(
                    rank=index, model_name=f"Prophet+XGBoost-C{index}",
                    model_type="hybrid", selected=index == 1,
                    metrics=MetricSet(**item["metrics"]),
                )
                for index, item in enumerate(output.candidates, start=1)
            ],
            rolling_backtest_results=rolling,
            rolling_backtest_fold_metrics=[RollingBacktestFoldMetric(
                model_name="Prophet+XGBoost", season="holdout", stage="evaluation",
                metrics=MetricSet(**output.metrics),
            )],
            metadata={"industry_name": self.spec.industry_name,
                      "algorithm_source": "江苏发电/xgbv2_base.py",
                      "artifact_sha256": sha256(path.read_bytes()).hexdigest(),
                      "model_version": output.artifact.model_version,
                      "artifact_version": output.artifact.artifact_version,
                      "train_start": output.artifact.train_start,
                      "train_end": output.artifact.train_end,
                      "backtest_point_count": len(rolling)},
        )

    def backtest(self, context: ModelContext) -> BacktestResult:
        output = train_sector_model(prepare_sector_data(context.dataset), self.info.model_code)
        return BacktestResult(
            agent_code=self.info.agent_code, model_code=self.info.model_code,
            metrics=MetricSet(**output.metrics),
            points=[BacktestPoint(
                stat_date=pd.Timestamp(row["date"]).date(),
                actual_value=float(row["actual"]), predicted_value=float(row["prediction"]),
            ) for row in output.backtest_rows],
            metadata={"algorithm": "Prophet+XGBoost", "selected_model_params": output.selected_params},
        )

    def predict(self, context: ModelContext) -> PredictResult:
        if context.forecast_unit != "day":
            raise ValueError(f"{self.info.model_code} 的 forecast_unit 必须是 day")
        if not context.train_batch_no or not context.forecast_batch_no:
            raise ValueError("预测需要 train_batch_no 和 forecast_batch_no")
        if len(context.dataset) < context.forecast_horizon:
            raise ValueError("预测日期条数不能少于 forecast_horizon")
        artifact = self._load_artifact(context.train_batch_no)
        future = prepare_sector_data_for_prediction(context.dataset)
        future["ds"] -= pd.Timedelta(days=artifact.ds_shift_days)
        history = pd.DataFrame({
            "ds": pd.to_datetime(artifact.history_dates),
            "y": artifact.history_values,
        })
        if artifact.history_temp_values:
            history["temp"] = artifact.history_temp_values
        forecast = _recursive_predict(
            artifact.prophet_model, artifact.xgb_model, artifact.xgb_feature_names,
            artifact.regressors, artifact.prophet_regressors,
            history, future.iloc[:context.forecast_horizon],
        )
        forecast["date"] += pd.Timedelta(days=artifact.ds_shift_days)
        return PredictResult(
            agent_code=self.info.agent_code, model_code=self.info.model_code,
            forecast_batch_no=context.forecast_batch_no,
            points=[ForecastPoint(
                forecast_date=pd.Timestamp(row.date).date(), prediction=float(row.prediction),
                lower_value=float(row.prediction + artifact.residual_lower),
                upper_value=float(row.prediction + artifact.residual_upper),
                extra={"algorithm": "Prophet+XGBoost"},
            ) for row in forecast.itertuples()],
            metadata={"train_batch_no": context.train_batch_no,
                      "model_version": artifact.model_version,
                      "artifact_version": artifact.artifact_version},
        )

    def _artifact_path(self, batch_no: str) -> Path:
        value = str(batch_no).strip()
        if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,64}", value):
            raise ValueError("train_batch_no 格式不正确")
        return settings.artifact_root / value / "model.joblib"

    def _load_artifact(self, batch_no: str) -> SectorGasXgbArtifact:
        path = self._artifact_path(batch_no)
        if not path.exists():
            raise FileNotFoundError(f"训练批次对应的模型产物不存在: {path}")
        artifact = joblib.load(path)
        if not isinstance(artifact, SectorGasXgbArtifact):
            raise TypeError("文件不是行业Prophet+XGBoost模型产物")
        if artifact.model_code != self.info.model_code:
            raise ValueError("训练批次模型类型与当前模型不一致")
        if artifact.artifact_version != "1.0":
            raise ValueError(f"不支持的模型产物版本: {artifact.artifact_version}")
        return artifact

    @staticmethod
    def _rolling_points(rows: list[dict[str, Any]]) -> list[RollingBacktestPoint]:
        result: list[RollingBacktestPoint] = []
        for row in rows:
            actual, predicted = float(row["actual"]), float(row["prediction"])
            error = predicted - actual
            result.append(RollingBacktestPoint(
                model_name="Prophet+XGBoost", season="holdout",
                stat_date=pd.Timestamp(row["date"]).date(), actual_value=actual,
                predicted_value=predicted, error_value=error, absolute_error=abs(error),
                error_rate=abs(error) / abs(actual) * 100 if actual else None,
            ))
        return result


def prepare_sector_data_for_prediction(rows: list[dict[str, Any]]) -> pd.DataFrame:
    frame = pd.DataFrame(rows)
    date_column = _find_column(list(frame.columns), DATE_ALIASES)
    if date_column is None:
        raise ValueError("行业模型预测数据缺少 date")
    frame = frame.rename(columns={date_column: "ds"})
    frame["ds"] = pd.to_datetime(frame["ds"], errors="coerce")
    return _add_degree_day_features(frame)


CNG_SPEC = SectorModelSpec(
    model_code="MODEL_CNG_V1.0", model_name="CNG预测模型 V1", industry_name="CNG",
    description="支持CNG行业；迁移江苏CNG Prophet+XGBoost模型，支持天气及能源价格特征。",
)
CHEMICAL_SPEC = SectorModelSpec(
    model_code="MODEL_CHEMICAL_GAS_V1.0", model_name="化工用气预测模型 V1", industry_name="化工",
    description="支持天然气化工行业；迁移江苏化工 Prophet+XGBoost模型。",
)
INDUSTRIAL_FUEL_SPEC = SectorModelSpec(
    model_code="MODEL_INDUSTRIAL_FUEL_V1.0", model_name="工业燃料预测模型 V1", industry_name="工业燃料",
    description="支持工业燃料行业；迁移江苏工业燃料 Prophet+XGBoost模型，支持天气及能源价格特征。",
)


class ModelCngV1Handler(SectorGasXgbV1Handler):
    def __init__(self):
        super().__init__(CNG_SPEC)


class ModelChemicalGasV1Handler(SectorGasXgbV1Handler):
    def __init__(self):
        super().__init__(CHEMICAL_SPEC)


class ModelIndustrialFuelV1Handler(SectorGasXgbV1Handler):
    def __init__(self):
        super().__init__(INDUSTRIAL_FUEL_SPEC)
