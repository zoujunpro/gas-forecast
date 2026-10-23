-- request_id 的幂等职责只保留在 agent_run_tb；任务创建后使用 task_no 作为唯一业务编号。
alter table execution_task_tb drop column request_id;
