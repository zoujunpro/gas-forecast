set @has_turn_id = (
    select count(*) from information_schema.columns
    where table_schema = database()
      and table_name = 'ai_conversation_message_tb'
      and column_name = 'turn_id'
);
set @add_turn_id_sql = if(
    @has_turn_id = 0,
    'alter table ai_conversation_message_tb add column turn_id varchar(64) default null after role',
    'select 1'
);
prepare add_turn_id_statement from @add_turn_id_sql;
execute add_turn_id_statement;
deallocate prepare add_turn_id_statement;

set @has_turn_role_index = (
    select count(*) from information_schema.statistics
    where table_schema = database()
      and table_name = 'ai_conversation_message_tb'
      and index_name = 'uk_ai_message_turn_role'
);
set @add_turn_role_index_sql = if(
    @has_turn_role_index = 0,
    'alter table ai_conversation_message_tb add unique key uk_ai_message_turn_role (conversation_id, turn_id, role)',
    'select 1'
);
prepare add_turn_role_index_statement from @add_turn_role_index_sql;
execute add_turn_role_index_statement;
deallocate prepare add_turn_role_index_statement;
