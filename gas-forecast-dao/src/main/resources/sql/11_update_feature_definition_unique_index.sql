use gas_data;

alter table model_feature_definition_tb
    drop column feature_alias;

alter table model_feature_definition_tb
    add unique key uk_feature_code_granularity (feature_code, time_granularity);
