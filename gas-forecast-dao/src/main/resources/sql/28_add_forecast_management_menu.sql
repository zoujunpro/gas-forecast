use gas_data;

insert into sys_permission_tb
    (id, parent_id, permission_name, path, component, permission_type, perms, icon, sort_no, hidden, status)
values
    (65, 60, '预测管理', '/model/forecast-management', 'views/model/ModelForecastManagement.vue',
     'MENU', 'model:forecast:list', null, 65, 0, 1)
on duplicate key update
    parent_id = values(parent_id),
    permission_name = values(permission_name),
    path = values(path),
    component = values(component),
    permission_type = values(permission_type),
    perms = values(perms),
    sort_no = values(sort_no),
    hidden = values(hidden),
    status = values(status);

insert ignore into sys_role_permission_ref (role_id, permission_id)
select r.id, 65 from sys_role_tb r where r.role_code = 'admin';
