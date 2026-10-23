-- request_id 是幂等请求键；task_no 是任务及其目标关联的稳定主业务编号。
alter table execution_task_tb
    add column task_no varchar(64) not null comment '执行任务唯一编号' after request_id,
    add unique key uk_execution_task_no (task_no);
