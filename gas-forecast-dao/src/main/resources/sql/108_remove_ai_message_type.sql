use gas_data;

-- 消息角色和 Agent Run 已足以表达消息语义，移除未被消费的 message_type。
set @has_message_type = (
    select count(*) from information_schema.columns
    where table_schema = database()
      and table_name = 'ai_conversation_message_tb'
      and column_name = 'message_type'
);
set @drop_message_type_sql = if(
    @has_message_type > 0,
    'alter table ai_conversation_message_tb drop column message_type',
    'select 1'
);
prepare drop_message_type_statement from @drop_message_type_sql;
execute drop_message_type_statement;
deallocate prepare drop_message_type_statement;
