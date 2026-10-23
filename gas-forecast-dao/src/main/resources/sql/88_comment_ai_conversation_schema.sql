use gas_data;

alter table ai_conversation_tb
    modify column id bigint not null auto_increment comment '主键ID',
    modify column conversation_no varchar(64) not null comment '会话唯一编号',
    modify column title varchar(200) default null comment '会话标题',
    modify column summary text default null comment '会话内容摘要',
    modify column agent_code varchar(64) not null comment '所属智能体编码',
    modify column status varchar(20) not null default 'ACTIVE' comment '会话状态：ACTIVE正常、ARCHIVED归档',
    modify column memory_json json default null comment '可变会话记忆，包括当前业务焦点、已确认参数和待处理动作；不作为会话绑定条件',
    modify column last_message_at datetime default null comment '最近消息时间',
    modify column created_by varchar(64) not null comment '创建人编码',
    modify column created_at datetime not null default current_timestamp comment '创建时间',
    modify column updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间',
    comment='AI智能体会话表';

alter table ai_conversation_message_tb
    modify column id bigint unsigned not null auto_increment comment '主键ID',
    modify column conversation_id bigint unsigned not null comment '所属会话ID，关联ai_conversation_tb.id',
    modify column role varchar(16) not null comment '消息角色：USER用户、ASSISTANT助手、SYSTEM系统、TOOL工具',
    modify column turn_id varchar(64) default null comment '对话轮次ID，同一轮用户与助手消息使用相同值',
    modify column content text not null comment '消息正文',
    modify column message_type varchar(64) default null comment '消息分类或来源Skill编码，与业务绑定类型无关',
    modify column structured_json json default null comment '消息附带的结构化数据JSON',
    modify column created_at datetime not null comment '消息创建时间',
    comment='AI对话消息表';
