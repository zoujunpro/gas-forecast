use gas_data;

-- 训练配置统一使用 train_code/train_name；config_code/config_name 为旧版遗留字段。
alter table model_train_config_tb
    drop column config_code,
    drop column config_name;
