-- 消息现在记录来源 Skill 或系统消息类型，不再保存旧意图编码。
-- 新库在 60 号脚本中已经是 message_type；存量库才执行列改名。
set @has_legacy_intent_column = (
    select count(*)
    from information_schema.columns
    where table_schema = database()
      and table_name = 'ai_conversation_message_tb'
      and column_name = 'intent_code'
);
set @rename_message_type_sql = if(
    @has_legacy_intent_column > 0,
    'alter table ai_conversation_message_tb change column intent_code message_type varchar(64) default null',
    'select 1'
);
prepare rename_message_type_statement from @rename_message_type_sql;
execute rename_message_type_statement;
deallocate prepare rename_message_type_statement;
