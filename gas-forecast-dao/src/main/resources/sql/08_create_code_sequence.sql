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
    ('FIL', 0),
    ('MCF', 0)
on duplicate key update code_type = values(code_type);

alter table data_file_info_tb add column file_code varchar(64) null comment '文件编码' after id;

update data_file_info_tb
set file_code = concat('FIL', lpad(id, 6, '0'))
where file_code is null or file_code = '';

alter table data_file_info_tb modify file_code varchar(64) not null comment '文件编码';

update sys_code_sequence_tb
set current_value = greatest(
    current_value,
    coalesce((select max(cast(substr(region_code, 4) as unsigned)) from base_region_tb where region_code regexp '^REG[0-9]+$'), 0)
)
where code_type = 'REG';

update sys_code_sequence_tb
set current_value = greatest(
    current_value,
    coalesce((select max(cast(substr(industry_code, 4) as unsigned)) from base_industry_tb where industry_code regexp '^IND[0-9]+$'), 0)
)
where code_type = 'IND';

update sys_code_sequence_tb
set current_value = greatest(
    current_value,
    coalesce((select max(cast(substr(customer_code, 4) as unsigned)) from base_customer_tb where customer_code regexp '^CUS[0-9]+$'), 0)
)
where code_type = 'CUS';

update sys_code_sequence_tb
set current_value = greatest(
    current_value,
    coalesce((select max(cast(substr(file_code, 4) as unsigned)) from data_file_info_tb where file_code regexp '^FIL[0-9]+$'), 0)
)
where code_type = 'FIL';

update sys_code_sequence_tb
set current_value = greatest(
    current_value,
    coalesce((select max(cast(substr(config_code, 4) as unsigned)) from model_config_tb where config_code regexp '^MCF[0-9]+$'), 0)
)
where code_type = 'MCF';

alter table base_region_tb add unique key uk_base_region_code (region_code);
alter table base_industry_tb add unique key uk_base_industry_code (industry_code);
alter table base_customer_tb add unique key uk_base_customer_code (customer_code);
alter table data_file_info_tb add unique key uk_data_file_info_code (file_code);
