use gas_data;

-- 执行过程已统一使用agent_run_event_tb；旧表没有业务代码读写。
drop table if exists execution_event_tb;
