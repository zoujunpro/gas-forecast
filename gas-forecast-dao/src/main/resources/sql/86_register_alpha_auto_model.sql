USE gas_data;

-- 注册 QuantaAlpha Auto 通用短期模型。模型平台中的编码、版本和能力参数
-- 必须与 gas_model_platform.models.alpha_model.handler 保持一致。
INSERT INTO model_config_tb (
    model_code,
    model_name,
    model_version,
    agent_code,
    time_granularity,
    min_forecast_horizon,
    max_forecast_horizon,
    minimum_training_rows,
    recommended_training_periods,
    default_training_range_mode,
    default_training_periods,
    training_data_requirement,
    description,
    created_by,
    created_by_name,
    created_at,
    updated_at
)
VALUES (
    'MODEL_ALPHA_AUTO_V1.0',
    'Alpha Auto 自动选模 V1',
    '1.0.0',
    'short-term',
    'DAY',
    1,
    90,
    30,
    90,
    'RECOMMENDED',
    NULL,
    '至少需要30天连续日销量，建议90天以上；日期必须唯一且连续，销量必须为非负数。',
    '面向短期全部行业和全部客户的通用自动选模模型；按时间划分训练、验证和独立测试集，执行清洗、候选模型选择、调参及回测。',
    'system',
    '系统',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON DUPLICATE KEY UPDATE
    model_name = VALUES(model_name),
    model_version = VALUES(model_version),
    agent_code = VALUES(agent_code),
    time_granularity = VALUES(time_granularity),
    min_forecast_horizon = VALUES(min_forecast_horizon),
    max_forecast_horizon = VALUES(max_forecast_horizon),
    minimum_training_rows = VALUES(minimum_training_rows),
    recommended_training_periods = VALUES(recommended_training_periods),
    default_training_range_mode = VALUES(default_training_range_mode),
    default_training_periods = VALUES(default_training_periods),
    training_data_requirement = VALUES(training_data_requirement),
    description = VALUES(description),
    updated_at = CURRENT_TIMESTAMP;

-- 该表没有范围记录即表示全部地区、全部行业、全部客户。使用通配语义可以让
-- 将来新增的行业无需再次修改模型配置，同时避免当前行业枚举被写死。
DELETE scope
FROM model_config_scope_tb AS scope
JOIN model_config_tb AS model ON model.id = scope.model_id
WHERE model.model_code = 'MODEL_ALPHA_AUTO_V1.0';
