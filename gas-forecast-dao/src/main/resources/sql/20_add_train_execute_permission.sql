use gas_data;

insert ignore into sys_permission_tb (id, parent_id, permission_name, path, component, permission_type, perms, icon, sort_no, hidden, status) values
    (3101, 31, '执行训练', null, null, 'BUTTON', 'config:train:execute', null, 3101, 1, 1),
    (3201, 32, '执行训练', null, null, 'BUTTON', 'config:train:execute', null, 3201, 1, 1),
    (3301, 33, '执行训练', null, null, 'BUTTON', 'config:train:execute', null, 3301, 1, 1);

insert ignore into sys_role_permission_ref (role_id, permission_id)
select r.id, p.id
from sys_role_tb r
join sys_permission_tb p
where r.role_code = 'admin'
  and p.id in (3101, 3201, 3301);
