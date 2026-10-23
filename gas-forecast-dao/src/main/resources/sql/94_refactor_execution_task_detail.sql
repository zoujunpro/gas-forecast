use gas_data;

-- 一条关联记录是任务下的一个最小执行目标；训练、预测批次在同一行回填。
alter table execution_task_detail_tb
    add column task_id bigint not null comment '父任务主键，关联execution_task_tb.id' after id,
    add column detail_seq int not null comment '父任务内明细序号' after task_no,
    add column detail_no varchar(96) not null comment '任务明细展示编号' after detail_seq,
    add column agent_code varchar(64) not null comment '执行智能体编码' after task_no,
    add column dimension_type varchar(32) not null comment 'CUSTOMER客户、REGION地区、INDUSTRY行业' after agent_code,
    add column region_code varchar(64) not null default '' comment '地区编码；不适用时为空串' after dimension_type,
    add column industry_code varchar(64) not null default '' comment '行业编码；不适用时为空串' after region_code,
    add column customer_code varchar(64) not null default '' comment '客户编码；不适用时为空串' after industry_code,
    add column train_batch_no varchar(64) default null comment '训练批次号' after customer_code,
    add column forecast_batch_no varchar(64) default null comment '预测批次号' after train_batch_no,
    add column status varchar(32) not null default 'PENDING' comment '最小目标执行状态' after forecast_batch_no,
    add column updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间' after created_at,
    add unique key uk_task_detail_seq (task_id, detail_seq),
    add unique key uk_task_detail_no (detail_no),
    add unique key uk_task_target (task_id, dimension_type, region_code, industry_code, customer_code),
    add key idx_train_batch (train_batch_no),
    add key idx_forecast_batch (forecast_batch_no);
