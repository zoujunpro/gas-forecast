"""智能体会话及后台业务执行接口的输入输出对象。"""

from datetime import datetime
from enum import StrEnum
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, model_validator


def _to_camel(name: str) -> str:
    """将 Python 蛇形字段名转换成接口使用的小驼峰字段名。"""
    head, *tail = name.split("_")
    return head + "".join(part.capitalize() for part in tail)


# ==================== 基础枚举 ====================


class AgentType(StrEnum):
    """智能体编码。"""

    GENERAL = "general"  # 通用智能助手，由 Agent 根据用户消息选择具体 Skill
    SHORT_TERM = "short-term"  # 短期客户预测智能体
    MONTHLY_SALES = "monthly-sales"  # 月度销量预测智能体
    WINTER_SUPPLY = "winter-supply"  # 冬季保供预测智能体


class AgentStatus(StrEnum):
    """Agent 本轮执行状态，不代表训练或预测任务的执行状态。"""

    PENDING = "PENDING"  # 等待执行
    RUNNING = "RUNNING"  # 正在执行
    WAITING = "WAITING"  # 等待用户补充或确认信息
    SUCCESS = "SUCCESS"  # Agent 已成功完成本轮处理或任务提交
    FAILED = "FAILED"  # Agent 本轮处理失败
    CANCELLED = "CANCELLED"  # Agent 本轮执行已取消


class UserInputType(StrEnum):
    """当前用户输入相对于会话待处理状态的来源类型。"""

    TEXT = "TEXT"  # 普通自然语言；由语义层识别新请求
    CLARIFICATION_REPLY = "CLARIFICATION_REPLY"  # 回答一个结构化澄清问题
    CONFIRMATION_REPLY = "CONFIRMATION_REPLY"  # 确认已经完成解析的待执行计划
    REJECTION_REPLY = "REJECTION_REPLY"  # 拒绝或取消待执行计划


class InteractionType(StrEnum):
    """系统当前等待用户完成的交互类型。"""

    CLARIFICATION = "CLARIFICATION"
    CONFIRMATION = "CONFIRMATION"


class ClarificationReason(StrEnum):
    """由解析、规划或校验阶段产生的受控澄清原因。"""

    INTENT_AMBIGUOUS = "INTENT_AMBIGUOUS"
    ENTITY_MISSING = "ENTITY_MISSING"
    ENTITY_AMBIGUOUS = "ENTITY_AMBIGUOUS"
    ENTITY_NOT_FOUND = "ENTITY_NOT_FOUND"
    PARAMETER_MISSING = "PARAMETER_MISSING"
    PARAMETER_CONFLICT = "PARAMETER_CONFLICT"
    SCOPE_AMBIGUOUS = "SCOPE_AMBIGUOUS"
    REFERENCE_AMBIGUOUS = "REFERENCE_AMBIGUOUS"
    WRITE_CONFIRMATION_REQUIRED = "WRITE_CONFIRMATION_REQUIRED"


class ExecutionStatus(StrEnum):
    """后台执行批次、任务和步骤的统一状态。"""

    PENDING = "PENDING"  # 等待执行
    WAITING = "WAITING"  # 等待前置任务完成
    RUNNING = "RUNNING"  # 正在执行
    SUCCESS = "SUCCESS"  # 执行成功
    PARTIAL_SUCCESS = "PARTIAL_SUCCESS"  # 部分成功，主要用于批次
    FAILED = "FAILED"  # 执行失败
    CANCELLED = "CANCELLED"  # 已取消
    SKIPPED = "SKIPPED"  # 已跳过，主要用于步骤


class ReferenceType(StrEnum):
    """
    消息关联的业务对象类型。

    该类型决定 MessageReferenceResponse.reference_no 应当到哪张业务表查询。
    引用只保存对象编号，不在会话响应中重复返回完整的任务或结果数据。
    """

    # 统一执行任务；reference_no 对应 execution_task_tb.task_no。
    # 一个任务包含其下所有预测任务明细。
    EXECUTION_TASK = "EXECUTION_TASK"

    # 一次预测执行编号；对应 model_execute_record_tb.forecast_no。
    FORECAST = "FORECAST"


