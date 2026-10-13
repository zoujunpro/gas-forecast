use gas_data;

alter table model_config_tb
    add column time_granularity varchar(16) not null default 'DAY' comment '支持的时间粒度：DAY/TENDAY/MONTH' after agent_code,
    add column min_forecast_horizon int not null default 1 comment '最小预测周期数' after time_granularity,
    add column max_forecast_horizon int not null default 30 comment '最大预测周期数' after min_forecast_horizon,
    add column minimum_training_rows int not null default 140 comment '最短训练数据量' after max_forecast_horizon,
    add column training_data_requirement varchar(500) default null comment '训练数据要求说明' after minimum_training_rows;

update model_config_tb
set time_granularity = case agent_code
        when 'monthly-sales' then 'MONTH'
        when 'winter-supply' then 'TENDAY'
        else 'DAY'
    end,
    min_forecast_horizon = 1,
    max_forecast_horizon = case agent_code
        when 'monthly-sales' then 24
        when 'winter-supply' then 18
        else 90
    end,
    minimum_training_rows = case agent_code
        when 'monthly-sales' then 24
        when 'winter-supply' then 18
        else 140
    end
where id is not null;
