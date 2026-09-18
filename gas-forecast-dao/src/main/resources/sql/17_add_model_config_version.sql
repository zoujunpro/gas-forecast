use gas_data;

alter table model_config_tb
    add column model_version varchar(32) not null default 'V1.0' comment '模型版本';

update model_config_tb
set model_version = 'V1.0'
where model_version is null or model_version = '';
