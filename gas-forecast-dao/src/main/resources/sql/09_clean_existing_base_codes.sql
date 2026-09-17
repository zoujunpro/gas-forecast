use gas_data;

create table if not exists sys_code_sequence_tb (
    code_type varchar(32) not null,
    current_value bigint not null default 0,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    primary key (code_type)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='业务编码流水表';

insert into sys_code_sequence_tb (code_type, current_value) values
    ('REG', 0),
    ('IND', 0),
    ('CUS', 0),
    ('FIL', 0)
on duplicate key update code_type = values(code_type);

create temporary table tmp_region_code_map as
select region_code as old_code,
       concat('REG', lpad(
               (select coalesce(max(cast(substr(r2.region_code, 4) as unsigned)), 0)
                from base_region_tb r2
                where r2.region_code regexp '^REG[0-9]+$')
               + row_number() over (order by id),
               6,
               '0'
       )) as new_code
from base_region_tb
where region_code not regexp '^REG[0-9]+$';

create temporary table tmp_industry_code_map as
select industry_code as old_code,
       concat('IND', lpad(
               (select coalesce(max(cast(substr(i2.industry_code, 4) as unsigned)), 0)
                from base_industry_tb i2
                where i2.industry_code regexp '^IND[0-9]+$')
               + row_number() over (order by id),
               6,
               '0'
       )) as new_code
from base_industry_tb
where industry_code not regexp '^IND[0-9]+$';

create temporary table tmp_customer_code_map as
select customer_code as old_code,
       concat('CUS', lpad(
               (select coalesce(max(cast(substr(c2.customer_code, 4) as unsigned)), 0)
                from base_customer_tb c2
                where c2.customer_code regexp '^CUS[0-9]+$')
               + row_number() over (order by id),
               6,
               '0'
       )) as new_code
from base_customer_tb
where customer_code not regexp '^CUS[0-9]+$';

update base_customer_tb t join tmp_region_code_map m on t.region_code = m.old_code set t.region_code = m.new_code;
update data_monthly_sales_tb t join tmp_region_code_map m on t.region_code = m.old_code set t.region_code = m.new_code;
update data_daily_sales_tb t join tmp_region_code_map m on t.region_code = m.old_code set t.region_code = m.new_code;
update model_train_batch_tb t join tmp_region_code_map m on t.region_code = m.old_code set t.region_code = m.new_code;
update model_forecast_batch_tb t join tmp_region_code_map m on t.region_code = m.old_code set t.region_code = m.new_code;
update base_region_tb t join tmp_region_code_map m on t.region_code = m.old_code set t.region_code = m.new_code;

update base_customer_tb t join tmp_industry_code_map m on t.industry_code = m.old_code set t.industry_code = m.new_code;
update data_monthly_sales_tb t join tmp_industry_code_map m on t.industry_code = m.old_code set t.industry_code = m.new_code;
update data_daily_sales_tb t join tmp_industry_code_map m on t.industry_code = m.old_code set t.industry_code = m.new_code;
update model_train_batch_tb t join tmp_industry_code_map m on t.industry_code = m.old_code set t.industry_code = m.new_code;
update model_forecast_batch_tb t join tmp_industry_code_map m on t.industry_code = m.old_code set t.industry_code = m.new_code;
update base_industry_tb t join tmp_industry_code_map m on t.industry_code = m.old_code set t.industry_code = m.new_code;

update data_monthly_sales_tb t join tmp_customer_code_map m on t.customer_code = m.old_code set t.customer_code = m.new_code;
update data_daily_sales_tb t join tmp_customer_code_map m on t.customer_code = m.old_code set t.customer_code = m.new_code;
update model_train_batch_tb t join tmp_customer_code_map m on t.customer_code = m.old_code set t.customer_code = m.new_code;
update model_forecast_batch_tb t join tmp_customer_code_map m on t.customer_code = m.old_code set t.customer_code = m.new_code;
update base_customer_tb t join tmp_customer_code_map m on t.customer_code = m.old_code set t.customer_code = m.new_code;

update sys_code_sequence_tb
set current_value = coalesce((select max(cast(substr(region_code, 4) as unsigned)) from base_region_tb where region_code regexp '^REG[0-9]+$'), 0)
where code_type = 'REG';

update sys_code_sequence_tb
set current_value = coalesce((select max(cast(substr(industry_code, 4) as unsigned)) from base_industry_tb where industry_code regexp '^IND[0-9]+$'), 0)
where code_type = 'IND';

update sys_code_sequence_tb
set current_value = coalesce((select max(cast(substr(customer_code, 4) as unsigned)) from base_customer_tb where customer_code regexp '^CUS[0-9]+$'), 0)
where code_type = 'CUS';

drop temporary table if exists tmp_region_code_map;
drop temporary table if exists tmp_industry_code_map;
drop temporary table if exists tmp_customer_code_map;
