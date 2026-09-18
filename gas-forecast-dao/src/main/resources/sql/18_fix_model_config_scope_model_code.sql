use gas_data;

insert into sys_code_sequence_tb (code_type, current_value) values ('MODEL', 0)
on duplicate key update code_type = values(code_type);

update model_config_tb
set model_code = concat('MODEL', substr(model_code, 4))
where model_code regexp '^MCF[0-9]{6}$';

alter table model_config_scope_tb
    modify model_code varchar(64) not null comment '模型配置编码';

update model_config_scope_tb scope
join model_config_tb config
  on scope.model_code collate utf8mb4_0900_ai_ci = cast(config.id as char)
set scope.model_code = config.model_code
where scope.model_code regexp '^[0-9]+$';

update model_config_scope_tb
set model_code = concat('MODEL', substr(model_code, 4))
where model_code regexp '^MCF[0-9]{6}$';

update sys_code_sequence_tb
set current_value = greatest(
    current_value,
    coalesce((select max(cast(substr(model_code, 6) as unsigned)) from model_config_tb where model_code regexp '^MODEL[0-9]{6}$'), 0)
)
where code_type = 'MODEL';

delete from sys_code_sequence_tb where code_type = 'MCF';
