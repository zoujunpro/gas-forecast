use gas_data;

set @drop_lower = if(
    exists(select 1 from information_schema.columns where table_schema = database()
           and table_name = 'model_forecast_result_tb' and column_name = 'lower_value'),
    'alter table model_forecast_result_tb drop column lower_value', 'select 1');
prepare stmt from @drop_lower;
execute stmt;
deallocate prepare stmt;

set @drop_upper = if(
    exists(select 1 from information_schema.columns where table_schema = database()
           and table_name = 'model_forecast_result_tb' and column_name = 'upper_value'),
    'alter table model_forecast_result_tb drop column upper_value', 'select 1');
prepare stmt from @drop_upper;
execute stmt;
deallocate prepare stmt;

set @drop_extra = if(
    exists(select 1 from information_schema.columns where table_schema = database()
           and table_name = 'model_forecast_result_tb' and column_name = 'extra_json'),
    'alter table model_forecast_result_tb drop column extra_json', 'select 1');
prepare stmt from @drop_extra;
execute stmt;
deallocate prepare stmt;
