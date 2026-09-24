use gas_data;

insert ignore into sys_permission_tb
    (id, parent_id, permission_name, path, component, permission_type, perms, icon, sort_no, hidden, status)
values
    (6501, 65, '新增预测配置', null, null, 'BUTTON', 'model:forecast:create', null, 6501, 1, 1),
    (6502, 65, '编辑预测配置', null, null, 'BUTTON', 'model:forecast:update', null, 6502, 1, 1),
    (6503, 65, '删除预测配置', null, null, 'BUTTON', 'model:forecast:delete', null, 6503, 1, 1),
    (6504, 65, '执行预测', null, null, 'BUTTON', 'model:forecast:execute', null, 6504, 1, 1);

insert ignore into sys_role_permission_ref (role_id, permission_id)
select r.id, p.id
from sys_role_tb r
join sys_permission_tb p on p.id in (6501, 6502, 6503, 6504)
where r.role_code = 'admin';
