use gas_data;

delete from sys_role_permission_ref where permission_id = 62;
delete from sys_department_permission_ref where permission_id = 62;
delete from sys_permission_tb where id = 62 or path = '/model/train-feature-data';

-- 训练数据现由日销量、日级天气和模型配置实时组装，不再持久化宽表。
drop table if exists model_train_feature_data_tb;
