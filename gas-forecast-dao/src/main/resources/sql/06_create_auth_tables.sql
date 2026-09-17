use gas_data;

create table if not exists sys_user_tb (
    id bigint primary key auto_increment,
    username varchar(64) not null,
    real_name varchar(64) not null,
    password_hash varchar(128) not null,
    password_salt varchar(64) not null,
    avatar varchar(255),
    email varchar(128),
    phone varchar(32),
    org_code varchar(64),
    status tinyint not null default 1 comment '1 normal, 0 disabled',
    deleted tinyint not null default 0,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    unique key uk_sys_user_username (username),
    key idx_sys_user_status (status),
    key idx_sys_user_deleted (deleted)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists sys_role_tb (
    id bigint primary key auto_increment,
    role_code varchar(64) not null,
    role_name varchar(64) not null,
    description varchar(255),
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    unique key uk_sys_role_code (role_code)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists sys_permission_tb (
    id bigint primary key auto_increment,
    parent_id bigint,
    permission_name varchar(64) not null,
    path varchar(255),
    component varchar(255),
    permission_type varchar(16) not null comment 'MENU or BUTTON',
    perms varchar(128),
    icon varchar(64),
    sort_no int not null default 0,
    hidden tinyint not null default 0,
    status tinyint not null default 1,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    unique key uk_sys_permission_path (path),
    key idx_sys_permission_parent (parent_id),
    key idx_sys_permission_type (permission_type),
    key idx_sys_permission_perms (perms)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists sys_department_tb (
    id bigint primary key auto_increment,
    parent_id bigint,
    department_name varchar(64) not null,
    org_code varchar(64) not null,
    sort_no int not null default 0,
    status tinyint not null default 1,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    unique key uk_sys_department_org_code (org_code),
    key idx_sys_department_parent (parent_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists sys_user_role_ref (
    id bigint primary key auto_increment,
    user_id bigint not null,
    role_id bigint not null,
    unique key uk_sys_user_role (user_id, role_id),
    key idx_sys_user_role_user (user_id),
    key idx_sys_user_role_role (role_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists sys_role_permission_ref (
    id bigint primary key auto_increment,
    role_id bigint not null,
    permission_id bigint not null,
    unique key uk_sys_role_permission (role_id, permission_id),
    key idx_sys_role_permission_role (role_id),
    key idx_sys_role_permission_permission (permission_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists sys_user_department_ref (
    id bigint primary key auto_increment,
    user_id bigint not null,
    department_id bigint not null,
    unique key uk_sys_user_department (user_id, department_id),
    key idx_sys_user_department_user (user_id),
    key idx_sys_user_department_department (department_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists sys_department_permission_ref (
    id bigint primary key auto_increment,
    department_id bigint not null,
    permission_id bigint not null,
    unique key uk_sys_department_permission (department_id, permission_id),
    key idx_sys_department_permission_department (department_id),
    key idx_sys_department_permission_permission (permission_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

insert into sys_user_tb (username, real_name, password_hash, password_salt, org_code, status, deleted)
select 'admin', '系统管理员', '6159e2d2e1431afa2e19a66ed49a817b4773fa760d53a41b896b1c69bbcef8f1', 'gas2026', 'GAS', 1, 0
where not exists (select 1 from sys_user_tb where username = 'admin');

insert into sys_role_tb (role_code, role_name, description)
select 'admin', '系统管理员', '拥有全部菜单和操作权限'
where not exists (select 1 from sys_role_tb where role_code = 'admin');

insert into sys_department_tb (department_name, org_code, sort_no)
select '公司总部', 'GAS', 1
where not exists (select 1 from sys_department_tb where org_code = 'GAS');

insert into sys_department_tb (parent_id, department_name, org_code, sort_no)
select p.id, '市场经营部', 'GAS-MARKET', 10 from sys_department_tb p
where p.org_code = 'GAS' and not exists (select 1 from sys_department_tb where org_code = 'GAS-MARKET');

insert into sys_department_tb (parent_id, department_name, org_code, sort_no)
select p.id, '调度运行部', 'GAS-DISPATCH', 20 from sys_department_tb p
where p.org_code = 'GAS' and not exists (select 1 from sys_department_tb where org_code = 'GAS-DISPATCH');

insert into sys_department_tb (parent_id, department_name, org_code, sort_no)
select p.id, '数据分析中心', 'GAS-DATA', 30 from sys_department_tb p
where p.org_code = 'GAS' and not exists (select 1 from sys_department_tb where org_code = 'GAS-DATA');

insert into sys_department_tb (parent_id, department_name, org_code, sort_no)
select p.id, '客户服务部', 'GAS-SERVICE', 40 from sys_department_tb p
where p.org_code = 'GAS' and not exists (select 1 from sys_department_tb where org_code = 'GAS-SERVICE');

insert into sys_department_tb (parent_id, department_name, org_code, sort_no)
select p.id, '华北区域组', 'GAS-MARKET-NORTH', 11 from sys_department_tb p
where p.org_code = 'GAS-MARKET' and not exists (select 1 from sys_department_tb where org_code = 'GAS-MARKET-NORTH');

insert into sys_department_tb (parent_id, department_name, org_code, sort_no)
select p.id, '华东区域组', 'GAS-MARKET-EAST', 12 from sys_department_tb p
where p.org_code = 'GAS-MARKET' and not exists (select 1 from sys_department_tb where org_code = 'GAS-MARKET-EAST');

insert ignore into sys_permission_tb (id, parent_id, permission_name, path, component, permission_type, perms, icon, sort_no, hidden, status) values
    (1, null, '工作台首页', '/', 'views/dashboard/Home.vue', 'MENU', null, 'House', 1, 0, 1),
    (10, null, '智能体中心', '/agent', null, 'MENU', null, 'Cpu', 10, 0, 1),
    (11, 10, '冬季保供预测', '/agent/winter-supply', 'views/agent/AgentDetail.vue', 'MENU', null, null, 11, 0, 1),
    (12, 10, '月度销量预测', '/agent/monthly-sales', 'views/agent/AgentDetail.vue', 'MENU', null, null, 12, 0, 1),
    (13, 10, '短期客户预测', '/agent/short-term', 'views/agent/AgentDetail.vue', 'MENU', null, null, 13, 0, 1),
    (20, null, '预测配置', '/config/forecast', null, 'MENU', null, 'Operation', 20, 0, 1),
    (21, 20, '冬季保供预测配置', '/config/forecast/winter-supply', 'views/agent/AgentConfig.vue', 'MENU', null, null, 21, 0, 1),
    (22, 20, '月度销量预测配置', '/config/forecast/monthly-sales', 'views/agent/AgentConfig.vue', 'MENU', null, null, 22, 0, 1),
    (23, 20, '短期客户预测配置', '/config/forecast/short-term', 'views/agent/AgentConfig.vue', 'MENU', null, null, 23, 0, 1),
    (30, null, '训练配置', '/config/train', null, 'MENU', null, 'DocumentChecked', 30, 0, 1),
    (31, 30, '冬季保供训练配置', '/config/train/winter-supply', 'views/agent/AgentConfig.vue', 'MENU', null, null, 31, 0, 1),
    (32, 30, '月度销量训练配置', '/config/train/monthly-sales', 'views/agent/AgentConfig.vue', 'MENU', null, null, 32, 0, 1),
    (33, 30, '短期客户训练配置', '/config/train/short-term', 'views/agent/AgentConfig.vue', 'MENU', null, null, 33, 0, 1),
    (40, null, '后台数据管理', '/data', null, 'MENU', null, 'Grid', 40, 0, 1),
    (41, 40, '区域表管理', '/data/regions', 'views/business/base/BaseRegionManagement.vue', 'MENU', 'base:region:list', null, 41, 0, 1),
    (42, 40, '客户表管理', '/data/customers', 'views/business/base/BaseCustomerManagement.vue', 'MENU', 'base:customer:list', null, 42, 0, 1),
    (43, 40, '行业表管理', '/data/industries', 'views/business/base/BaseIndustryManagement.vue', 'MENU', 'base:industry:list', null, 43, 0, 1),
    (49, 40, '原始数据文件管理', '/data/file-info', 'views/business/file/BaseFileInfoManagement.vue', 'MENU', 'data:file-info:list', null, 49, 0, 1),
    (44, 40, '日销量标准数据', '/data/daily-sales', 'views/business/sales/StandardSalesData.vue', 'MENU', 'data:daily-sales:list', null, 44, 0, 1),
    (45, 40, '月销量标准数据', '/data/monthly-sales', 'views/business/sales/StandardSalesData.vue', 'MENU', 'data:monthly-sales:list', null, 45, 0, 1),
    (46, 40, '预测批次表', '/data/forecast-batches', 'views/common/Placeholder.vue', 'MENU', null, null, 46, 0, 1),
    (47, 40, '预测结果表', '/data/forecast-results', 'views/common/Placeholder.vue', 'MENU', null, null, 47, 0, 1),
    (48, 40, '训练批次表', '/data/train-batches', 'views/common/Placeholder.vue', 'MENU', null, null, 48, 0, 1),
    (60, null, '模型管理', '/model', null, 'MENU', null, 'DataAnalysis', 60, 0, 1),
    (61, 60, '模型列表', '/model/list', 'views/model/ModelConfigManagement.vue', 'MENU', 'model:config:list', null, 61, 0, 1),
    (50, null, '系统管理', '/system', null, 'MENU', null, 'Setting', 50, 0, 1),
    (51, 50, '用户管理', '/system/users', 'views/system/SystemUserManagement.vue', 'MENU', 'sys:user:list', null, 51, 0, 1),
    (52, 50, '角色权限', '/system/roles', 'views/system/SystemRoleManagement.vue', 'MENU', 'sys:role:list', null, 52, 0, 1),
    (53, 50, '部门管理', '/system/departments', 'views/system/SystemDepartmentManagement.vue', 'MENU', 'sys:department:list', null, 53, 0, 1),
    (54, 50, '菜单管理', '/system/permissions', 'views/system/SystemPermissionManagement.vue', 'MENU', 'sys:permission:list', null, 54, 0, 1),
    (55, 50, '操作日志', '/system/logs', 'views/common/Placeholder.vue', 'MENU', 'sys:log:list', null, 55, 0, 1),
    (56, 50, '系统参数', '/system/settings', 'views/common/Placeholder.vue', 'MENU', 'sys:setting:list', null, 56, 0, 1);

update sys_permission_tb set component = 'views/business/base/BaseRegionManagement.vue', perms = 'base:region:list' where id = 41;
update sys_permission_tb set component = 'views/business/base/BaseCustomerManagement.vue', perms = 'base:customer:list' where id = 42;
update sys_permission_tb set component = 'views/business/base/BaseIndustryManagement.vue', perms = 'base:industry:list' where id = 43;
update sys_permission_tb set permission_name = '原始数据文件管理', path = '/data/file-info', component = 'views/business/file/BaseFileInfoManagement.vue', perms = 'data:file-info:list', sort_no = 49 where id = 49;
update sys_permission_tb set component = 'views/business/sales/StandardSalesData.vue', perms = 'data:daily-sales:list' where id = 44;
update sys_permission_tb set component = 'views/business/sales/StandardSalesData.vue', perms = 'data:monthly-sales:list' where id = 45;
update sys_permission_tb set component = 'views/agent/AgentDetail.vue' where id in (11, 12, 13);
update sys_permission_tb set component = 'views/agent/AgentConfig.vue' where id in (21, 22, 23, 31, 32, 33);
update sys_permission_tb set component = 'views/common/Placeholder.vue' where id in (46, 47, 48, 55, 56);
update sys_permission_tb set permission_name = '模型管理', path = '/model', component = null, perms = null, icon = 'DataAnalysis', sort_no = 60 where id = 60;
update sys_permission_tb set permission_name = '模型列表', path = '/model/list', component = 'views/model/ModelConfigManagement.vue', perms = 'model:config:list', sort_no = 61 where id = 61;
update sys_permission_tb set component = 'views/system/SystemUserManagement.vue', perms = 'sys:user:list' where id = 51;
update sys_permission_tb set component = 'views/system/SystemRoleManagement.vue', perms = 'sys:role:list' where id = 52;
update sys_permission_tb set permission_name = '部门管理', path = '/system/departments', component = 'views/system/SystemDepartmentManagement.vue', perms = 'sys:department:list', sort_no = 53 where id = 53;
update sys_permission_tb set permission_name = '菜单管理', path = '/system/permissions', component = 'views/system/SystemPermissionManagement.vue', perms = 'sys:permission:list', sort_no = 54 where id = 54;
insert ignore into sys_permission_tb (id, parent_id, permission_name, path, component, permission_type, perms, icon, sort_no, hidden, status) values
    (55, 50, '操作日志', '/system/logs', 'views/common/Placeholder.vue', 'MENU', 'sys:log:list', null, 55, 0, 1),
    (56, 50, '系统参数', '/system/settings', 'views/common/Placeholder.vue', 'MENU', 'sys:setting:list', null, 56, 0, 1);

insert ignore into sys_permission_tb (id, parent_id, permission_name, path, component, permission_type, perms, icon, sort_no, hidden, status) values
    (4101, 41, '新增区域', null, null, 'BUTTON', 'base:region:create', null, 4101, 1, 1),
    (4102, 41, '编辑区域', null, null, 'BUTTON', 'base:region:update', null, 4102, 1, 1),
    (4103, 41, '删除区域', null, null, 'BUTTON', 'base:region:delete', null, 4103, 1, 1),
    (4201, 42, '新增客户', null, null, 'BUTTON', 'base:customer:create', null, 4201, 1, 1),
    (4202, 42, '编辑客户', null, null, 'BUTTON', 'base:customer:update', null, 4202, 1, 1),
    (4203, 42, '删除客户', null, null, 'BUTTON', 'base:customer:delete', null, 4203, 1, 1),
    (4301, 43, '新增行业', null, null, 'BUTTON', 'base:industry:create', null, 4301, 1, 1),
    (4302, 43, '编辑行业', null, null, 'BUTTON', 'base:industry:update', null, 4302, 1, 1),
    (4303, 43, '删除行业', null, null, 'BUTTON', 'base:industry:delete', null, 4303, 1, 1),
    (4901, 49, '新增原始数据文件', null, null, 'BUTTON', 'data:file-info:create', null, 4901, 1, 1),
    (4902, 49, '编辑原始数据文件', null, null, 'BUTTON', 'data:file-info:update', null, 4902, 1, 1),
    (4903, 49, '删除原始数据文件', null, null, 'BUTTON', 'data:file-info:delete', null, 4903, 1, 1),
    (6101, 61, '新增模型', null, null, 'BUTTON', 'model:config:create', null, 6101, 1, 1),
    (6102, 61, '模型配置', null, null, 'BUTTON', 'model:config:update', null, 6102, 1, 1),
    (6103, 61, '删除模型', null, null, 'BUTTON', 'model:config:delete', null, 6103, 1, 1),
    (5101, 51, '保存用户', null, null, 'BUTTON', 'sys:user:save', null, 5101, 1, 1),
    (5102, 51, '删除用户', null, null, 'BUTTON', 'sys:user:delete', null, 5102, 1, 1),
    (5201, 52, '保存角色', null, null, 'BUTTON', 'sys:role:save', null, 5201, 1, 1),
    (5202, 52, '删除角色', null, null, 'BUTTON', 'sys:role:delete', null, 5202, 1, 1),
    (5301, 53, '保存部门', null, null, 'BUTTON', 'sys:department:save', null, 5301, 1, 1),
    (5302, 53, '删除部门', null, null, 'BUTTON', 'sys:department:delete', null, 5302, 1, 1),
    (5401, 54, '保存菜单', null, null, 'BUTTON', 'sys:permission:save', null, 5401, 1, 1),
    (5402, 54, '删除菜单', null, null, 'BUTTON', 'sys:permission:delete', null, 5402, 1, 1);

insert ignore into sys_user_role_ref (user_id, role_id)
select u.id, r.id from sys_user_tb u join sys_role_tb r
where u.username = 'admin' and r.role_code = 'admin';

insert ignore into sys_user_department_ref (user_id, department_id)
select u.id, d.id from sys_user_tb u join sys_department_tb d
where u.username = 'admin' and d.org_code = 'GAS';

insert ignore into sys_role_permission_ref (role_id, permission_id)
select r.id, p.id from sys_role_tb r join sys_permission_tb p
where r.role_code = 'admin';
