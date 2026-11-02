-- 将异步预测任务关联到所属智能体运行，以便调度器持续写回真实过程事件。
ALTER TABLE execution_task_tb
    ADD COLUMN run_no varchar(64) DEFAULT NULL COMMENT '所属智能体运行编号，关联agent_run_tb.run_no' AFTER conversation_no,
    ADD KEY idx_execution_task_run_no (run_no);