class ReferenceRelation(StrEnum):
    """当前消息与所引用业务对象之间的关系。"""

    # 当前消息触发创建了该对象，例如提交训练后创建了一个执行任务。
    CREATED = "CREATED"

    # 当前消息提到或使用了一个已经存在的对象，例如询问历史任务为何失败。
    REFERENCED = "REFERENCED"

    # 当前助手消息把该对象作为结果卡片或业务结果展示给用户。
    DISPLAYED = "DISPLAYED"


class ClarificationInputType(StrEnum):
    """缺失参数建议使用的前端输入控件类型。"""

    TEXT = "TEXT"  # 文本输入
    NUMBER = "NUMBER"  # 数值输入
    DATE = "DATE"  # 日期选择
    SELECT = "SELECT"  # 单选
    MULTI_SELECT = "MULTI_SELECT"  # 多选
    CONFIRM = "CONFIRM"  # 确认或取消


class ResourceRole(StrEnum):
    """步骤数据资源在本步骤中的用途。"""

    INPUT = "INPUT"  # 步骤读取的数据
    OUTPUT = "OUTPUT"  # 步骤生成的数据
    METRIC = "METRIC"  # 指标明细
    LOG = "LOG"  # 执行日志
    RESULT = "RESULT"  # 可供业务页面使用的最终结果


class ResourceType(StrEnum):
    """步骤详情资源的展示或读取类型。"""

    DATASET = "DATASET"  # 可分页查询的数据集
    TABLE = "TABLE"  # 表格数据
    CHART = "CHART"  # 图表数据
    JSON = "JSON"  # 结构化 JSON 数据
    LOG = "LOG"  # 可分段读取的日志
    FILE = "FILE"  # 文件或可下载制品
    MODEL = "MODEL"  # 模型版本或模型制品


# ==================== 公共对象 ====================


class ApiModel(BaseModel):
    """接口公共基类，入参兼容蛇形，出参统一使用小驼峰字段名。"""

    model_config = ConfigDict(
        alias_generator=_to_camel,
        populate_by_name=True,
        extra="forbid",
        str_strip_whitespace=True,
    )


class BusinessContext(ApiModel):
    """
    用户发送本轮消息时正在查看的业务对象。

    该对象是用于指代消解的当前焦点，可以随页面选择变化，不是会话绑定条件。
    创建训练或预测任务前，地区、行业和客户仍须经过主数据校验或用户确认。
    """

    region_code: str | None = Field(
        default=None,
        max_length=64,
        description="当前地区编码；行政层级由地区基础数据定义",
    )
    region_name: str | None = Field(
        default=None,
        max_length=128,
        description="当前地区名称，仅用于展示和实体确认",
    )
    industry_code: str | None = Field(
        default=None,
        max_length=64,
        description="当前行业编码",
    )
    industry_name: str | None = Field(
        default=None,
        max_length=128,
        description="当前行业名称，仅用于展示和实体确认",
    )
    customer_code: str | None = Field(
        default=None,
        max_length=64,
        description="当前客户编码",
    )
    customer_name: str | None = Field(
        default=None,
        max_length=128,
        description="当前客户名称，仅用于展示和实体确认",
    )


# ==================== Java 调用 Python 的内部请求 ====================


class AgentRecentMessage(ApiModel):
    """Java 从数据库加载并传给 Agent 的一条最近原始消息。"""

    message_id: int = Field(
        ge=1,
        description="消息数据库主键，对应 ai_conversation_message_tb.id",
    )
    turn_id: str | None = Field(
        default=None,
        max_length=64,
        description="消息所属对话轮次；系统历史消息可以为空",
    )
    role: str = Field(
        min_length=1,
        max_length=16,
        description="消息角色：USER、ASSISTANT、SYSTEM 或 TOOL",
    )
    content: str = Field(
        min_length=1,
        max_length=10000,
        description="消息正文",
    )
    message_type: str | None = Field(
        default=None,
        max_length=64,
        description="消息分类或来源 Skill 编码",
    )
    created_at: datetime | None = Field(
        default=None,
        description="消息创建时间",
    )
    references: list["MessageReferenceResponse"] = Field(
        default_factory=list,
        description="该消息关联的执行任务、训练批次或预测批次",
    )


