use gas_data;

insert ignore into sys_permission_tb
    (id, parent_id, permission_name, path, component, permission_type, perms, icon, sort_no, hidden, status)
values
    (6404, 64, '执行模型训练', null, null, 'BUTTON', 'config:train:execute', null, 6404, 1, 1);

insert ignore into sys_role_permission_ref (role_id, permission_id)
select r.id, 6404
from sys_role_tb r
where r.role_code = 'admin';
