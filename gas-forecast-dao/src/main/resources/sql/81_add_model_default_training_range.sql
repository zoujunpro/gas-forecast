use gas_data;

alter table model_config_tb
    add column default_training_range_mode varchar(16) not null default 'RECOMMENDED'
        comment '默认训练范围策略：RECOMMENDED推荐/CUSTOM自定义/ALL全部历史'
        after recommended_training_periods;

alter table model_config_tb
    add column default_training_periods int null
        comment 'CUSTOM策略下默认训练周期数'
        after default_training_range_mode;

update model_config_tb
set default_training_range_mode = 'RECOMMENDED'
where default_training_range_mode is null or default_training_range_mode = '';
