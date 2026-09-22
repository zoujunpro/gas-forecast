use gas_data;

update model_forecast_result_tb result
join model_forecast_record_tb record
  on result.forecast_batch_no = cast(record.forecast_batch_no as char)
  or result.forecast_batch_no = concat('F', lpad(record.forecast_batch_no, 5, '0'))
set result.forecast_batch_no = concat(
    'forecast-', date_format(record.created_at, '%Y%m%d'), '-', lpad(record.id, 6, '0')
);

alter table model_forecast_record_tb
    modify column forecast_batch_no varchar(32) null comment '预测批次号';

update model_forecast_record_tb
set forecast_batch_no = concat(
    'forecast-', date_format(created_at, '%Y%m%d'), '-', lpad(id, 6, '0')
)
where forecast_batch_no is null
   or forecast_batch_no not like 'forecast-%';

set @batch_index_exists = (
    select count(*)
    from information_schema.statistics
    where table_schema = database()
      and table_name = 'model_forecast_record_tb'
      and index_name = 'uk_forecast_record_batch_no'
);
set @add_batch_index_sql = if(
    @batch_index_exists = 0,
    'alter table model_forecast_record_tb add unique key uk_forecast_record_batch_no (forecast_batch_no)',
    'select 1'
);
prepare stmt from @add_batch_index_sql;
execute stmt;
deallocate prepare stmt;
