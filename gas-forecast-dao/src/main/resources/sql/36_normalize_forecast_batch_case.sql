use gas_data;

-- 预测批次号统一采用大写格式，并保持主记录与结果明细的关联键一致。
update model_forecast_result_tb
set forecast_batch_no = upper(forecast_batch_no)
where forecast_batch_no is not null
  and binary forecast_batch_no <> binary upper(forecast_batch_no);

update model_forecast_record_tb
set forecast_batch_no = upper(forecast_batch_no)
where forecast_batch_no is not null
  and binary forecast_batch_no <> binary upper(forecast_batch_no);
