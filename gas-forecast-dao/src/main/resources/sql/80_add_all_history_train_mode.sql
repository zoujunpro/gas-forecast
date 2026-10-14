use gas_data;

alter table model_train_config_tb
    modify column train_mode varchar(32) not null default 'RECENT'
        comment '训练方式：ALL全部历史/RECENT最近时间/RANGE指定时间范围';
