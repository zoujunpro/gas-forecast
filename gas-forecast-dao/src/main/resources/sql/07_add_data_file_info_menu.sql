use gas_data;

insert ignore into sys_permission_tb (id, parent_id, permission_name, path, component, permission_type, perms, icon, sort_no, hidden, status) values
    (40, null, '后台数据管理', '/data', null, 'MENU', null, 'Grid', 40, 0, 1),
    (49, 40, '原始数据文件管理', '/data/file-info', 'views/business/file/BaseFileInfoManagement.vue', 'MENU', 'data:file-info:list', null, 49, 0, 1),
    (4901, 49, '新增原始数据文件', null, null, 'BUTTON', 'data:file-info:create', null, 4901, 1, 1),
    (4902, 49, '编辑原始数据文件', null, null, 'BUTTON', 'data:file-info:update', null, 4902, 1, 1),
    (4903, 49, '删除原始数据文件', null, null, 'BUTTON', 'data:file-info:delete', null, 4903, 1, 1);

update sys_permission_tb
set permission_name = '后台数据管理',
    path = '/data',
    component = null,
    permission_type = 'MENU',
    perms = null,
    icon = 'Grid',
    sort_no = 40,
    hidden = 0,
    status = 1
where id = 40;

update sys_permission_tb
set permission_name = '原始数据文件管理',
    path = '/data/file-info',
    component = 'views/business/file/BaseFileInfoManagement.vue',
    permission_type = 'MENU',
    perms = 'data:file-info:list',
    sort_no = 49,
    hidden = 0,
    status = 1
where id = 49;

insert ignore into sys_role_permission_ref (role_id, permission_id)
select r.id, p.id
from sys_role_tb r
join sys_permission_tb p
where r.role_code = 'admin'
  and p.id in (40, 49, 4901, 4902, 4903);
