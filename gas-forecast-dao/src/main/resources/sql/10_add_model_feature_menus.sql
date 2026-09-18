use gas_data;

insert ignore into sys_permission_tb (id, parent_id, permission_name, path, component, permission_type, perms, icon, sort_no, hidden, status) values
    (60, null, '模型管理', '/model', null, 'MENU', null, 'DataAnalysis', 60, 0, 1),
    (62, 60, '训练数据管理', '/model/train-feature-data', 'views/model/ModelTrainFeatureDataManagement.vue', 'MENU', 'model:train-feature-data:list', null, 62, 0, 1),
    (63, 60, '特征定义管理', '/model/feature-definitions', 'views/model/ModelFeatureDefinitionManagement.vue', 'MENU', 'model:feature-definition:list', null, 63, 0, 1),
    (6301, 63, '新增特征定义', null, null, 'BUTTON', 'model:feature-definition:create', null, 6301, 1, 1),
    (6302, 63, '编辑特征定义', null, null, 'BUTTON', 'model:feature-definition:update', null, 6302, 1, 1),
    (6303, 63, '删除特征定义', null, null, 'BUTTON', 'model:feature-definition:delete', null, 6303, 1, 1);

update sys_permission_tb
set permission_name = '模型管理',
    path = '/model',
    component = null,
    permission_type = 'MENU',
    perms = null,
    icon = 'DataAnalysis',
    sort_no = 60,
    hidden = 0,
    status = 1
where id = 60;

update sys_permission_tb
set permission_name = '训练数据管理',
    path = '/model/train-feature-data',
    component = 'views/model/ModelTrainFeatureDataManagement.vue',
    permission_type = 'MENU',
    perms = 'model:train-feature-data:list',
    sort_no = 62,
    hidden = 0,
    status = 1
where id = 62;

update sys_permission_tb
set permission_name = '特征定义管理',
    path = '/model/feature-definitions',
    component = 'views/model/ModelFeatureDefinitionManagement.vue',
    permission_type = 'MENU',
    perms = 'model:feature-definition:list',
    sort_no = 63,
    hidden = 0,
    status = 1
where id = 63;

insert ignore into sys_role_permission_ref (role_id, permission_id)
select r.id, p.id
from sys_role_tb r
join sys_permission_tb p
where r.role_code = 'admin'
  and p.id in (60, 62, 63, 6201, 6202, 6203, 6301, 6302, 6303);

delete from sys_role_permission_ref where permission_id in (6201, 6202, 6203);
delete from sys_permission_tb where id in (6201, 6202, 6203);
