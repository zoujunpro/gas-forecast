USE gas_data;

UPDATE model_feature_definition_tb
SET missing_value_strategy = 'IGNORE'
WHERE missing_value_strategy = 'ERROR';

ALTER TABLE model_feature_definition_tb
    MODIFY COLUMN missing_value_strategy varchar(16) NOT NULL DEFAULT 'IGNORE'
        COMMENT 'ERROR/IGNORE/ZERO/FORWARD_FILL/INTERPOLATE';
