use gas_data;

-- 一次预测（forecast_no）对应一批预测点；不同预测批次可以包含相同预测日期。
set @has_old_unique_key = (
    select count(*) from information_schema.statistics
    where table_schema = database()
      and table_name = 'model_forecast_result_tb'
      and index_name = 'uk_forecast_date'
);
set @drop_old_unique_key_sql = if(
    @has_old_unique_key > 0,
    'alter table model_forecast_result_tb drop index uk_forecast_date',
    'select 1'
);
prepare drop_old_unique_key_statement from @drop_old_unique_key_sql;
execute drop_old_unique_key_statement;
deallocate prepare drop_old_unique_key_statement;

set @has_batch_unique_key = (
    select count(*) from information_schema.statistics
    where table_schema = database()
      and table_name = 'model_forecast_result_tb'
      and index_name = 'uk_forecast_result_date'
);
set @add_batch_unique_key_sql = if(
    @has_batch_unique_key = 0,
    'alter table model_forecast_result_tb add unique key uk_forecast_result_date (forecast_no, forecast_date)',
    'select 1'
);
prepare add_batch_unique_key_statement from @add_batch_unique_key_sql;
execute add_batch_unique_key_statement;
deallocate prepare add_batch_unique_key_statement;
