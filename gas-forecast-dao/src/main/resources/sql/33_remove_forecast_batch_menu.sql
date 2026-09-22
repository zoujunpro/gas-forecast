use gas_data;

delete from sys_role_permission_ref where permission_id = 46;
delete from sys_department_permission_ref where permission_id = 46;
delete from sys_permission_tb where id = 46 or path = '/data/forecast-batches';
