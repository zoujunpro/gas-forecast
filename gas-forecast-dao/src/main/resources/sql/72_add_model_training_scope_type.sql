USE gas_data;

ALTER TABLE model_config_tb
    ADD COLUMN training_scope_type varchar(16) NOT NULL DEFAULT 'REGION'
        COMMENT '训练数据汇总维度 REGION/INDUSTRY/CUSTOMER'
        AFTER time_granularity;
