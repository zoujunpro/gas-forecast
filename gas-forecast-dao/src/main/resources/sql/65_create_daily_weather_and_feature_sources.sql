use gas_data;

create table if not exists data_region_weather_daily_tb (
    id bigint not null auto_increment comment '主键',
    region_code varchar(64) not null comment '地区编码',
    region_name varchar(64) not null comment '地区名称',
    stat_date date not null comment '天气日期',
    weather_type varchar(16) not null default 'ACTUAL' comment 'ACTUAL实际天气/FORECAST预报天气',
    weather_version varchar(32) not null default 'ACTUAL' comment '天气版本',
    forecast_issue_time datetime null comment '预报发布时间',
    temp_max decimal(10,4) null comment '最高气温',
    temp_min decimal(10,4) null comment '最低气温',
    temp_avg decimal(10,4) null comment '平均气温',
    feels_like_max decimal(10,4) null comment '最高体感温度',
    feels_like_min decimal(10,4) null comment '最低体感温度',
    feels_like_avg decimal(10,4) null comment '平均体感温度',
    dew_point decimal(10,4) null comment '露点温度',
    humidity decimal(10,4) null comment '湿度',
    precipitation decimal(12,4) null comment '降水量',
    wind_gust decimal(10,4) null comment '阵风风速',
    wind_speed decimal(10,4) null comment '风速',
    sea_level_pressure decimal(12,4) null comment '海平面气压',
    cloud_cover decimal(10,4) null comment '云量',
    visibility decimal(10,4) null comment '能见度',
    solar_radiation decimal(14,4) null comment '太阳辐射',
    solar_energy decimal(14,4) null comment '太阳能量',
    uv_index decimal(10,4) null comment '紫外线指数',
    source_code varchar(32) not null default 'MANUAL' comment '数据来源',
    quality_status varchar(16) not null default 'VALID' comment 'VALID/MISSING/ABNORMAL',
    created_at datetime not null default current_timestamp,
    updated_at datetime not null default current_timestamp on update current_timestamp,
    primary key (id),
    unique key uk_region_weather_version (region_code, stat_date, weather_type, weather_version, source_code),
    key idx_region_weather_query (region_code, weather_type, stat_date)
) comment='省级日度天气表';

alter table model_feature_definition_tb
    add column value_source varchar(32) not null default 'LEGACY_WIDE_TABLE' comment 'WEATHER_DAILY/SALES_DAILY/FEATURE_VALUE/CALCULATED/MODEL_INTERNAL/LEGACY_WIDE_TABLE' after source_type,
    add column source_field varchar(64) null comment '来源字段' after value_source,
    add column calculation_type varchar(32) null comment 'HDD/CDD/EXTREME_COLD_DAYS/CALENDAR等' after source_field,
    add column calculation_params json null comment '计算参数' after calculation_type,
    add column aggregation_method varchar(16) null comment 'AVG/SUM/MAX/MIN/COUNT/LAST' after calculation_params,
    add column missing_value_strategy varchar(16) not null default 'ERROR' comment 'ERROR/IGNORE/ZERO/FORWARD_FILL/INTERPOLATE' after aggregation_method;

