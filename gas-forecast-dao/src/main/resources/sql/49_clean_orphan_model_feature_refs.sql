-- 清理特征定义已删除后遗留的模型特征关联，避免配置页面重复提交无效ID。
delete ref
from model_feature_ref ref
left join model_feature_definition_tb feature on feature.id = ref.feature_id
where feature.id is null;
