use gas_data;

alter table model_train_record_tb
    add column train_config_id bigint unsigned null comment '关联训练配置ID' after id,
    add index idx_train_record_config_id (train_config_id);

alter table model_forecast_config_tb
    add column train_config_id bigint unsigned null comment '关联训练配置ID' after auto_forecast,
    add index idx_forecast_config_train_id (train_config_id);

update model_forecast_config_tb forecast_config
join model_train_config_tb train_config
    on train_config.train_code = forecast_config.train_config_code
set forecast_config.train_config_id = train_config.id
where forecast_config.train_config_id is null;

update model_train_record_tb train_record
set train_record.train_config_id = (
    select min(train_config.id)
    from model_train_config_tb train_config
    where train_config.agent_code collate utf8mb4_unicode_ci = train_record.agent_code collate utf8mb4_unicode_ci
      and coalesce(nullif(train_config.region_code, ''), 'ALL') collate utf8mb4_unicode_ci = coalesce(nullif(train_record.region_code, ''), 'ALL') collate utf8mb4_unicode_ci
      and coalesce(nullif(train_config.customer_code, ''), 'ALL') collate utf8mb4_unicode_ci = coalesce(nullif(train_record.customer_code, ''), 'ALL') collate utf8mb4_unicode_ci
      and coalesce(nullif(train_config.industry_code, ''), 'ALL') collate utf8mb4_unicode_ci = coalesce(nullif(train_record.industry_code, ''), 'ALL') collate utf8mb4_unicode_ci
)
where train_record.train_config_id is null
  and 1 = (
      select count(*)
      from model_train_config_tb train_config
      where train_config.agent_code collate utf8mb4_unicode_ci = train_record.agent_code collate utf8mb4_unicode_ci
        and coalesce(nullif(train_config.region_code, ''), 'ALL') collate utf8mb4_unicode_ci = coalesce(nullif(train_record.region_code, ''), 'ALL') collate utf8mb4_unicode_ci
        and coalesce(nullif(train_config.customer_code, ''), 'ALL') collate utf8mb4_unicode_ci = coalesce(nullif(train_record.customer_code, ''), 'ALL') collate utf8mb4_unicode_ci
        and coalesce(nullif(train_config.industry_code, ''), 'ALL') collate utf8mb4_unicode_ci = coalesce(nullif(train_record.industry_code, ''), 'ALL') collate utf8mb4_unicode_ci
  );
