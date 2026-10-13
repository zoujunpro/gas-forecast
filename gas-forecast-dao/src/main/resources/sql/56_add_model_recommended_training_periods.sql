use gas_data;

alter table model_config_tb
    add column recommended_training_periods int null comment '自动训练推荐历史周期数' after minimum_training_rows;

update model_config_tb
set recommended_training_periods = minimum_training_rows
where recommended_training_periods is null and minimum_training_rows is not null;
