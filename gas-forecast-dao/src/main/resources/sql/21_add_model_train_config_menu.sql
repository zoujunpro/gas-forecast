use gas_data;

insert ignore into sys_permission_tb (id, parent_id, permission_name, path, component, permission_type, perms, icon, sort_no, hidden, status) values
    (64, 60, '模型训练管理', '/model/train-config', 'views/model/ModelTrainConfigManagement.vue', 'MENU', 'model:train-config:list', null, 64, 0, 1),
    (6401, 64, '新增模型训练配置', null, null, 'BUTTON', 'model:train-config:create', null, 6401, 1, 1),
    (6402, 64, '编辑模型训练配置', null, null, 'BUTTON', 'model:train-config:update', null, 6402, 1, 1),
    (6403, 64, '删除模型训练配置', null, null, 'BUTTON', 'model:train-config:delete', null, 6403, 1, 1),
    (6404, 64, '执行模型训练', null, null, 'BUTTON', 'config:train:execute', null, 6404, 1, 1);

update sys_permission_tb
set permission_name = '模型训练管理',
    path = '/model/train-config',
    component = 'views/model/ModelTrainConfigManagement.vue',
    permission_type = 'MENU',
    perms = 'model:train-config:list',
    sort_no = 64,
    hidden = 0,
    status = 1
where id = 64;

insert ignore into sys_role_permission_ref (role_id, permission_id)
select r.id, p.id
from sys_role_tb r
join sys_permission_tb p
where r.role_code = 'admin'
  and p.id in (64, 6401, 6402, 6403);
