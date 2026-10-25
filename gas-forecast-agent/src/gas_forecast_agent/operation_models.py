"""Agent 内部可调试的业务参数。字典仅用于模型/MCP 的序列化边界。"""

from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field


def camel_case(name: str) -> str:
    head, *tail = name.split("_")
    return head + "".join(part.capitalize() for part in tail)


class RuntimeModel(BaseModel):
    model_config = ConfigDict(
        alias_generator=camel_case, populate_by_name=True, serialize_by_alias=True, extra="forbid",
    )

    def to_payload(self) -> dict[str, object]:
        """仅在调用模型、MCP 或 Java 接口时序列化。保留显式 false/0。"""
        return self.model_dump(mode="json", by_alias=True, exclude_none=True, exclude_unset=True)


class EntityParameters(RuntimeModel):
    """主数据校验后的实体编码及名称；单值和集合对应现有 MCP 查询/批量参数。"""

    customer_code: str | None = None
    customer_name: str | None = None
    customer_codes: list[str] = Field(default_factory=list)
    customer_names: list[str] = Field(default_factory=list)
    region_code: str | None = None
    region_name: str | None = None
    region_codes: list[str] = Field(default_factory=list)
    region_names: list[str] = Field(default_factory=list)
    industry_code: str | None = None
    industry_name: str | None = None
    industry_codes: list[str] = Field(default_factory=list)
    industry_names: list[str] = Field(default_factory=list)

    def has_codes(self, entity: str) -> bool:
        return bool({
            "customer": self.customer_code or self.customer_codes,
            "region": self.region_code or self.region_codes,
            "industry": self.industry_code or self.industry_codes,
        }.get(entity))


class ExecutionTarget(RuntimeModel):
    """组内取交集、组间独立执行的预测目标。"""

    region_codes: list[str] = Field(default_factory=list)
    industry_codes: list[str] = Field(default_factory=list)
    customer_codes: list[str] = Field(default_factory=list)


class SemanticParameters(RuntimeModel):
    """用户本轮明确表达的时间、周期、查询条件；不接收模型编造的实体编码。"""

    forecast_horizon: int | None = None
    forecast_start_date: str | None = None
    forecast_end_date: str | None = None
    forecast_unit: str | None = None
    forecast_frequency: str | None = None
    history_start_date: str | None = None
    history_end_date: str | None = None
    training_policy: str | None = None
    task_no: str | None = None
    forecast_no: str | None = None
    keyword: str | None = None
    page_no: int | None = None
    page_size: int | None = None
    fetch_all: bool | None = None
    include_points: bool | None = None
    include_recovery: bool | None = None
    include_business_steps: bool | None = None
    include_attempts: bool | None = None


class ReferenceMentions(RuntimeModel):
    """尚未解析的自然语言指代，只表示引用目标，不自动继承任何业务参数。"""

    customer: str | None = None
    customers: str | None = None
    region: str | None = None
    industry: str | None = None
    task: str | None = None
    forecast: str | None = None
    training_result: str | None = None

    @property
    def is_explicit(self) -> bool:
        return any(getattr(self, name) for name in type(self).model_fields)


class OperationParameters(EntityParameters, SemanticParameters):
    """当前操作唯一的参数快照。未提供字段保持为空，不从上次任务补默认值。"""

    conversation_no: str | None = None
    agent_code: str | None = None
    dimension_type: str | None = None
    targets: list[ExecutionTarget] = Field(default_factory=list)
    confirmed: bool | None = None
    idempotency_key: str | None = None
    force_retrain: bool | None = None
    auto_train: bool | None = None
    training_preference: str | None = None
    bulk: bool | None = None
    locked_parameters: list[str] = Field(default_factory=list, alias="_lockedParameters")
    validated_entity_parameters: list[str] = Field(default_factory=list, alias="_validatedEntityParameters")
    entity_batch_resolved: bool | None = Field(default=None, alias="_entityBatchResolved")

    def to_payload(self) -> dict[str, object]:
        # Java JSON 往返后字段可能被显式补成空数组。它们不是已确认的条件，
        # 不能让 targets=[] 遮住已校验的 customerCodes；false 和 0 仍保留。
        return {key: value for key, value in super().to_payload().items() if value not in ("", [])}

    @classmethod
    def from_execution(cls, value: object) -> OperationParameters:
        """执行器临时控制字段不进入 State/记忆；业务字段必须有明确模型定义。"""
        if isinstance(value, cls):
            return value
        if not isinstance(value, dict):
            raise TypeError("操作参数必须是 OperationParameters 或接口对象")
        resumable = {"_lockedParameters", "_validatedEntityParameters", "_entityBatchResolved"}
        return cls.model_validate({
            key: item for key, item in value.items()
            if not key.startswith("_") or key in resumable
        })

    def pending_snapshot(self) -> OperationParameters:
        return OperationParameters.model_validate({
            key: value for key, value in self.to_payload().items() if key != "confirmed"
        })