update model_feature_definition_tb set value_source='WEATHER_DAILY', source_field='temp_max', aggregation_method='MAX' where feature_code in ('tempmax','max_temp');
update model_feature_definition_tb set value_source='WEATHER_DAILY', source_field='temp_min', aggregation_method='MIN' where feature_code in ('tempmin','min_temp');
update model_feature_definition_tb set value_source='WEATHER_DAILY', source_field='temp_avg', aggregation_method='AVG' where feature_code in ('temp','avg_temp');
update model_feature_definition_tb set value_source='WEATHER_DAILY', source_field='feels_like_max', aggregation_method='MAX' where feature_code='feelslikemax';
update model_feature_definition_tb set value_source='WEATHER_DAILY', source_field='feels_like_min', aggregation_method='MIN' where feature_code='feelslikemin';
update model_feature_definition_tb set value_source='WEATHER_DAILY', source_field='feels_like_avg', aggregation_method='AVG' where feature_code='feelslike';
update model_feature_definition_tb set value_source='WEATHER_DAILY', source_field='dew_point', aggregation_method='AVG' where feature_code='dew';
update model_feature_definition_tb set value_source='WEATHER_DAILY', source_field='humidity', aggregation_method='AVG' where feature_code='humidity';
update model_feature_definition_tb set value_source='WEATHER_DAILY', source_field='precipitation', aggregation_method='SUM' where feature_code='precip';
update model_feature_definition_tb set value_source='WEATHER_DAILY', source_field='wind_gust', aggregation_method='MAX' where feature_code='windgust';
update model_feature_definition_tb set value_source='WEATHER_DAILY', source_field='wind_speed', aggregation_method='AVG' where feature_code='windspeed';
update model_feature_definition_tb set value_source='WEATHER_DAILY', source_field='sea_level_pressure', aggregation_method='AVG' where feature_code='sealevelpressure';
update model_feature_definition_tb set value_source='WEATHER_DAILY', source_field='cloud_cover', aggregation_method='AVG' where feature_code='cloudcover';
update model_feature_definition_tb set value_source='WEATHER_DAILY', source_field='visibility', aggregation_method='AVG' where feature_code='visibility';
update model_feature_definition_tb set value_source='WEATHER_DAILY', source_field='solar_radiation', aggregation_method='AVG' where feature_code='solarradiation';
update model_feature_definition_tb set value_source='WEATHER_DAILY', source_field='solar_energy', aggregation_method='SUM' where feature_code='solarenergy';
update model_feature_definition_tb set value_source='WEATHER_DAILY', source_field='uv_index', aggregation_method='MAX' where feature_code='uvindex';
update model_feature_definition_tb set value_source='CALCULATED', source_field='temp_avg', calculation_type='HDD', calculation_params=json_object('base_temperature',18), aggregation_method='SUM' where feature_code='hdd';
update model_feature_definition_tb set value_source='CALCULATED', source_field='temp_min', calculation_type='EXTREME_COLD_DAYS', calculation_params=json_object('threshold',-10), aggregation_method='SUM' where feature_code='extreme_cold_days';

-- 旧训练宽表按客户重复保存了同一地区同一天的天气。迁移时按地区、日期聚合，
-- 每个地区每天只生成一条实际天气，避免客户数量放大天气数据量。
insert into data_region_weather_daily_tb (
    region_code, region_name, stat_date, weather_type, weather_version,
    temp_max, temp_min, temp_avg, feels_like_max, feels_like_min, feels_like_avg,
    dew_point, humidity, precipitation, wind_gust, wind_speed, sea_level_pressure,
    cloud_cover, visibility, solar_radiation, solar_energy, uv_index,
    source_code, quality_status
)
select region_code, max(region_name), str_to_date(stat_date, '%Y-%m-%d'), 'ACTUAL', 'ACTUAL',
       avg(feature_006), avg(feature_007), avg(feature_008), avg(feature_009), avg(feature_010), avg(feature_011),
       avg(feature_012), avg(feature_013), avg(feature_014), avg(feature_015), avg(feature_016), avg(feature_017),
       avg(feature_018), avg(feature_019), avg(feature_020), avg(feature_021), avg(feature_022),
       'LEGACY_FEATURE_DATA', 'VALID'
from model_train_feature_data_tb
where time_granularity = 'DAY'
  and region_code is not null
  and stat_date regexp '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'
  and coalesce(feature_006, feature_007, feature_008, feature_009, feature_010, feature_011,
               feature_012, feature_013, feature_014, feature_015, feature_016, feature_017,
               feature_018, feature_019, feature_020, feature_021, feature_022) is not null
group by region_code, stat_date
on duplicate key update
    region_name=values(region_name), temp_max=values(temp_max), temp_min=values(temp_min), temp_avg=values(temp_avg),
    feels_like_max=values(feels_like_max), feels_like_min=values(feels_like_min), feels_like_avg=values(feels_like_avg),
    dew_point=values(dew_point), humidity=values(humidity), precipitation=values(precipitation),
    wind_gust=values(wind_gust), wind_speed=values(wind_speed), sea_level_pressure=values(sea_level_pressure),
    cloud_cover=values(cloud_cover), visibility=values(visibility), solar_radiation=values(solar_radiation),
    solar_energy=values(solar_energy), uv_index=values(uv_index), updated_at=current_timestamp;

insert ignore into sys_permission_tb (id,parent_id,permission_name,path,component,permission_type,perms,icon,sort_no,hidden,status) values
    (46,40,'日级天气','/data/daily-weather','views/business/weather/DailyWeatherManagement.vue','MENU','data:daily-weather:list',null,46,0,1),
    (4601,46,'新增日级天气',null,null,'BUTTON','data:daily-weather:create',null,4601,1,1),
    (4602,46,'编辑日级天气',null,null,'BUTTON','data:daily-weather:update',null,4602,1,1),
    (4603,46,'删除日级天气',null,null,'BUTTON','data:daily-weather:delete',null,4603,1,1);

insert ignore into sys_role_permission_ref (role_id,permission_id)
select r.id,p.id from sys_role_tb r join sys_permission_tb p
where r.role_code='admin' and p.id in (46,4601,4602,4603);
