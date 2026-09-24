use gas_data;

alter table model_train_config_tb
    drop index idx_train_config_scope,
    drop column scope_type,
    add key idx_train_config_scope (agent_code, region_code, customer_code, industry_code);
