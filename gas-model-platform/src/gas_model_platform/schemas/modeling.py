from datetime import date, datetime
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field

AgentCode = Literal["winter-supply", "monthly-sales", "short-term"]
ForecastUnit = Literal["day", "tenday", "month"]


class TrainDatasetRow(BaseModel):
    """统一训练数据行；模型专属特征作为同级扩展字段保留。"""

    model_config = ConfigDict(extra="allow")

    date: date
    gas_sales: float


class PredictDatasetRow(BaseModel):
    """统一预测数据行；模型专属未来特征作为同级扩展字段保留。"""

    model_config = ConfigDict(extra="allow")

    date: date


class ModelInfo(BaseModel):
    agent_code: AgentCode
    model_code: str
    model_version: str = "1.0.0"
    model_name: str
    description: str
    capabilities: list[Literal["train", "backtest", "predict"]]


class ModelContext(BaseModel):
    agent_code: AgentCode | None = None
    model_code: str = Field(min_length=1)
    train_batch_no: str | None = None
    forecast_batch_no: str | None = None
    region_code: str | None = None
    region_name: str | None = None
    industry_code: str | None = None
    industry_name: str | None = None
    customer_code: str | None = None
    customer_name: str | None = None
    forecast_horizon: int = Field(default=12, ge=1, le=366)
    forecast_unit: ForecastUnit | None = None
    params: dict[str, Any] = Field(default_factory=dict)
    dataset: list[dict[str, Any]] = Field(default_factory=list)

class TrainRequest(ModelContext):
    train_batch_no: str
    dataset: list[TrainDatasetRow] = Field(min_length=1)


class BacktestRequest(ModelContext):
    pass


class PredictRequest(ModelContext):
    train_batch_no: str
    forecast_batch_no: str = Field(min_length=1, max_length=65)
    forecast_horizon: int = Field(ge=1, le=366)
    forecast_unit: ForecastUnit
    dataset: list[PredictDatasetRow] = Field(min_length=1, max_length=366)


class FeatureComputeRequest(BaseModel):
    """单日期特征计算请求。

    用于只计算某一个目标日期的模型特征，例如计算 2026-11-01 的冬供特征。
    """

    agent_code: AgentCode
    target_date: date
    region_code: str | None = None
    region_name: str | None = None
    industry_code: str | None = None
    industry_name: str | None = None
    customer_code: str | None = None
    customer_name: str | None = None
    input_row: dict[str, Any] = Field(default_factory=dict)
    history_dataset: list[dict[str, Any]] = Field(default_factory=list)
    params: dict[str, Any] = Field(default_factory=dict)


class FeatureBatchComputeRequest(BaseModel):
    """多日期批量特征计算请求。

    用于一次性计算多个目标日期的模型特征，例如未来 15 个旬或未来 30 天。
    """

    agent_code: AgentCode
    target_dates: list[date] = Field(min_length=1, max_length=366)
    region_code: str | None = None
    region_name: str | None = None
    industry_code: str | None = None
    industry_name: str | None = None
    customer_code: str | None = None
    customer_name: str | None = None
    input_rows: list[dict[str, Any]] = Field(default_factory=list)
    history_dataset: list[dict[str, Any]] = Field(default_factory=list)
    params: dict[str, Any] = Field(default_factory=dict)


class MetricSet(BaseModel):
    mape: float | None = None
    wmape: float | None = None
    smape: float | None = None
    rmse: float | None = None
    mae: float | None = None
    r2: float | None = None


class CandidateEvaluation(BaseModel):
    rank: int
    model_name: str
    model_type: str
    selected: bool = False
    metrics: MetricSet = Field(default_factory=MetricSet)
    constituents: list[str] = Field(default_factory=list)


class ModelIssue(BaseModel):
    severity: Literal["warning", "error"]
    stage: str
    model_name: str | None = None
    season: str | None = None
    category: str
    message: str


class RollingBacktestPoint(BaseModel):
    model_name: str
    season: str
    stat_date: date
    actual_value: float
    predicted_value: float
    error_value: float
    absolute_error: float
    error_rate: float | None = None


class RollingBacktestFoldMetric(BaseModel):
    model_name: str
    season: str
    stage: str
    metrics: MetricSet = Field(default_factory=MetricSet)


class TrainResult(BaseModel):
    agent_code: AgentCode
    model_code: str
    train_batch_no: str
    metrics: MetricSet = Field(default_factory=MetricSet)
    feature_names: list[str] = Field(default_factory=list)
    selected_model_name: str | None = None
    selection_reason: str | None = None
    requested_candidate_count: int = 0
    successful_candidate_count: int = 0
    ranked_candidate_count: int = 0
    selected_model_params: dict[str, Any] = Field(default_factory=dict)
    candidate_evaluations: list[CandidateEvaluation] = Field(default_factory=list)
    rolling_backtest_results: list[RollingBacktestPoint] = Field(default_factory=list)
    rolling_backtest_fold_metrics: list[RollingBacktestFoldMetric] = Field(default_factory=list)
    issues: list[ModelIssue] = Field(default_factory=list)
    metadata: dict[str, Any] = Field(default_factory=dict)
    created_at: datetime = Field(default_factory=datetime.now)


class BacktestPoint(BaseModel):
    stat_date: date
    actual_value: float | None = None
    predicted_value: float
    lower_value: float | None = None
    upper_value: float | None = None


class BacktestResult(BaseModel):
    agent_code: AgentCode
    model_code: str
    metrics: MetricSet = Field(default_factory=MetricSet)
    points: list[BacktestPoint] = Field(default_factory=list)
    issues: list[ModelIssue] = Field(default_factory=list)
    metadata: dict[str, Any] = Field(default_factory=dict)


class ForecastPoint(BaseModel):
    forecast_date: date
    prediction: float
    lower_value: float | None = None
    upper_value: float | None = None
    extra: dict[str, Any] = Field(default_factory=dict)


class PredictResult(BaseModel):
    agent_code: AgentCode
    model_code: str
    forecast_batch_no: str
    points: list[ForecastPoint]
    metadata: dict[str, Any] = Field(default_factory=dict)
    created_at: datetime = Field(default_factory=datetime.now)


class FeatureComputeResult(BaseModel):
    agent_code: AgentCode
    target_date: date
    features: dict[str, Any]
    feature_names: list[str]
    metadata: dict[str, Any] = Field(default_factory=dict)


class FeatureBatchComputeResult(BaseModel):
    agent_code: AgentCode
    results: list[FeatureComputeResult]
    metadata: dict[str, Any] = Field(default_factory=dict)
