use gas_data;

-- 旧数据无需迁移。执行步骤、批次关联、旧任务按依赖顺序删除。
drop table if exists agent_execution_step_tb;
drop table if exists agent_execution_batch_tb;
drop table if exists agent_execution_task_tb;
