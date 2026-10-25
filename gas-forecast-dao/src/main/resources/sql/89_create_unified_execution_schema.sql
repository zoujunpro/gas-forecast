use gas_data;

create table if not exists agent_run_tb (
    id bigint not null auto_increment comment '主键ID',
    run_no varchar(64) not null comment 'Agent执行唯一编号',
    conversation_id bigint not null comment '所属会话ID，关联ai_conversation_tb.id',
    message_id bigint unsigned not null comment '触发本次Agent执行的用户消息ID，关联ai_conversation_message_tb.id',
    agent_code varchar(64) not null comment '执行智能体编码',
    status varchar(24) not null default 'PENDING'
        comment '执行状态：PENDING待执行、RUNNING执行中、WAITING等待用户补充参数、SUCCESS成功、FAILED失败、CANCELLED取消',
    intent_code varchar(32) default null
        comment '主意图编码：CHAT普通问答、QUERY数据查询、TRAIN模型训练、FORECAST模型预测、COMPOSITE组合操作',
    intent_json json default null comment '结构化意图信息，支持多个操作及业务参数',
    skill_code varchar(64) default null comment '本次执行匹配的Skill编码',
    request_json json default null comment '本次Agent请求参数快照，包括用户消息及本轮页面提供的当前业务焦点',
    result_json json default null comment 'Agent执行结果摘要，包括工具调用结果及提交的业务任务信息',
    error_code varchar(64) default null comment '执行失败错误编码',
    error_message text default null comment '执行失败原因',
    started_at datetime default null comment '开始执行时间',
    finished_at datetime default null comment '执行结束时间',
    created_at datetime not null default current_timestamp comment '创建时间',
    updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间',
    primary key (id),
    unique key uk_run_no (run_no),
    key idx_conversation_id (conversation_id),
    key idx_message_id (message_id),
    key idx_agent_status (agent_code, status),
    key idx_created_at (created_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci comment='Agent请求执行记录表';

create table if not exists execution_batch_tb (
    id bigint not null auto_increment comment '主键ID',
    batch_no varchar(64) not null comment '执行批次唯一编号',
    batch_name varchar(200) default null comment '执行批次名称',
    batch_type varchar(32) not null comment '批次类型：TRAIN模型训练、FORECAST模型预测、WORKFLOW组合流程',
    trigger_source varchar(32) not null comment '触发来源：AGENT智能体、MANUAL人工操作、SCHEDULE定时调度',
    run_no varchar(64) default null comment '来源Agent执行编号，关联agent_run_tb.run_no，非智能体触发时为空',
    conversation_no varchar(64) default null comment '来源会话编号，非会话触发时为空',
    agent_code varchar(64) default null comment '来源智能体编码，非智能体触发时为空',
    scope_type varchar(32) default null
        comment '执行范围类型：REGION地区、REGION_INDUSTRY地区行业、CUSTOMER单客户、MULTI_CUSTOMER多客户',
    region_code varchar(64) default null comment '地区编码，表示本批次的地区范围',
    industry_code varchar(64) default null comment '行业编码，表示本批次的行业范围',
    customer_code varchar(64) default null comment '客户编码，单客户执行时填写',
    status varchar(32) not null default 'PENDING'
        comment '批次状态：PENDING待执行、RUNNING执行中、SUCCESS成功、PARTIAL_SUCCESS部分成功、FAILED失败、CANCELLED取消',
    current_stage varchar(64) default null comment '当前执行阶段摘要',
    progress_percent decimal(5, 2) not null default 0 comment '整体执行进度百分比，范围0到100',
    total_count int not null default 0 comment '任务总数',
    pending_count int not null default 0 comment '待执行任务数，包括等待依赖的任务',
    running_count int not null default 0 comment '正在执行的任务数',
    success_count int not null default 0 comment '成功任务数',
    failed_count int not null default 0 comment '失败任务数',
    cancelled_count int not null default 0 comment '取消任务数',
    request_json json default null comment '执行请求参数快照，包括模型配置、执行范围和预测参数',
    result_json json default null comment '批次执行结果摘要，不保存完整训练预测结果',
    error_code varchar(64) default null comment '批次错误编码',
    error_message text default null comment '批次失败原因或错误摘要',
    idempotency_key varchar(128) default null comment '幂等键，用于防止重复创建执行批次',
    created_by varchar(64) default null comment '创建人编码，系统调度可使用SYSTEM',
    created_at datetime not null default current_timestamp comment '创建时间',
    started_at datetime default null comment '开始执行时间',
    finished_at datetime default null comment '执行结束时间',
    updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间',
    primary key (id),
    unique key uk_batch_no (batch_no),
    unique key uk_idempotency_key (idempotency_key),
    key idx_run_no (run_no),
    key idx_conversation_no (conversation_no),
    key idx_status_created (status, created_at),
    key idx_business_scope (region_code, industry_code, customer_code),
    constraint chk_execution_batch_counts check (
        total_count >= 0
        and pending_count >= 0
        and running_count >= 0
        and success_count >= 0
        and failed_count >= 0
        and cancelled_count >= 0
        and total_count = pending_count + running_count + success_count + failed_count + cancelled_count
    ),
    constraint chk_execution_batch_progress check (progress_percent between 0 and 100)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci
    comment='统一业务执行批次表，支持智能体、人工及定时调度';

create table if not exists execution_task_tb (
    id bigint not null auto_increment comment '主键ID',
    task_no varchar(64) not null comment '执行任务唯一编号',
    batch_no varchar(64) not null comment '所属执行批次编号，关联execution_batch_tb.batch_no',
    business_type varchar(32) not null comment '业务类型：TRAIN模型训练、FORECAST模型预测',
    business_no varchar(64) default null comment '对应训练或预测业务批次号，业务批次尚未创建时为空',
    agent_code varchar(64) default null comment '来源智能体编码，非智能体触发时为空',
    scope_type varchar(32) not null comment '执行维度：REGION地区、REGION_INDUSTRY地区行业、CUSTOMER客户',
    region_code varchar(64) default null comment '地区编码',
    region_name varchar(128) default null comment '地区名称快照',
    industry_code varchar(64) default null comment '行业编码',
    industry_name varchar(128) default null comment '行业名称快照',
    customer_code varchar(64) default null comment '客户编码',
    customer_name varchar(128) default null comment '客户名称快照',
    status varchar(32) not null default 'PENDING'
        comment '任务状态：PENDING待执行、WAITING等待依赖、RUNNING执行中、SUCCESS成功、FAILED失败、CANCELLED取消',
    current_stage varchar(64) default null comment '当前执行阶段，如DATA_PREPARE、FEATURE_BUILD、MODEL_TRAIN、FORECAST',
    current_step_code varchar(64) default null comment '当前执行步骤编码',
    progress_percent decimal(5, 2) not null default 0 comment '任务执行进度百分比，范围0到100',
    attempt_no int not null default 0 comment '当前执行尝试次数，首次领取执行时为1',
    max_attempts int not null default 3 comment '最大允许执行次数，包括首次执行',
    next_retry_at datetime default null comment '下次允许重试时间',
    worker_id varchar(128) default null comment '当前执行Worker实例标识',
    heartbeat_at datetime default null comment 'Worker最近心跳时间',
    lease_expires_at datetime default null comment '任务执行租约到期时间，用于故障恢复',
    request_json json default null comment '任务执行参数快照，包括模型配置、特征、预测周期等',
    checkpoint_json json default null comment '任务业务执行检查点，用于恢复可重入执行步骤',
    result_json json default null comment '任务执行结果摘要，不保存完整训练或预测数据',
    error_code varchar(64) default null comment '最近一次执行错误编码',
    error_message text default null comment '最近一次执行错误信息',
    idempotency_key varchar(128) default null comment '任务幂等键，防止重复创建或执行',
    created_by varchar(64) not null default 'SYSTEM' comment '任务创建人编码，系统调度使用SYSTEM',
    created_at datetime not null default current_timestamp comment '创建时间',
    started_at datetime default null comment '首次开始执行时间',
    finished_at datetime default null comment '最终执行结束时间',
    updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间',
    primary key (id),
    unique key uk_task_no (task_no),
    unique key uk_idempotency_key (idempotency_key),
    key idx_batch_no (batch_no),
    key idx_batch_status (batch_no, status),
    key idx_task_status_retry (status, next_retry_at),
    key idx_worker_lease (status, lease_expires_at),
    key idx_business_scope (region_code, industry_code, customer_code),
    key idx_business_ref (business_type, business_no),
    constraint chk_execution_task_attempts check (attempt_no >= 0 and max_attempts >= 1 and attempt_no <= max_attempts),
    constraint chk_execution_task_progress check (progress_percent between 0 and 100)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci
    comment='统一业务执行任务表，支持异步训练预测、进度跟踪、失败重试及故障恢复';

create table if not exists execution_step_tb (
    id bigint not null auto_increment comment '主键ID',
    step_no varchar(64) not null comment '执行步骤唯一编号',
    task_no varchar(64) not null comment '所属执行任务编号，关联execution_task_tb.task_no',
    attempt_no int not null default 1 comment '任务执行次数，首次为1，重试时递增',
    step_code varchar(64) not null comment '步骤编码，如DATA_PREPARE、FEATURE_BUILD、MODEL_TRAIN',
    step_name varchar(128) not null comment '步骤名称，如历史销量数据准备、动态特征获取',
    step_order int not null default 0 comment '步骤执行顺序',
    status varchar(32) not null default 'PENDING'
        comment '步骤状态：PENDING待执行、RUNNING执行中、SUCCESS成功、FAILED失败、SKIPPED跳过、CANCELLED取消',
    progress_percent decimal(5, 2) not null default 0 comment '当前步骤执行进度百分比，范围0到100',
    stage_code varchar(64) default null comment '所属执行阶段编码，如DATA、FEATURE、TRAIN、FORECAST',
    input_json mediumtext default null comment '步骤输入参数摘要，可保存文本或JSON，不保存完整大数据集',
    output_json mediumtext default null comment '步骤输出结果摘要，可保存文本或JSON',
    metrics_json mediumtext default null comment '步骤执行指标，可保存文本或JSON',
    detail_type varchar(32) default null comment '详细数据类型：TABLE表格、CHART图表、JSON结构化数据、LOG日志、FILE文件',
    detail_ref varchar(512) default null comment '详细数据引用地址或业务数据标识，用于查询真实执行数据',
    message text default null comment '步骤执行说明，供前端展示',
    error_code varchar(64) default null comment '步骤失败错误编码',
    error_message text default null comment '步骤失败原因',
    started_at datetime default null comment '步骤开始执行时间',
    finished_at datetime default null comment '步骤结束时间',
    created_at datetime not null default current_timestamp comment '创建时间',
    updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间',
    primary key (id),
    unique key uk_step_no (step_no),
    unique key uk_task_attempt_step (task_no, attempt_no, step_code),
    key idx_task_order (task_no, attempt_no, step_order),
    key idx_task_status (task_no, status),
    constraint chk_execution_step_attempt check (attempt_no >= 1),
    constraint chk_execution_step_progress check (progress_percent between 0 and 100)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci
    comment='统一业务执行步骤表，记录训练预测过程、进度及数据明细引用';

create table if not exists ai_message_ref_tb (
    id bigint not null auto_increment comment '主键ID',
    message_id bigint unsigned not null comment '关联消息ID，关联ai_conversation_message_tb.id',
    ref_type varchar(32) not null
        comment '引用类型：EXECUTION_TASK执行任务、FORECAST预测结果',
    ref_no varchar(64) not null comment '引用对象编号，根据ref_type确定对应业务表',
    relation_type varchar(32) not null default 'REFERENCED'
        comment '关联类型：CREATED本消息触发创建、REFERENCED引用历史对象、DISPLAYED消息展示对象',
    created_at datetime not null default current_timestamp comment '创建时间',
    primary key (id),
    unique key uk_message_ref (message_id, ref_type, ref_no, relation_type),
    key idx_ref (ref_type, ref_no)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci
    comment='AI消息与执行任务、预测结果的引用关系表';
