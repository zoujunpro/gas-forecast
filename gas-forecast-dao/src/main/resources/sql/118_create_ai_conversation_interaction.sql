use gas_data;

create table if not exists ai_conversation_interaction_tb (
    id bigint not null auto_increment comment '主键ID',
    interaction_id varchar(64) not null comment '交互唯一编号',
    conversation_id bigint not null comment '所属会话ID，关联ai_conversation_tb.id',

    source_turn_id varchar(64) not null comment '最初用户业务问题所在轮次',
    question_turn_id varchar(64) not null comment '系统提出本次问题的轮次',
    answer_turn_id varchar(64) default null comment '用户回答本次问题的轮次',

    question_message_id bigint unsigned default null comment '提出问题的助手消息ID，关联ai_conversation_message_tb.id',
    answer_message_id bigint unsigned default null comment '用户回答消息ID，关联ai_conversation_message_tb.id',

    interaction_type varchar(32) not null comment '交互类型：CLARIFICATION澄清、CONFIRMATION确认',
    reason_code varchar(64) not null comment '后端确定的主要交互原因编码',

    question_json json not null comment '返回前端的完整结构化问题',
    operation_json json not null comment 'Python恢复当前未完成操作所需的结构化快照',
    answer_json json default null comment '解析并校验后的结构化回答',

    validation_code varchar(64) default null comment '回答校验结果编码',
    validation_message varchar(500) default null comment '回答校验结果说明',

    status varchar(32) not null default 'WAITING' comment '交互状态：WAITING、ANSWERED、INVALID、CANCELLED、EXPIRED',
    answered_at datetime default null comment '用户回答时间',
    expired_at datetime default null comment '交互过期时间',
    created_at datetime not null default current_timestamp comment '创建时间',
    updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间',

    primary key (id),
    unique key uk_ai_interaction_id (interaction_id),
    key idx_ai_interaction_conversation_status (conversation_id, status),
    key idx_ai_interaction_source_turn (source_turn_id),
    key idx_ai_interaction_question_turn (question_turn_id),
    key idx_ai_interaction_answer_turn (answer_turn_id),
    key idx_ai_interaction_question_message (question_message_id),
    key idx_ai_interaction_answer_message (answer_message_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='AI会话澄清及确认交互记录表';
