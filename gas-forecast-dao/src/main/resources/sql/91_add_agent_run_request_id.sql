use gas_data;

-- 兼容已经创建过agent_run_tb但缺少request_id的数据库。
set @has_request_id = (
    select count(*)
    from information_schema.columns
    where table_schema = database()
      and table_name = 'agent_run_tb'
      and column_name = 'request_id'
);
set @add_request_id_sql = if(
    @has_request_id = 0,
    'alter table agent_run_tb add column request_id varchar(128) default null comment ''Java服务端生成的请求跟踪编号，不参与前端幂等或响应重放'' after run_no',
    'select 1'
);
prepare add_request_id_statement from @add_request_id_sql;
execute add_request_id_statement;
deallocate prepare add_request_id_statement;

-- 历史Run使用run_no生成稳定的请求跟踪号，然后统一收紧为非空。
update agent_run_tb
set request_id = concat('LEGACY-', run_no)
where request_id is null or request_id = '';

alter table agent_run_tb
    modify column request_id varchar(128) not null
        comment 'Java服务端生成的请求跟踪编号，不参与前端幂等或响应重放';

set @has_request_id_index = (
    select count(*)
    from information_schema.statistics
    where table_schema = database()
      and table_name = 'agent_run_tb'
      and index_name = 'uk_agent_run_request_id'
);
set @add_request_id_index_sql = if(
    @has_request_id_index = 0,
    'alter table agent_run_tb add unique key uk_agent_run_request_id (request_id)',
    'select 1'
);
prepare add_request_id_index_statement from @add_request_id_index_sql;
execute add_request_id_index_statement;
deallocate prepare add_request_id_index_statement;
