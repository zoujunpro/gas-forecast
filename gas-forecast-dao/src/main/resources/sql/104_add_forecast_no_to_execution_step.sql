alter table execution_step_tb
    add column forecast_no varchar(64) null comment '任务明细实际执行时绑定的稳定预测编号' after task_detail_no,
    add key idx_execution_step_forecast_no (forecast_no);
