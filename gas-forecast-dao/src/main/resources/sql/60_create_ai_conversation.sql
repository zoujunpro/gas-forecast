use gas_data;

create table if not exists ai_conversation_tb (
    id bigint unsigned not null auto_increment,
    conversation_no varchar(64) not null,
    title varchar(128) not null,
    agent_code varchar(64) not null default 'short-term',
    status varchar(32) not null default 'ACTIVE',
    pending_intent_json json default null,
    memory_json json default null,
    last_train_batch_no varchar(64) default null,
    last_forecast_batch_no varchar(64) default null,
    created_by varchar(64) not null,
    created_at datetime not null,
    updated_at datetime not null,
    primary key (id),
    unique key uk_ai_conversation_no (conversation_no),
    key idx_ai_conversation_user (created_by, updated_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='AI对话会话';

create table if not exists ai_conversation_message_tb (
    id bigint unsigned not null auto_increment,
    conversation_id bigint unsigned not null,
    role varchar(16) not null,
    content text not null,
    intent_code varchar(64) default null,
    structured_json json default null,
    created_at datetime not null,
    primary key (id),
    key idx_ai_message_conversation (conversation_id, id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='AI对话消息';