class AgentConversationContext(ApiModel):
    """
    Java 统一加载的会话上下文。

    summary 用于承载较早消息的压缩结果，memory 保存结构化会话记忆，
    recent_messages 保存最近未压缩的原始消息，三者职责相互独立。
    """

    summary: str | None = Field(
        default=None,
        max_length=50000,
        description="较早会话内容的压缩摘要；尚未生成时为空",
    )
    memory: dict[str, Any] = Field(
        default_factory=dict,
        description="当前会话的结构化记忆，如已确认的业务参数和待处理状态",
    )
    recent_messages: list[AgentRecentMessage] = Field(
        default_factory=list,
        max_length=50,
        description="最近原始消息，按创建时间正序排列，不包含本轮用户消息",
    )


class AgentChatRequest(ApiModel):
    """
    Java 会话服务调用 Python Agent 的内部请求。

    会话、用户消息和 Agent Run 已由 Java 创建并提交数据库。Python 只能使用
    Java 传入的服务端标识进行本轮编排，不能自行创建或改写这些标识。
    """

    conversation_no: str = Field(
        min_length=1,
        max_length=64,
        description="Java 创建或校验后的会话编号",
    )
    turn_id: str = Field(
        min_length=1,
        max_length=64,
        description="Java 生成的本轮对话标识，同一轮用户消息和助手消息共用",
    )
    run_no: str = Field(
        min_length=1,
        max_length=64,
        description="Java 创建的 Agent Run 编号，对应 agent_run_tb.run_no",
    )
    message: str = Field(
        min_length=1,
        max_length=4000,
        description="用户本轮输入的自然语言消息",
    )
    input_type: UserInputType = Field(
        default=UserInputType.TEXT,
        description="普通文本、澄清回复、确认回复或拒绝回复；普通客户端可省略",
    )
    interaction_id: str | None = Field(
        default=None,
        max_length=64,
        description="结构化回复对应的待处理交互编号",
    )
    reply_to_turn_id: str | None = Field(
        default=None,
        max_length=64,
        description="结构化回复所回应的助手轮次编号",
    )
    answers: dict[str, Any] = Field(
        default_factory=dict,
        description="结构化澄清字段答案；候选选择提交服务端生成的不透明选项值",
    )
    agent_code: AgentType = Field(
        description="Java 根据会话确定的智能体编码，不接受本轮临时切换",
    )
    business_context: BusinessContext = Field(
        description="本轮消息发生时的当前业务焦点；不是会话绑定条件",
    )
    conversation_context: AgentConversationContext = Field(
        description="Java 统一加载的会话摘要、结构化会话记忆和最近原始消息",
    )

    @model_validator(mode="after")
    def validate_interaction_reference(self) -> "AgentChatRequest":
        """结构化回复必须绑定服务端此前返回的待处理交互。"""
        if self.input_type != UserInputType.TEXT:
            if not self.interaction_id:
                raise ValueError("结构化回复必须提供 interactionId")
            if not self.reply_to_turn_id:
                raise ValueError("结构化回复必须提供 replyToTurnId")
        return self


# ==================== 会话响应 ====================


class ClarificationOption(ApiModel):
    """缺失参数的一个可选值。"""

    value: str = Field(description="提交给后端的选项值")
    label: str = Field(description="展示给用户的选项名称")


class ResolutionIssue(ApiModel):
    """阻止请求继续形成可执行计划的一个结构化问题。"""

    reason: ClarificationReason = Field(description="问题的稳定原因编码")
    field: str = Field(max_length=128, description="问题对应的语义、实体或参数字段")
    message: str = Field(description="可直接展示或用于组合澄清问题的说明")
    input_type: ClarificationInputType = Field(
        default=ClarificationInputType.TEXT,
        description="建议前端采用的答案输入方式",
    )
    required: bool = Field(default=True, description="该问题是否阻止当前请求继续执行")
    options: list[ClarificationOption] = Field(default_factory=list)
    details: dict[str, Any] = Field(
        default_factory=dict,
        description="仅供服务端恢复待处理请求使用的结构化信息",
    )


