-- 标准天气特征只保留 avg_temp/max_temp/min_temp；模型接口旧字段名通过关联表别名输出。
ALTER TABLE model_feature_ref
    ADD COLUMN output_feature_code varchar(32) NULL COMMENT '模型接口输出特征编码，为空使用标准特征编码' AFTER feature_id;

-- 同一模型同时引用 avg_temp 和 temp 时只保留模型接口使用的 temp 映射。
DELETE standard_ref
FROM model_feature_ref standard_ref
JOIN model_feature_ref legacy_ref
  ON legacy_ref.model_id = standard_ref.model_id AND legacy_ref.feature_id = 8
WHERE standard_ref.feature_id = 1;

UPDATE model_feature_ref SET feature_id = 2, output_feature_code = 'tempmax' WHERE feature_id = 6;
UPDATE model_feature_ref SET feature_id = 3, output_feature_code = 'tempmin' WHERE feature_id = 7;
UPDATE model_feature_ref SET feature_id = 1, output_feature_code = 'temp' WHERE feature_id = 8;

UPDATE model_feature_ref ref
JOIN model_feature_definition_tb feature ON feature.id = ref.feature_id
SET ref.output_feature_code = feature.feature_code
WHERE ref.output_feature_code IS NULL OR ref.output_feature_code = '';

DELETE FROM model_feature_definition_tb WHERE id IN (6, 7, 8);
