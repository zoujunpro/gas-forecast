use gas_data;

-- 未实现响应重放的请求号不具备实际幂等语义，统一移除。
set @has_request_id_index = (
    select count(*) from information_schema.statistics
    where table_schema = database()
      and table_name = 'agent_run_tb'
      and index_name = 'uk_agent_run_request_id'
);
set @drop_request_id_index_sql = if(
    @has_request_id_index > 0,
    'alter table agent_run_tb drop index uk_agent_run_request_id',
    'select 1'
);
prepare drop_request_id_index_statement from @drop_request_id_index_sql;
execute drop_request_id_index_statement;
deallocate prepare drop_request_id_index_statement;

set @has_request_id = (
    select count(*) from information_schema.columns
    where table_schema = database()
      and table_name = 'agent_run_tb'
      and column_name = 'request_id'
);
set @drop_request_id_sql = if(
    @has_request_id > 0,
    'alter table agent_run_tb drop column request_id',
    'select 1'
);
prepare drop_request_id_statement from @drop_request_id_sql;
execute drop_request_id_statement;
deallocate prepare drop_request_id_statement;