class ClarificationField(ApiModel):
    """需要用户补充的一个结构化参数。"""

    parameter_code: str = Field(
        max_length=64,
        description="参数编码，如 customerCode 或 forecastDays",
    )
    parameter_name: str = Field(
        max_length=128,
        description="参数中文名称，如客户或预测天数",
    )
    reason: ClarificationReason = Field(
        default=ClarificationReason.PARAMETER_MISSING,
        description="当前字段需要用户处理的原因",
    )
    input_type: ClarificationInputType = Field(
        default=ClarificationInputType.TEXT,
        description="前端建议使用的输入控件类型",
    )
    required: bool = Field(
        default=True,
        description="该参数是否为继续执行所必需",
    )
    options: list[ClarificationOption] = Field(
        default_factory=list,
        description="候选选项；仅选择类参数需要返回",
    )


class ClarificationResponse(ApiModel):
    """Agent 等待用户补充或确认信息时返回的结构化说明。"""

    interaction_id: str = Field(
        min_length=1,
        max_length=64,
        description="待处理交互编号；回复时必须原样携带",
    )
    interaction_type: InteractionType = Field(
        description="本轮等待的是信息澄清还是执行确认",
    )
    reply_to_turn_id: str = Field(
        min_length=1,
        max_length=64,
        description="产生本次交互的助手轮次编号",
    )
    request_id: str | None = Field(
        default=None,
        max_length=32,
        description="本次交互所属的结构化请求编号",
    )
    reason: ClarificationReason = Field(description="本次交互的主要原因")
    resume_from: str = Field(
        max_length=64,
        description="用户回复后应恢复的处理阶段",
    )
    prompt: str = Field(
        description="向用户展示的补充信息提示语",
    )
    fields: list[ClarificationField] = Field(
        min_length=1,
        description="本轮需要用户补充或确认的字段",
    )


class MessageReferenceResponse(ApiModel):
    """
    当前助手消息关联的业务对象轻量引用。

    一条消息可以关联多个批次或任务。前端根据对象类型和对象编号查询任务、
    执行步骤或业务结果详情；该对象本身不承载完整的执行过程和结果数据。
    """

    reference_type: ReferenceType = Field(
        description=(
            "业务对象类型，决定 referenceNo 对应的业务表；"
            "对应 ai_message_ref_tb.ref_type"
        ),
    )
    reference_no: str = Field(
        min_length=1,
        max_length=64,
        description=(
            "业务对象编号：执行批次使用 batch_no，执行任务使用 task_no，"
            "训练批次使用训练 batch_no，预测批次使用 forecast_batch_no；"
            "对应 ai_message_ref_tb.ref_no"
        ),
    )
    relation_type: ReferenceRelation = Field(
        description=(
            "当前消息与业务对象的关系：CREATED 表示本消息触发创建，"
            "REFERENCED 表示引用已有对象，DISPLAYED 表示作为结果展示；"
            "对应 ai_message_ref_tb.relation_type"
        ),
    )
    title: str | None = Field(
        default=None,
        max_length=200,
        description="引用对象展示标题；为空时前端可通过详情接口获取",
    )
    status: str | None = Field(
        default=None,
        max_length=32,
        description="对象返回时的状态快照；页面应通过任务接口获取最新状态",
    )


