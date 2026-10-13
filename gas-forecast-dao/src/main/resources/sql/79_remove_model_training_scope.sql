USE gas_data;

-- 训练汇总范围由训练配置中的客户/行业/地区目标自动决定，不属于模型能力配置。
ALTER TABLE model_config_tb
    DROP COLUMN training_scope_type;

-- 短期预测恢复为按客户独立训练，行业/月度等智能体仍由各自策略决定训练主体。
UPDATE agent_strategy_config_tb
SET train_scope_type = 'CUSTOMER',
    updated_at = CURRENT_TIMESTAMP
WHERE agent_code = 'short-term';

-- 历史短期行业级配置不能再用于客户预测；先停用预测配置，再停用对应训练配置。
UPDATE model_forecast_config_tb forecast_config
JOIN model_train_config_tb train_config ON train_config.id = forecast_config.train_config_id
SET forecast_config.enabled = 0,
    forecast_config.updated_at = CURRENT_TIMESTAMP
WHERE train_config.agent_code = 'short-term'
  AND (train_config.customer_code IS NULL OR TRIM(train_config.customer_code) = '' OR UPPER(TRIM(train_config.customer_code)) = 'ALL');

UPDATE model_train_config_tb
SET enabled = 0,
    updated_at = CURRENT_TIMESTAMP
WHERE agent_code = 'short-term'
  AND (customer_code IS NULL OR TRIM(customer_code) = '' OR UPPER(TRIM(customer_code)) = 'ALL');
