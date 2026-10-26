-- 会话逻辑删除：保留历史消息、业务引用和 Agent Run 用于审计及故障排查。
-- 正常会话查询必须固定过滤 deleted_flag = 0。
set @schema_name := database();

set @sql := if(
    exists(select 1 from information_schema.columns where table_schema = @schema_name and table_name = 'ai_conversation_tb' and column_name = 'deleted_flag'),
    'select 1',
    'alter table ai_conversation_tb add column deleted_flag tinyint not null default 0 comment ''逻辑删除标识：0否、1是'' after status'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql := if(
    exists(select 1 from information_schema.columns where table_schema = @schema_name and table_name = 'ai_conversation_tb' and column_name = 'deleted_at'),
    'select 1',
    'alter table ai_conversation_tb add column deleted_at datetime default null comment ''逻辑删除时间'' after deleted_flag'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql := if(
    exists(select 1 from information_schema.statistics where table_schema = @schema_name and table_name = 'ai_conversation_tb' and index_name = 'idx_user_visible_recent'),
    'select 1',
    'alter table ai_conversation_tb add key idx_user_visible_recent (created_by, deleted_flag, last_message_at)'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;