class AgentChatResponse(ApiModel):
    """
    Python 返回给 Java 会话服务的内部 Agent 执行结果。

    Java 使用该结果保存助手消息、业务对象引用并更新 agent_run_tb；Vue 不直接
    调用或消费本对象。训练、预测任务的实时状态仍通过 references 查询。
    """

    conversation_no: str = Field(
        min_length=1,
        max_length=64,
        description="原样回传 Java 创建或校验后的会话编号",
    )
    turn_id: str = Field(
        min_length=1,
        max_length=64,
        description="原样回传 Java 生成的对话轮次编号",
    )
    run_no: str = Field(
        min_length=1,
        max_length=64,
        description="原样回传 Java 创建的 Agent Run 编号",
    )
    agent_code: AgentType = Field(
        description="本轮实际使用的智能体编码",
    )
    status: AgentStatus = Field(
        description="Agent 本轮执行状态，与后台训练预测任务状态相互独立",
    )
    message: str = Field(
        min_length=1,
        max_length=10000,
        description="展示给用户的自然语言回复",
    )
    clarification: ClarificationResponse | None = Field(
        default=None,
        description="等待用户补充或确认信息时返回；其他状态为空",
    )
    references: list[MessageReferenceResponse] = Field(
        default_factory=list,
        description="当前消息关联的多个执行任务、训练批次或预测批次",
    )
    intent_code: str | None = Field(
        default=None,
        max_length=32,
        description="Agent 识别的主意图编码，用于更新 agent_run_tb.intent_code",
    )
    intent_data: dict[str, Any] = Field(
        default_factory=dict,
        description="结构化意图与已解析参数摘要，用于更新 agent_run_tb.intent_json",
    )
    skill_code: str | None = Field(
        default=None,
        max_length=64,
        description="本轮选中的 Skill 编码，用于更新 agent_run_tb.skill_code",
    )
    result_data: dict[str, Any] = Field(
        default_factory=dict,
        description="Agent 编排结果摘要，仅供 Java 持久化审计，不直接返回给 Vue",
    )
    memory_patch: dict[str, Any] = Field(
        default_factory=dict,
        description="本轮会话记忆变更补丁，仅供 Java 校验、合并并持久化",
    )
    presentation: dict[str, Any] | None = Field(
        default=None,
        description="前端展示结构，例如列表、图表或进度条配置",
    )
    execution_result: dict[str, Any] | None = Field(
        default=None,
        description="本轮工具真实结果；列表 records 用于前端展示查询表格",
    )
    error_code: str | None = Field(
        default=None,
        max_length=64,
        description="Agent 本轮失败时的稳定错误编码",
    )
    error_message: str | None = Field(
        default=None,
        description="Agent 本轮失败时经过脱敏的错误说明",
    )


# ==================== 后台业务步骤响应 ====================


class ExecutionStepResponse(ApiModel):
    """后台业务执行步骤摘要，用于快速绘制任务执行时间线。"""

    step_no: str = Field(
        description="执行步骤唯一编号，对应 execution_step_tb.step_no",
    )
    task_no: str = Field(
        description="所属执行任务编号，对应 execution_task_tb.task_no",
    )
    attempt_no: int = Field(
        ge=1,
        description="任务执行次数；首次为1，失败重试时递增",
    )
    step_code: str = Field(
        description="业务步骤编码，如 DATA_PREPARE、FEATURE_BUILD、MODEL_TRAIN",
    )
    step_name: str = Field(
        description="业务步骤名称，如历史销量数据准备、训练特征构建",
    )
    step_order: int = Field(
        ge=0,
        description="步骤执行顺序，用于前端按业务顺序展示",
    )
    stage_code: str | None = Field(
        default=None,
        description="所属业务阶段编码，如 DATA、FEATURE、TRAIN、FORECAST",
    )
    status: ExecutionStatus = Field(
        description="当前步骤执行状态",
    )
    progress_percent: float | None = Field(
        default=None,
        ge=0,
        le=100,
        description="步骤执行进度；无法准确计算时为空，不应伪造百分比",
    )
    message: str | None = Field(
        default=None,
        description="步骤业务摘要，如成功获取730条历史销量数据",
    )
    has_detail: bool = Field(
        default=False,
        description="是否存在可通过步骤详情接口查询的输入、输出、指标或数据资源",
    )
    started_at: datetime | None = Field(
        default=None,
        description="步骤开始执行时间",
    )
    finished_at: datetime | None = Field(
        default=None,
        description="步骤结束执行时间",
    )


class ExecutionMetricResponse(ApiModel):
    """执行步骤产生的一个可展示业务指标。"""

    metric_code: str = Field(
        max_length=64,
        description="指标编码，如 MAPE、RMSE、SAMPLE_COUNT",
    )
    metric_name: str = Field(
        max_length=128,
        description="指标展示名称",
    )
    value: int | float | str | bool | None = Field(
        default=None,
        description="指标原始值",
    )
    unit: str | None = Field(
        default=None,
        max_length=32,
        description="指标单位，如百分比、条、秒",
    )
    display_value: str | None = Field(
        default=None,
        description="已格式化的展示值；为空时前端根据 value 和 unit 处理",
    )


