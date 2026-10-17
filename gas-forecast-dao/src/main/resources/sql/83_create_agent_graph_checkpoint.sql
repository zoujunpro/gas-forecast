use gas_data;

-- LangGraph专用单表检查点。Python智能体账号只允许访问本表，不得访问业务表。
create table if not exists agent_graph_checkpoint_tb (
    thread_id varchar(128) not null comment 'LangGraph线程编号，业务执行图使用taskNo',
    checkpoint_ns varchar(128) not null default '' comment 'LangGraph检查点命名空间',
    checkpoint_id varchar(128) not null comment 'LangGraph检查点编号',
    parent_checkpoint_id varchar(128) default null comment '父检查点编号',
    checkpoint_type varchar(32) default null comment '允许pending writes先写入占位行',
    checkpoint_blob longblob default null,
    metadata_type varchar(32) default null,
    metadata_blob longblob default null,
    pending_writes_type varchar(32) default null,
    pending_writes_blob longblob default null,
    created_at datetime not null default current_timestamp,
    updated_at datetime not null default current_timestamp on update current_timestamp,
    primary key (thread_id, checkpoint_ns, checkpoint_id),
    key idx_agent_graph_checkpoint_updated (updated_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='LangGraph单表持久化检查点';

-- 兼容早期已创建的非空字段定义。
alter table agent_graph_checkpoint_tb
    modify checkpoint_type varchar(32) default null,
    modify checkpoint_blob longblob default null,
    modify metadata_type varchar(32) default null,
    modify metadata_blob longblob default null;

-- 数据库账号不得在迁移脚本中保存明文密码。部署时由管理员创建仅允许Agent所在主机
-- 连接的gas_agent账号，并且只授予本表SELECT、INSERT、UPDATE、DELETE权限。
