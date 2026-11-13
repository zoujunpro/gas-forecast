"""MCP 工具的运行时结果对象，对应 Java 当前提供的七个工具。"""

from __future__ import annotations

from typing import Any, Generic, TypeVar

from pydantic import ConfigDict, Field, field_validator, model_validator

from .operation_models import OperationParameters, RuntimeModel


class ToolResultModel(RuntimeModel):
    """External MCP payloads are additive; unknown fields must not break a run."""

    model_config = ConfigDict(extra="ignore")


class RecordAudit(ToolResultModel):
    id: int | None = None
    created_at: str | None = None
    updated_at: str | None = None
    created_by: str | int | None = None
    created_by_name: str | None = None
    updated_by: str | int | None = None
    updated_by_name: str | None = None


class CustomerRecord(RecordAudit):
    customer_code: str
    customer_name: str
    region_code: str | None = None
    region_name: str | None = None
    industry_code: str | None = None
    industry_name: str | None = None
    raw_region_name: str | None = None
    raw_industry_name: str | None = None


class RegionRecord(RecordAudit):
    region_code: str
    region_name: str
    remark: str | None = None


class IndustryRecord(RecordAudit):
    industry_code: str
    industry_name: str


RecordT = TypeVar("RecordT")


class PageResult(ToolResultModel, Generic[RecordT]):
    records: list[RecordT] = Field(default_factory=list)
    total: int | None = None
    page: int | None = None
    size: int | None = None
    page_no: int | None = None
    page_size: int | None = None
    pages: int | None = None
    fetched_count: int | None = None
    all_fetched: bool | None = None


class TaskDetail(ToolResultModel):
    id: int | None = None
    task_no: str | None = None
    detail_no: str | None = None
    forecast_no: str | None = None
    agent_code: str | None = None
    dimension_type: str | None = None
    region_code: str | None = None
    industry_code: str | None = None
    customer_code: str | None = None
    status: str | None = None
    created_at: str | None = None
    updated_at: str | None = None


class TaskCreatedResult(ToolResultModel):
    task_no: str | None = None
    total: int | None = None
    status: str | None = None
    message: str | None = None
    tasks: list[TaskDetail] = Field(default_factory=list)
    affected_count: int | None = None
    proposed_parameters: OperationParameters | None = None


class TaskCounts(ToolResultModel):
    total: int = 0
    pending: int = 0
    running: int = 0
    success: int = 0
    failed: int = 0


class TaskStatusResult(ToolResultModel):
    task_no: str
    dimension_type: str | None = None
    status: str | None = None
    current_step_code: str | None = None
    error_code: str | None = None
    error_message: str | None = None
    summary: TaskCounts = Field(default_factory=TaskCounts)
    details: list[TaskDetail] = Field(default_factory=list)
    next_action: str | None = None


class TaskStep(ToolResultModel):
    id: int | None = None
    step_no: str | None = None
    task_detail_no: str | None = None
    forecast_no: str | None = None
    attempt_no: int | None = None
    step_code: str | None = None
    step_name: str | None = None
    step_order: int | None = None
    status: str | None = None
    progress_percent: float | None = None
    # 业务库中保存的原始日志快照为文本，并非 Agent 可合并的运行参数。
    input_json: str | None = None
    output_json: str | None = None
    metrics_json: str | None = None
    message: str | None = None
    error_code: str | None = None
    error_message: str | None = None
    started_at: str | None = None
    finished_at: str | None = None
    duration_ms: int | None = None
    created_at: str | None = None
    updated_at: str | None = None


class TaskStepsResult(ToolResultModel):
    task_no: str
    steps: list[TaskStep] = Field(default_factory=list)


class LastYearPoint(ToolResultModel):
    date: str | None = None
    value: float | None = None


