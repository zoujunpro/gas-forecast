USE gas_data;

UPDATE model_train_config_tb c
JOIN model_config_tb m ON m.id = c.model_id
SET c.customer_code = NULL,
    c.customer_name = NULL,
    c.train_name = CONCAT_WS('-', c.region_name, c.industry_name, '训练'),
    c.updated_at = NOW()
WHERE m.agent_code = 'short-term'
  AND m.training_scope_type = 'REGION_INDUSTRY';
