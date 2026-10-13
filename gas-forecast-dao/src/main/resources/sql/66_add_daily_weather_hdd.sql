use gas_data;

alter table data_region_weather_daily_tb
    add column hdd decimal(10,4) null comment '采暖度日，默认基准温度18摄氏度' after uv_index;

update data_region_weather_daily_tb
set hdd = greatest(18 - temp_avg, 0)
where temp_avg is not null and hdd is null;

-- 旬级HDD直接汇总日级天气表中的HDD。
update model_feature_definition_tb
set value_source = 'WEATHER_DAILY',
    source_field = 'hdd',
    calculation_type = null,
    calculation_params = null,
    aggregation_method = 'SUM',
    description = '由日级天气HDD求和得到；日级HDD默认按max(18-日平均温度,0)计算'
where feature_code = 'hdd' and time_granularity = 'TENDAY';
