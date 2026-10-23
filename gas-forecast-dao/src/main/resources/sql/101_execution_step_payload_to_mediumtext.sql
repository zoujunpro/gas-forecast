use gas_data;

-- 步骤输入、输出和指标既可能是普通说明文字，也可能是 JSON，统一使用 MEDIUMTEXT 保存。
alter table execution_step_tb
    modify column input_json mediumtext default null comment '步骤输入参数摘要，可保存文本或JSON',
    modify column output_json mediumtext default null comment '步骤输出结果摘要，可保存文本或JSON',
    modify column metrics_json mediumtext default null comment '步骤执行指标，可保存文本或JSON';
