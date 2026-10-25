alter table model_execute_record_tb
    add column analysis_content mediumtext default null comment '基于本次回测和预测结果生成的研判说明'
    after forecast_result_json;
