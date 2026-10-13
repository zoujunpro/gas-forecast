use gas_data;

create table if not exists agent_strategy_config_tb (
    id bigint unsigned not null auto_increment comment '主键ID',
    agent_code varchar(64) not null comment '智能体编码',
    agent_name varchar(128) not null comment '智能体名称',
    train_scope_type varchar(32) not null comment '训练主体 CUSTOMER/REGION/INDUSTRY/REGION_INDUSTRY',
    trigger_type varchar(32) not null default 'HYBRID' comment '触发方式',
    check_cron varchar(64) not null comment '巡检Cron',
    data_delay_periods int not null default 0 comment '数据延迟周期数',
    model_validity_periods int not null default 1 comment '模型有效周期数',
    max_retry_count int not null default 3 comment '最大重试次数',
    retry_interval_minutes int not null default 30 comment '重试间隔分钟',
    auto_train_enabled tinyint not null default 1 comment '是否自动训练',
    auto_forecast_enabled tinyint not null default 0 comment '是否自动预测',
    forecast_lead_periods int not null default 1 comment '预测提前周期数',
    enabled tinyint not null default 1 comment '是否启用',
    description varchar(500) default null comment '策略说明',
    created_at datetime not null default current_timestamp,
    updated_at datetime not null default current_timestamp on update current_timestamp,
    primary key (id),
    unique key uk_agent_strategy_code (agent_code)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='智能体运行策略配置';

insert into agent_strategy_config_tb
(agent_code, agent_name, train_scope_type, trigger_type, check_cron, data_delay_periods, model_validity_periods, max_retry_count, retry_interval_minutes, auto_train_enabled, auto_forecast_enabled, forecast_lead_periods, enabled, description)
values
('short-term', '短期客户预测智能体', 'CUSTOMER', 'HYBRID', '0 0 2 * * ?', 1, 7, 3, 30, 1, 0, 1, 1, '每日巡检新的完整日数据，按客户独立训练。'),
('monthly-sales', '月度销量预测智能体', 'REGION_INDUSTRY', 'HYBRID', '0 30 2 1 * ?', 1, 1, 3, 60, 1, 0, 1, 1, '月度数据结算后按地区和行业训练。'),
('winter-supply', '冬季保供预测智能体', 'REGION', 'HYBRID', '0 0 3 1,11,21 * ?', 1, 3, 3, 60, 1, 0, 1, 1, '每旬巡检完整数据，按省级地区训练。')
on duplicate key update agent_name=values(agent_name);

insert ignore into sys_permission_tb (id, parent_id, permission_name, path, component, permission_type, perms, icon, sort_no, hidden, status) values
(66, 60, '智能体策略', '/model/agent-strategy', 'views/model/AgentStrategyConfigManagement.vue', 'MENU', 'agent:strategy:list', null, 66, 0, 1),
(6601, 66, '新增智能体策略', null, null, 'BUTTON', 'agent:strategy:create', null, 6601, 1, 1),
(6602, 66, '编辑智能体策略', null, null, 'BUTTON', 'agent:strategy:update', null, 6602, 1, 1),
(6603, 66, '删除智能体策略', null, null, 'BUTTON', 'agent:strategy:delete', null, 6603, 1, 1);

insert ignore into sys_role_permission_ref (role_id, permission_id)
select r.id, p.id from sys_role_tb r join sys_permission_tb p
where r.role_code='admin' and p.id in (66,6601,6602,6603);
