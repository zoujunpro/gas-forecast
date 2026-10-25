"""MCP 工具的运行时结果对象，对应 Java 当前提供的七个工具。"""

from __future__ import annotations

from typing import Any, Generic, TypeVar

from pydantic import Field, model_validator

from .operation_models import OperationParameters, RuntimeModel


class RecordAudit(RuntimeModel):
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


class PageResult(RuntimeModel, Generic[RecordT]):
    records: list[RecordT] = Field(default_factory=list)
    total: int | None = None
    page: int | None = None
    size: int | None = None
    page_no: int | None = None
    page_size: int | None = None
    pages: int | None = None
    fetched_count: int | None = None
    all_fetched: bool | None = None


class TaskDetail(RuntimeModel):
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


class TaskCreatedResult(RuntimeModel):
    task_no: str | None = None
    total: int | None = None
    status: str | None = None
    message: str | None = None
    tasks: list[TaskDetail] = Field(default_factory=list)
    affected_count: int | None = None
    proposed_parameters: OperationParameters | None = None


class TaskCounts(RuntimeModel):
    total: int = 0
    pending: int = 0
    running: int = 0
    success: int = 0
    failed: int = 0


class TaskStatusResult(RuntimeModel):
    task_no: str
    dimension_type: str | None = None
    status: str | None = None
    current_step_code: str | None = None
    error_code: str | None = None
    error_message: str | None = None
    summary: TaskCounts = Field(default_factory=TaskCounts)
    details: list[TaskDetail] = Field(default_factory=list)
    next_action: str | None = None


class TaskStep(RuntimeModel):
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


class TaskStepsResult(RuntimeModel):
    task_no: str
    steps: list[TaskStep] = Field(default_factory=list)


class LastYearPoint(RuntimeModel):
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
    last_year_points: list[LastYearPoint] | None = None
    error_message: str | None = None
    started_at: str | None = None
    finished_at: str | None = None


class ForecastQueryResult(RuntimeModel):
    record: ForecastRecord
    # Java 已将模型平台快照解析为对象，避免长 JSON 被当作文本截断。
    training: dict[str, Any] = Field(default_factory=dict)
    backtest: dict[str, Any] = Field(default_factory=dict)
    forecast: dict[str, Any] = Field(default_factory=dict)


class ToolProblem(RuntimeModel):
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


class ToolExecutionResult(RuntimeModel):
    """稳定结果信封；data 是具体工具的明确结果对象。"""

    success: bool | None = None
    status: str | None = None
    result_type: str | None = None
    message: str | None = None
    error_code: str | None = None
    error_type: str | None = None
    retryable: bool | None = None
    data: ToolData | None = None


class ToolCallTrace(RuntimeModel):
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
