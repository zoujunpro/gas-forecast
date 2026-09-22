use gas_data;

alter table model_train_record_tb
    add column train_duration_seconds decimal(12, 3) null comment '训练耗时（秒）' after r2;

update model_train_record_tb
set train_duration_seconds = coalesce(
    cast(json_unquote(json_extract(result_json, '$.elapsed_seconds')) as decimal(12, 3)),
    cast(json_unquote(json_extract(result_json, '$.elapsedSeconds')) as decimal(12, 3)),
    cast(json_unquote(json_extract(result_json, '$.train_duration_seconds')) as decimal(12, 3)),
    cast(json_unquote(json_extract(result_json, '$.trainDurationSeconds')) as decimal(12, 3)),
    cast(json_unquote(json_extract(result_json, '$.duration_seconds')) as decimal(12, 3)),
    cast(json_unquote(json_extract(result_json, '$.durationSeconds')) as decimal(12, 3)),
    cast(json_unquote(json_extract(result_json, '$.metadata.elapsed_seconds')) as decimal(12, 3)),
    cast(json_unquote(json_extract(result_json, '$.metadata.elapsedSeconds')) as decimal(12, 3))
)
where train_duration_seconds is null
  and result_json is not null
  and json_valid(result_json);
