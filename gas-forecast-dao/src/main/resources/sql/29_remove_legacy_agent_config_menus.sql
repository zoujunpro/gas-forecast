use gas_data;

delete from sys_role_permission_ref
where permission_id in (20, 21, 22, 23, 30, 31, 32, 33);

delete from sys_department_permission_ref
where permission_id in (20, 21, 22, 23, 30, 31, 32, 33);

delete from sys_permission_tb
where id in (21, 22, 23, 31, 32, 33, 20, 30);
