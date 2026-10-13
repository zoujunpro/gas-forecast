use gas_data;

-- 同一智能体、地区、行业和客户只保留一条预测配置。
-- 预测开始日期、预测步长和时间颗粒度作为配置参数更新，不参与唯一性判断。
create temporary table tmp_forecast_config_merge as
select id as old_id,
       first_value(id) over (
           partition by agent_code, ifnull(nullif(region_code, ''), 'ALL'),
                        ifnull(nullif(industry_code, ''), 'ALL'), ifnull(nullif(customer_code, ''), 'ALL')
           order by updated_at desc, id desc
       ) as keep_id
from model_forecast_config_tb;

update model_forecast_record_tb record
join tmp_forecast_config_merge merge_config on merge_config.old_id = record.forecast_id
set record.forecast_id = merge_config.keep_id
where merge_config.old_id <> merge_config.keep_id;

delete duplicate_config
from model_forecast_config_tb duplicate_config
join tmp_forecast_config_merge merge_config on merge_config.old_id = duplicate_config.id
where merge_config.old_id <> merge_config.keep_id;

drop temporary table tmp_forecast_config_merge;

-- 生成列用于把 NULL/空字符串统一映射为 ALL，避免 MySQL 唯一索引允许多个 NULL。
alter table model_forecast_config_tb
    add column region_scope_key varchar(64)
        generated always as (ifnull(nullif(region_code, ''), 'ALL')) stored comment '唯一键地区标准值',
    add column industry_scope_key varchar(64)
        generated always as (ifnull(nullif(industry_code, ''), 'ALL')) stored comment '唯一键行业标准值',
    add column customer_scope_key varchar(64)
        generated always as (ifnull(nullif(customer_code, ''), 'ALL')) stored comment '唯一键客户标准值',
    add unique key uk_forecast_config_business_scope (
        agent_code, region_scope_key, industry_scope_key, customer_scope_key
    );
