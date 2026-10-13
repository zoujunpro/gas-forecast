-- 同步 Python 模型平台的新模型编号，并保留既有模型ID及训练配置关联。
update model_config_tb
set model_code = 'MODEL_POWER_GENERATION_V1.0',
    model_name = '发电预测模型 V1',
    model_version = '1.0.0',
    description = '支持范围：发电行业，支持任意地区和任意客户；采用 Prophet 与 LightGBM 残差融合，并使用 Walk-Forward 回测选择参数。',
    updated_at = now()
where model_code = 'MODEL_JIANGSHU_DIANLI_V1.0';

update model_config_scope_tb scope
join model_config_tb model on scope.model_id = model.id
set scope.model_code = model.model_code
where model.model_code = 'MODEL_POWER_GENERATION_V1.0';

update model_train_config_tb train
join model_config_tb model on train.model_id = model.id
set train.model_code = model.model_code,
    train.model_name = model.model_name,
    train.updated_at = now()
where model.model_code = 'MODEL_POWER_GENERATION_V1.0';

insert into model_config_tb
    (model_code, model_name, model_version, agent_code, description, created_by, created_by_name, created_at, updated_at)
select
    'MODEL_CITY_GAS_V1.0',
    '城市燃气预测模型 V1',
    '1.0.0',
    'short-term',
    '支持范围：城市燃气行业，支持任意地区和任意客户；完整保留城燃 v3 的 Prophet、LightGBM、AR 备用、节假日、天气特征和滚动回测逻辑。',
    'system',
    '系统',
    now(),
    now()
where not exists (
    select 1 from model_config_tb where model_code = 'MODEL_CITY_GAS_V1.0'
);
