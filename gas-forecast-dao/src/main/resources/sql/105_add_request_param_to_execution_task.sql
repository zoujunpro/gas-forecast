alter table execution_task_tb
    add column request_param mediumtext null comment '任务创建时的完整预测参数快照' after scope_condition_json;