class ForecastRecord(RecordAudit):
    forecast_no: str
    task_detail_id: int | None = None
    agent_code: str | None = None
    model_code: str | None = None
    model_version: str | None = None
    customer_code: str | None = None
    customer_name: str | None = None
    region_code: str | None = None
    region_name: str | None = None
    industry_code: str | None = None
    industry_name: str | None = None
    history_start_date: str | None = None
    history_end_date: str | None = None
    history_rows: int | None = None
    forecast_start_date: str | None = None
    forecast_horizon: int | None = None
    forecast_unit: str | None = None
    status: str | None = None
    best_model: str | None = None
    candidate_count: int | None = None
    successful_candidate_count: int | None = None
    mape: float | None = None
    wmape: float | None = None
    smape: float | None = None
    rmse: float | None = None
    mae: float | None = None
    r2: float | None = None
    execute_duration_seconds: float | None = None
    request_param: str | None = None
    training_result_json: str | None = None
    backtest_result_json: str | None = None
    forecast_result_json: str | None = None
    analysis_content: str | None = None
    analysis_questions_json: str | list[str] | None = None
    last_year_points: list[LastYearPoint] | None = None
    error_message: str | None = None
    started_at: str | None = None
    finished_at: str | None = None

    @field_validator("analysis_questions_json", mode="before")
    @classmethod
    def tolerate_invalid_analysis_questions(cls, value: object) -> object:
        """Questions are optional presentation data and must never invalidate a forecast result."""
        return value if value is None or isinstance(value, (str, list)) else None


class ForecastQueryResult(ToolResultModel):
    record: ForecastRecord
    # Java 已将模型平台快照解析为对象，避免长 JSON 被当作文本截断。
    training: dict[str, Any] = Field(default_factory=dict)
    backtest: dict[str, Any] = Field(default_factory=dict)
    forecast: dict[str, Any] = Field(default_factory=dict)


class ToolProblem(ToolResultModel):
    tool_name: str | None = None
    reason: str | None = None
    next_action: str | None = None
    allowed_actions: list[str] = Field(default_factory=list)
    retryable: bool | None = None
    proposed_parameters: OperationParameters | None = None
    field: str | None = None


ToolData = (
    PageResult[CustomerRecord] | PageResult[RegionRecord] | PageResult[IndustryRecord]
    | TaskCreatedResult | TaskStatusResult | TaskStepsResult | ForecastQueryResult | ToolProblem
)


class ToolExecutionResult(ToolResultModel):
    """稳定结果信封；data 是具体工具的明确结果对象。"""

    success: bool | None = None
    status: str | None = None
    result_type: str | None = None
    message: str | None = None
    error_code: str | None = None
    error_type: str | None = None
    retryable: bool | None = None
    data: ToolData | None = None
    raw_data: dict[str, Any] | None = Field(default=None, exclude=True)

    @model_validator(mode="before")
    @classmethod
    def parse_data_by_contract(cls, value: object) -> object:
        """Select the result model once instead of trying every union member."""
        if not isinstance(value, dict):
            return value
        data = value.get("data")
        if not isinstance(data, dict) or isinstance(data, ToolResultModel):
            return value

        result_type = str(value.get("resultType") or value.get("result_type") or "").upper()
        parsed: ToolData | dict[str, Any] = data

        # Forecast details have a stable four-part shape even when older MCP
        # versions label them as OBJECT rather than RESULT.
        if isinstance(data.get("record"), dict) and any(
            key in data for key in ("training", "backtest", "forecast")
        ):
            parsed = ForecastQueryResult.model_validate(data)
        elif isinstance(data.get("steps"), list) and (data.get("taskNo") or data.get("task_no")):
            parsed = TaskStepsResult.model_validate(data)
        elif result_type == "PAGE" or isinstance(data.get("records"), list):
            records = data.get("records") if isinstance(data.get("records"), list) else []
            sample = next((item for item in records if isinstance(item, dict)), {})
            if "customerCode" in sample or "customerName" in sample:
                parsed = PageResult[CustomerRecord].model_validate(data)
            elif "industryCode" in sample or "industryName" in sample:
                parsed = PageResult[IndustryRecord].model_validate(data)
            elif "regionCode" in sample or "regionName" in sample:
                parsed = PageResult[RegionRecord].model_validate(data)
        elif result_type == "TASK":
            parsed = TaskCreatedResult.model_validate(data)
        elif result_type == "PROGRESS":
            parsed = TaskStatusResult.model_validate(data)
        elif value.get("success") is False or any(
            key in data for key in ("reason", "nextAction", "allowedActions", "retryable")
        ):
            parsed = ToolProblem.model_validate(data)

        return {**value, "data": parsed, "raw_data": dict(data)}


class ToolCallTrace(ToolResultModel):
    tool: str
    arguments: OperationParameters = Field(default_factory=OperationParameters)
    result: ToolExecutionResult
    attempts: int | None = None

    @model_validator(mode="before")
    @classmethod
    def parse_arguments(cls, value: object) -> object:
        if isinstance(value, dict):
            return {**value, "arguments": OperationParameters.from_execution(value.get("arguments") or {})}
        return value
