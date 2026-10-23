USE gas_data;

ALTER TABLE model_config_tb
    ADD COLUMN enabled TINYINT NOT NULL DEFAULT 1
        COMMENT '是否启用：1启用，0停用'
        AFTER agent_code;

CREATE INDEX idx_model_config_agent_enabled
    ON model_config_tb (agent_code, enabled);

UPDATE model_config_tb
SET enabled = 1
WHERE enabled IS NULL;
