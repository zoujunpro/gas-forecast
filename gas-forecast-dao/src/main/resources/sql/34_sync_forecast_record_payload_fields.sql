use gas_data;

set @drop_biz_config = if(
    exists(select 1 from information_schema.columns
           where table_schema = database() and table_name = 'model_forecast_record_tb' and column_name = 'biz_config_json'),
    'alter table model_forecast_record_tb drop column biz_config_json',
    'select 1'
);
prepare stmt from @drop_biz_config;
execute stmt;
deallocate prepare stmt;

set @add_feature_snapshot = if(
    not exists(select 1 from information_schema.columns
               where table_schema = database() and table_name = 'model_forecast_record_tb' and column_name = 'feature_snapshot'),
    'alter table model_forecast_record_tb add column feature_snapshot longblob null comment ''预测特征数据快照'' after updated_at',
    'select 1'
);
prepare stmt from @add_feature_snapshot;
execute stmt;
deallocate prepare stmt;

set @add_request_param = if(
    not exists(select 1 from information_schema.columns
               where table_schema = database() and table_name = 'model_forecast_record_tb' and column_name = 'request_param'),
    'alter table model_forecast_record_tb add column request_param longblob null comment ''模型预测请求参数'' after feature_snapshot',
    'select 1'
);
prepare stmt from @add_request_param;
execute stmt;
deallocate prepare stmt;

set @add_response_param = if(
    not exists(select 1 from information_schema.columns
               where table_schema = database() and table_name = 'model_forecast_record_tb' and column_name = 'response_param'),
    'alter table model_forecast_record_tb add column response_param longblob null comment ''模型预测响应参数'' after request_param',
    'select 1'
);
prepare stmt from @add_response_param;
execute stmt;
deallocate prepare stmt;
