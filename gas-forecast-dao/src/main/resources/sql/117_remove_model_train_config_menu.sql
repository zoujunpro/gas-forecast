use gas_data;

-- 独立模型训练配置已下线，训练由统一预测流程触发。
delete from sys_role_permission_ref
where permission_id in (select id from sys_permission_tb where path = '/model/train-config');

delete from sys_department_permission_ref
where permission_id in (select id from sys_permission_tb where path = '/model/train-config');

delete from sys_permission_tb where path = '/model/train-config';
