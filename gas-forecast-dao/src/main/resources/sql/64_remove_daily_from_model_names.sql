-- 时间粒度已在独立字段展示，模型名称不再重复包含“日级”。
update model_config_tb
set model_name = replace(model_name, '日级', ''),
    updated_at = now()
where model_code in (
    'MODEL_POWER_GENERATION_V1.0',
    'MODEL_CITY_GAS_V1.0',
    'MODEL_CNG_V1.0',
    'MODEL_CHEMICAL_GAS_V1.0',
    'MODEL_INDUSTRIAL_FUEL_V1.0'
) and model_name like '%日级%';

update model_train_config_tb train_config
join model_config_tb model_config on model_config.id = train_config.model_id
set train_config.model_name = model_config.model_name,
    train_config.updated_at = now()
where model_config.model_code in (
    'MODEL_POWER_GENERATION_V1.0',
    'MODEL_CITY_GAS_V1.0',
    'MODEL_CNG_V1.0',
    'MODEL_CHEMICAL_GAS_V1.0',
    'MODEL_INDUSTRIAL_FUEL_V1.0'
);
