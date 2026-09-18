alter table model_train_config_tb
    add column train_mode varchar(32) not null default 'RECENT' comment '训练方式：RECENT最近时间/RANGE指定时间范围' after train_end_date,
    add column time_granularity varchar(32) not null default 'MONTH' comment '时间格式：DAY日/TENDAY旬/MONTH月' after train_mode,
    add column recent_periods int default 36 comment '最近周期数' after time_granularity;
