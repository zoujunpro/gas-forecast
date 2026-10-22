use gas_data;

create table if not exists ai_conversation_tb (
    id bigint not null auto_increment comment '主键ID',
    conversation_no varchar(64) not null comment '会话唯一编号',
    title varchar(200) default null comment '会话标题',
    summary text default null comment '会话内容摘要',
    agent_code varchar(64) not null comment '所属智能体编码',
    status varchar(20) not null default 'ACTIVE' comment '会话状态：ACTIVE正常、ARCHIVED归档',
    memory_json json default null comment '可变会话记忆，包括当前业务焦点、已确认参数和待处理动作；不作为会话绑定条件',
    last_message_at datetime default null comment '最近消息时间',
    created_by varchar(64) not null comment '创建人编码',
    created_at datetime not null default current_timestamp comment '创建时间',
    updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间',
    primary key (id),
    unique key uk_conversation_no (conversation_no),
    key idx_user_agent_recent (created_by, agent_code, last_message_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci comment='AI智能体会话表';

create table if not exists ai_conversation_message_tb (
    id bigint unsigned not null auto_increment comment '主键ID',
    conversation_id bigint unsigned not null comment '所属会话ID，关联ai_conversation_tb.id',
    role varchar(16) not null comment '消息角色：USER用户、ASSISTANT助手、SYSTEM系统、TOOL工具',
    turn_id varchar(64) default null comment '对话轮次ID，同一轮用户与助手消息使用相同值',
    content text not null comment '消息正文',
    message_type varchar(64) default null comment '消息分类或来源Skill编码，与业务绑定类型无关',
    structured_json json default null comment '消息附带的结构化数据JSON',
    created_at datetime not null comment '消息创建时间',
    primary key (id),
    key idx_ai_message_conversation (conversation_id, id),
    unique key uk_ai_message_turn_role (conversation_id, turn_id, role)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='AI对话消息表';
