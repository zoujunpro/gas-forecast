use gas_data;

-- 训练配置编码与训练执行批次号使用不同前缀，避免 TRAIN/T 混用造成歧义。
-- 训练配置编码：TRCFG-00001
-- 训练执行批次号：TRBATCH-20260920-00001

insert into sys_code_sequence_tb (code_type, current_value) values
    ('TRCFG-', 0),
    ('TRBATCH-', 0)
on duplicate key update code_type = values(code_type);

drop temporary table if exists tmp_train_config_code_map;
create temporary table tmp_train_config_code_map as
select
    id,
    train_code as old_train_code,
    concat('TRCFG-', lpad(row_number() over (order by id), 5, '0')) as new_train_code
from model_train_config_tb;

update model_train_config_tb config
join tmp_train_config_code_map code_map on config.id = code_map.id
set config.train_code = concat('__TMP_TRCFG_', config.id);

update model_train_config_tb config
join tmp_train_config_code_map code_map on config.id = code_map.id
set config.train_code = code_map.new_train_code;

drop temporary table if exists tmp_train_batch_code_map;
create temporary table tmp_train_batch_code_map as
select
    id,
    batch_no as old_batch_no,
    concat(
        'TRBATCH-',
        date_format(coalesce(created_at, updated_at, now()), '%Y%m%d'),
        '-',
        lpad(row_number() over (order by coalesce(created_at, updated_at), id), 5, '0')
    ) as new_batch_no
from model_train_record_tb
where batch_no is not null
  and batch_no not regexp '^(WGTRAIN-|XQTRAIN-)';

update model_train_backtest_tb backtest
join tmp_train_batch_code_map code_map on backtest.train_batch_no = code_map.old_batch_no
set backtest.train_batch_no = code_map.new_batch_no;

update model_train_record_tb detail
join tmp_train_batch_code_map code_map on detail.id = code_map.id
set detail.batch_no = code_map.new_batch_no;

update sys_code_sequence_tb
set current_value = greatest(
    current_value,
    coalesce((select max(cast(substr(train_code, 7) as unsigned)) from model_train_config_tb where train_code regexp '^TRCFG-[0-9]{5}$'), 0)
)
where code_type = 'TRCFG-';

update sys_code_sequence_tb
set current_value = greatest(
    current_value,
    coalesce((select max(cast(substring_index(batch_no, '-', -1) as unsigned)) from model_train_record_tb where batch_no regexp '^TRBATCH-[0-9]{8}-[0-9]{5}$'), 0)
)
where code_type = 'TRBATCH-';

delete from sys_code_sequence_tb where code_type in ('TRAIN-', 'T');
