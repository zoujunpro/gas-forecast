use gas_data;

-- 时间粒度属于模型能力，不再属于特征定义。
alter table model_feature_definition_tb
    drop index uk_feature_code_granularity,
    drop column time_granularity,
    add unique key uk_feature_code (feature_code);

-- 同一特征被不同模型使用时，可以按模型覆盖聚合、缺失处理和计算参数。
alter table model_feature_ref
    add column aggregation_method varchar(16) null comment '覆盖特征默认聚合方式' after feature_order,
    add column missing_value_strategy varchar(16) null comment '覆盖特征默认缺失值策略' after aggregation_method,
    add column calculation_params json null comment '覆盖特征默认计算参数' after missing_value_strategy;
