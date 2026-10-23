alter table execution_task_detail_tb
    add column forecast_no varchar(64) null comment '首次执行生成的稳定预测编号；重试复用' after customer_code,
    add unique key uk_task_detail_forecast_no (forecast_no);
