use gas_data;

-- 同一智能体、模型、业务范围和时间粒度只保留一条训练配置。
-- 优先保留存在成功训练批次的配置，其次保留最近更新的配置。
create temporary table tmp_train_config_merge as
select id as old_id,
       first_value(id) over (
           partition by agent_code, model_id, ifnull(region_code, ''), ifnull(industry_code, ''),
                        ifnull(customer_code, ''), time_granularity
           order by exists(
               select 1 from model_train_record_tb record
               where record.train_config_id = model_train_config_tb.id and record.status = 'SUCCESS'
           ) desc, updated_at desc, id desc
       ) as keep_id
from model_train_config_tb;

update model_train_record_tb record
join tmp_train_config_merge merge_config on merge_config.old_id = record.train_config_id
set record.train_config_id = merge_config.keep_id
where merge_config.old_id <> merge_config.keep_id;

update model_forecast_config_tb forecast_config
join tmp_train_config_merge merge_config on merge_config.old_id = forecast_config.train_config_id
join model_train_config_tb keep_config on keep_config.id = merge_config.keep_id
set forecast_config.train_config_id = merge_config.keep_id,
    forecast_config.train_config_code = keep_config.train_code
where merge_config.old_id <> merge_config.keep_id;

delete duplicate_config
from model_train_config_tb duplicate_config
join tmp_train_config_merge merge_config on merge_config.old_id = duplicate_config.id
where merge_config.old_id <> merge_config.keep_id;

drop temporary table tmp_train_config_merge;

-- 生成列用于把 NULL 统一映射为空字符串，避免 MySQL 唯一索引允许多个 NULL。
alter table model_train_config_tb
    add column region_scope_key varchar(64)
        generated always as (ifnull(region_code, '')) stored comment '唯一键地区标准值',
    add column industry_scope_key varchar(64)
        generated always as (ifnull(industry_code, '')) stored comment '唯一键行业标准值',
    add column customer_scope_key varchar(64)
        generated always as (ifnull(customer_code, '')) stored comment '唯一键客户标准值',
    add unique key uk_train_config_business_scope (
        agent_code, model_id, region_scope_key, industry_scope_key, customer_scope_key, time_granularity
    );
