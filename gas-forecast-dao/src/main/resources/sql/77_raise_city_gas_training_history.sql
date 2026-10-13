UPDATE model_config_tb
SET minimum_training_rows = 365,
    recommended_training_periods = 730,
    training_data_requirement = '至少365条连续日级数据并覆盖完整年度，推荐730条；回测MAPE超过100%或预测出现负值时不得作为可用模型',
    updated_at = NOW()
WHERE model_code = 'MODEL_CITY_GAS_V1.0';

UPDATE model_train_config_tb
SET recent_periods = 730,
    remark = '城市燃气模型按最近730天训练，最少要求365条连续日级数据',
    updated_at = NOW()
WHERE model_code = 'MODEL_CITY_GAS_V1.0' AND train_mode = 'RECENT';
