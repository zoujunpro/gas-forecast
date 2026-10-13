USE gas_data;

UPDATE model_config_tb
SET training_scope_type = 'REGION_INDUSTRY'
WHERE agent_code = 'short-term';

UPDATE model_config_tb
SET training_scope_type = 'REGION_INDUSTRY'
WHERE training_scope_type = 'INDUSTRY';

UPDATE agent_strategy_config_tb
SET train_scope_type = 'REGION_INDUSTRY'
WHERE agent_code = 'short-term';