class ExecutionResourceResponse(ApiModel):
    """执行步骤使用或产生的一份可按需查询的数据资源。"""

    resource_ref: str = Field(
        min_length=1,
        max_length=512,
        description="资源不透明引用；前端将其交给步骤资源接口，不直接解析",
    )
    resource_code: str = Field(
        max_length=64,
        description="步骤内稳定的资源编码，如 HISTORY_SALES、FEATURE_MATRIX、TRAIN_LOG",
    )
    resource_name: str = Field(
        max_length=200,
        description="资源展示名称，如历史销量数据、训练特征矩阵",
    )
    resource_role: ResourceRole = Field(
        description="资源在当前步骤中的用途，如输入、输出、日志或最终结果",
    )
    resource_type: ResourceType = Field(
        description="资源数据或展示类型，用于选择表格、图表、日志等查看组件",
    )
    summary: dict[str, Any] = Field(
        default_factory=dict,
        description="资源轻量摘要，如数据源、时间范围、记录数和数据版本",
    )


class ExecutionStepDetailResponse(ExecutionStepResponse):
    """单个后台步骤的完整可展示详情，不直接携带大规模业务数据。"""

    input_data: dict[str, Any] = Field(
        default_factory=dict,
        description="步骤输入参数摘要；不得包含密钥、完整数据集等敏感或大体量内容",
    )
    output_data: dict[str, Any] = Field(
        default_factory=dict,
        description="步骤输出结果摘要；完整业务数据通过 resources 按需查询",
    )
    metrics: list[ExecutionMetricResponse] = Field(
        default_factory=list,
        description="步骤产生的结构化指标列表",
    )
    resources: list[ExecutionResourceResponse] = Field(
        default_factory=list,
        description="步骤读取或生成的数据资源；一个步骤可以关联多个资源",
    )
    error_code: str | None = Field(
        default=None,
        max_length=64,
        description="步骤执行失败时的稳定错误编码",
    )
    error_message: str | None = Field(
        default=None,
        description="经过脱敏、可以展示给用户的失败原因",
    )


class ExecutionStepListResponse(ApiModel):
    """某次任务执行尝试的步骤摘要列表。"""

    task_no: str = Field(
        description="执行任务编号",
    )
    attempt_no: int = Field(
        ge=1,
        description="当前查询的任务执行次数",
    )
    steps: list[ExecutionStepResponse] = Field(
        default_factory=list,
        description="步骤摘要列表，按 stepOrder 升序排列",
    )


# ==================== LangGraph 内部运行时对象 ====================


class AgentRuntimeRequest(BaseModel):
    """LangGraph 编排层使用的内部请求，不属于 Python 对外接口。"""

    turn_id: str
    conversation_no: str
    message: str
    input_type: UserInputType = UserInputType.TEXT
    interaction_id: str | None = None
    reply_to_turn_id: str | None = None
    answers: dict[str, Any] = Field(default_factory=dict)
    agent_type: AgentType | None = None
    conversation_summary: str | None = None
    metadata: dict[str, Any] = Field(default_factory=dict)
    memory: dict[str, Any] = Field(default_factory=dict)
    history: list[dict[str, str]] = Field(default_factory=list)


class AgentRuntimeResponse(BaseModel):
    """LangGraph 编排层的内部结果，不作为 Python 对外接口发布。"""

    conversation_no: str
    agent_type: AgentType | None = None
    intent_code: str | None = None
    message: str
    status: str
    skill_name: str | None = None
    skill_selection: dict[str, Any] | None = None
    parameters: dict[str, Any] = Field(default_factory=dict)
    entity_resolution: dict[str, Any] | None = None
    missing_parameters: list[str] = Field(default_factory=list)
    resolution_issues: list[ResolutionIssue] = Field(default_factory=list)
    interaction_id: str | None = None
    interaction_type: InteractionType | None = None
    reply_to_turn_id: str | None = None
    request_id: str | None = None
    clarification_reason: ClarificationReason | None = None
    resume_from: str | None = None
    memory_loaded: bool = False
    available_tools: list[str] = Field(default_factory=list)
    executed_tool: str | None = None
    execution_result: dict[str, Any] | None = None
    presentation: dict[str, Any] | None = None
    progress: dict[str, Any] | None = None
    forecast_result: dict[str, Any] | None = None
    training_result: dict[str, Any] | None = None
    next_action: str | None = None
    next_check_seconds: int | None = None
    decision_reason: str | None = None
    memory_patch: dict[str, Any] = Field(default_factory=dict)
