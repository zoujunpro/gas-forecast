use gas_data;

-- 统一任务已由 execution_task_tb、execution_task_detail_tb、execution_step_tb 承担，
-- 不再保留历史执行批次汇总表。
drop table if exists execution_batch_tb;
