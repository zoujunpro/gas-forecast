use gas_data;

-- 一条记录代表一个任务明细的一次预测执行；无历史数据需要保留，直接按新模型重建。
drop table if exists model_execute_record_tb;

create table model_execute_record_tb (
    id bigint not null auto_increment comment '主键ID',
    forecast_no varchar(64) not null comment '预测唯一编号，同时作为模型产物目录名',
    task_detail_id bigint not null comment '任务明细主键，关联 execution_task_detail_tb.id',

    agent_code varchar(64) not null comment '智能体编码',
    model_code varchar(64) not null comment '模型编码',
    model_version varchar(64) default null comment '模型版本',
    region_code varchar(64) default null comment '地区编码',
    region_name varchar(128) default null comment '地区名称快照',
    industry_code varchar(64) default null comment '行业编码',
    industry_name varchar(128) default null comment '行业名称快照',
    customer_code varchar(64) default null comment '客户编码',
    customer_name varchar(200) default null comment '客户名称快照',

    history_start_date date default null comment '历史训练数据开始日期',
    history_end_date date default null comment '历史训练数据结束日期',
    history_rows int default null comment '历史训练数据条数',
    forecast_start_date date default null comment '预测开始日期',
    forecast_horizon int default null comment '预测期数',
    forecast_unit varchar(16) default null comment '预测单位：day/tenday/month',

    status varchar(20) not null comment 'PENDING/RUNNING/SUCCESS/FAILED',
    best_model varchar(100) default null comment '最佳模型',
    candidate_count int default null comment '候选模型数量',
    successful_candidate_count int default null comment '成功候选模型数量',
    mape decimal(12,6) default null comment 'MAPE',
    wmape decimal(12,6) default null comment 'WMAPE',
    smape decimal(12,6) default null comment 'SMAPE',
    rmse decimal(18,4) default null comment 'RMSE',
    mae decimal(18,4) default null comment 'MAE',
    r2 decimal(12,6) default null comment 'R2',
    execute_duration_seconds decimal(12,3) default null comment '模型训练、回测和预测总耗时（秒）',

    request_param mediumtext default null comment '模型平台统一执行请求快照JSON文本',
    training_result_json mediumtext default null comment '模型平台返回的训练结果JSON文本',
    backtest_result_json mediumtext default null comment '模型平台返回的回测结果JSON文本',
    forecast_result_json mediumtext default null comment '模型平台返回的预测结果JSON文本',
    error_message mediumtext default null comment '执行失败原因',
    created_by varchar(64) default null comment '创建人',
    created_by_name varchar(64) default null comment '创建人名称',
    started_at datetime default null comment '开始执行时间',
    finished_at datetime default null comment '执行结束时间',
    created_at datetime not null default current_timestamp comment '创建时间',
    updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间',

    primary key (id),
    unique key uk_execute_forecast_no (forecast_no),
    unique key uk_execute_task_detail_id (task_detail_id),
    key idx_execute_status_id (status, id),
    key idx_execute_agent_status (agent_code, status),
    key idx_execute_region_history_end (region_code, history_end_date)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci
  comment='模型预测执行记录：一条预测编号包含训练、回测和预测结果快照';
