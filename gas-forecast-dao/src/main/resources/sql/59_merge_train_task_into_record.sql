use gas_data;

-- 训练批次即训练任务：将调度字段并入批次表。
alter table model_train_record_tb
    add column active_dedup_key varchar(512) default null comment '活动批次防重键，终态置空' after status,
    add column trigger_source varchar(32) default null comment 'MANUAL/AI/SCHEDULE/DATA_EVENT' after active_dedup_key,
    add column priority int not null default 50 after trigger_source,
    add column retry_count int not null default 0 after priority,
    add column max_retry_count int not null default 3 after retry_count,
    add column next_retry_time datetime default null after max_retry_count,
    add column claimed_by varchar(128) default null after next_retry_time,
    add column claimed_at datetime default null after claimed_by,
    add column started_at datetime default null after claimed_at,
    add column finished_at datetime default null after started_at;

-- 迁移已有批次对应任务的调度状态；没有批次号的旧准备任务不再保留。
update model_train_record_tb r
join model_train_task_tb t on t.train_batch_no collate utf8mb4_unicode_ci = r.batch_no collate utf8mb4_unicode_ci
set r.active_dedup_key = t.active_dedup_key,
    r.trigger_source = t.trigger_source,
    r.priority = t.priority,
    r.retry_count = t.retry_count,
    r.max_retry_count = t.max_retry_count,
    r.next_retry_time = t.next_retry_time,
    r.claimed_by = t.claimed_by,
    r.claimed_at = t.claimed_at,
    r.started_at = t.started_at,
    r.finished_at = t.finished_at;

alter table model_train_record_tb
    add unique key uk_train_record_active_dedup (active_dedup_key),
    add key idx_train_record_dispatch (status, next_retry_time, priority, id),
    add key idx_train_record_agent_status (agent_code, status, updated_at);

delete from sys_role_permission_ref
where permission_id in (select id from sys_permission_tb where path = '/model/train-task');
delete from sys_permission_tb where path = '/model/train-task';

drop table if exists model_train_task_tb;
drop table if exists model_train_plan_tb;
