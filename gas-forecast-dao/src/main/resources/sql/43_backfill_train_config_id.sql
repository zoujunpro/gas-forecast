use gas_data;

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
