use gas_data;

create table if not exists model_train_execution_step_tb (
    id bigint not null auto_increment comment '主键',
    train_batch_no varchar(64) not null comment '训练批次号',
    attempt_no int not null comment '第几次执行，从1开始',
    parent_step_id bigint default null comment '父步骤ID，空表示主步骤',
    step_level int not null default 1 comment '步骤层级，1主步骤2子步骤',
    step_code varchar(64) not null comment '步骤编码',
    step_name varchar(128) not null comment '步骤名称',
    step_order int not null comment '步骤顺序',
    status varchar(32) not null comment 'RUNNING/SUCCESS/FAILED/SKIPPED',
    input_summary text default null comment '输入摘要',
    output_summary longtext default null comment '输出摘要或产出信息',
    produced_count int default null comment '产出数据量',
    error_message text default null comment '失败原因',
    started_at datetime not null comment '开始时间',
    finished_at datetime default null comment '结束时间',
    duration_ms bigint default null comment '执行耗时毫秒',
    created_at datetime not null,
    updated_at datetime not null,
    primary key (id),
    unique key uk_train_step_attempt (train_batch_no, attempt_no, step_code),
    key idx_train_step_batch (train_batch_no, attempt_no, step_order),
    key idx_train_step_parent (parent_step_id, step_order),
    key idx_train_step_status (status, updated_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='模型训练批次执行步骤';
