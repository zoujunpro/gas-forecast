use gas_data;

create temporary table tmp_region_code_fix (
    old_code varchar(64) not null,
    new_code varchar(64) not null,
    region_name varchar(64) not null,
    primary key (old_code)
);

insert into tmp_region_code_fix (old_code, new_code, region_name) values
    ('40358db7-c191-526b-ab53-77a9ea7c0c7d', 'REG000005', '北京'),
    ('b774086f-36f2-5ce7-b12a-d4c6ea3d7950', 'REG000006', '天津'),
    ('294aa550-2b81-50ff-9b51-62d49506d693', 'REG000007', '山东'),
    ('c87e0076-8dd2-5529-a791-a4e6cead38f1', 'REG000008', '山西'),
    ('3f040f4b-9c2a-53a9-a518-b605292612f0', 'REG000009', '河北'),
    ('f8275a1c-a26f-57c3-91d8-6eb41610369e', 'REG000010', '河南'),
    ('a35a1225-95bf-5255-aff3-950649a45c9c', 'REG000011', '陕西');

insert into base_region_tb (
    region_code,
    region_name,
    remark,
    created_by,
    created_at,
    updated_by,
    updated_at,
    updated_by_name,
    created_by_name
)
select new_code,
       region_name,
       'winner-agent模拟省份',
       'zoujun',
       now(),
       'zoujun',
       now(),
       '邹军',
       '邹军'
from tmp_region_code_fix
on duplicate key update
    region_name = values(region_name),
    remark = values(remark),
    updated_by = values(updated_by),
    updated_by_name = values(updated_by_name),
    updated_at = values(updated_at);

update base_customer_tb t join tmp_region_code_fix m on t.region_code = m.old_code set t.region_code = m.new_code;
update data_monthly_sales_tb t join tmp_region_code_fix m on t.region_code = m.old_code set t.region_code = m.new_code;
update data_daily_sales_tb t join tmp_region_code_fix m on t.region_code = m.old_code set t.region_code = m.new_code;
update model_train_feature_data_tb t join tmp_region_code_fix m on t.region_code = m.old_code set t.region_code = m.new_code;
update model_train_batch_tb t join tmp_region_code_fix m on t.region_code = m.old_code set t.region_code = m.new_code;
update model_forecast_batch_tb t join tmp_region_code_fix m on t.region_code = m.old_code set t.region_code = m.new_code;
update model_config_scope_tb t
join tmp_region_code_fix m on t.region_code = m.old_code collate utf8mb4_0900_ai_ci
set t.region_code = m.new_code;

delete r
from base_region_tb r
join tmp_region_code_fix m on r.region_code = m.old_code;

delete r
from base_region_tb r
join base_region_tb kept
  on kept.region_name = r.region_name
 and kept.region_code regexp '^REG[0-9]{6}$'
 and r.region_code not regexp '^REG[0-9]{6}$'
where r.region_name in ('北京', '天津', '山东', '山西', '河北', '河南', '陕西');

update sys_code_sequence_tb
set current_value = greatest(
    current_value,
    coalesce((select max(cast(substr(region_code, 4) as unsigned)) from base_region_tb where region_code regexp '^REG[0-9]{6}$'), 0)
)
where code_type = 'REG';

drop temporary table if exists tmp_region_code_fix;
