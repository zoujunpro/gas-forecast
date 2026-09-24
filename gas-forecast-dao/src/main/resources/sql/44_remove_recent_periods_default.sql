use gas_data;

alter table model_train_config_tb
    modify column recent_periods int default null comment '最近周期数';
