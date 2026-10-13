use gas_data;

insert ignore into sys_permission_tb
(id, parent_id, permission_name, path, component, permission_type, perms, icon, sort_no, hidden, status)
values
(67, 60, '训练任务', '/model/train-task', 'views/model/ModelTrainTaskManagement.vue', 'MENU', 'model:train-task:list', null, 67, 0, 1);

insert ignore into sys_role_permission_ref (role_id, permission_id)
select r.id, 67 from sys_role_tb r where r.role_code = 'admin';
