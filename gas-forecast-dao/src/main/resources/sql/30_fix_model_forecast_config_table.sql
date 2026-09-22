use gas_data;

-- 兼容旧环境：将预测配置表改名为预测记录表。
set @old_table_exists = (select count(*) from information_schema.tables
                         where table_schema = database()
                           and table_name = 'model_forecast_config_tb');
set @record_table_exists = (select count(*) from information_schema.tables
                            where table_schema = database()
                              and table_name = 'model_forecast_record_tb');
set @rename_sql = if(@old_table_exists = 1 and @record_table_exists = 0,
                     'rename table model_forecast_config_tb to model_forecast_record_tb',
                     'select 1');
prepare rename_stmt from @rename_sql;
execute rename_stmt;
deallocate prepare rename_stmt;

create table if not exists model_forecast_record_tb (
    id bigint unsigned not null auto_increment comment '主键ID',
    config_code varchar(64) not null comment '预测配置编码',
    config_name varchar(128) not null comment '预测配置名称',
    train_config_code varchar(64) not null comment '关联模型训练配置编码',
    forecast_start_date varchar(16) not null comment '预测开始日期，yyyy-MM-dd',
    forecast_horizon int not null comment '预测步长',
    forecast_frequency varchar(16) not null comment '时间颗粒度：DAILY/TENDAY/MONTHLY',
    agent_code varchar(64) not null comment '由训练配置同步的智能体编码',
    scope_type varchar(32) not null default 'ALL' comment '由训练配置同步的作用范围',
    region_code varchar(64) default null comment '由训练配置同步的区域编码',
    region_name varchar(64) default null comment '由训练配置同步的区域名称',
    industry_code varchar(64) default null comment '由训练配置同步的行业编码',
    industry_name varchar(64) default null comment '由训练配置同步的行业名称',
    customer_code varchar(64) default null comment '由训练配置同步的客户编码',
    customer_name varchar(128) default null comment '由训练配置同步的客户名称',
    model_batch_no varchar(64) default null comment '自动匹配的最新成功训练批次',
    auto_forecast tinyint not null default 0 comment '是否自动预测',
    weather_source varchar(64) default null comment '气象来源',
    enabled tinyint not null default 1 comment '是否启用',
    remark varchar(512) default null comment '备注',
    created_by varchar(64) default null comment '创建人',
    created_by_name varchar(64) default null comment '创建人名称',
    created_at datetime not null default current_timestamp comment '创建时间',
    updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间',
    feature_snapshot longblob default null comment '预测特征数据快照',
    request_param longblob default null comment '模型预测请求参数',
    response_param longblob default null comment '模型预测响应参数',
    primary key (id),
    unique key uk_forecast_config_code (config_code),
    key idx_forecast_config_train (train_config_code),
    key idx_forecast_config_agent (agent_code, enabled),
    key idx_forecast_config_scope (agent_code, region_code, industry_code, customer_code)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='模型预测记录表';
