use gas_data;

-- 当前仅支持预测任务，删除 execution_task_tb 中不再使用的业务类型字段。
set @has_business_ref_index = (
    select count(*) from information_schema.statistics
    where table_schema = database() and table_name = 'execution_task_tb' and index_name = 'idx_business_ref'
);
set @drop_business_ref_index_sql = if(
    @has_business_ref_index > 0,
    'alter table execution_task_tb drop index idx_business_ref',
    'select 1'
);
prepare drop_business_ref_index_statement from @drop_business_ref_index_sql;
execute drop_business_ref_index_statement;
deallocate prepare drop_business_ref_index_statement;

set @has_business_type = (
    select count(*) from information_schema.columns
    where table_schema = database() and table_name = 'execution_task_tb' and column_name = 'business_type'
);
set @drop_business_type_sql = if(
    @has_business_type > 0,
    'alter table execution_task_tb drop column business_type',
    'select 1'
);
prepare drop_business_type_statement from @drop_business_type_sql;
execute drop_business_type_statement;
deallocate prepare drop_business_type_statement;
