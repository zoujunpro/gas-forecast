-- 旧的固定步骤记录已由 agent_run_event_tb 替代。
-- 事件按运行实际发生的顺序写入，不再维护 execution_step_tb。
DROP TABLE IF EXISTS execution_step_tb;
