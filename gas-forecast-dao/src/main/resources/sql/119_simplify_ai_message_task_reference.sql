use gas_data;

-- 会话消息只关联统一执行任务；预测结果通过execution_task_tb.business_no继续查询。
set @has_ref_type = (
    select count(*) from information_schema.columns
    where table_schema = database()
      and table_name = 'ai_message_ref_tb'
      and column_name = 'ref_type'
);

set @simplify_reference_sql = if(
    @has_ref_type = 1,
    'alter table ai_message_ref_tb drop index uk_message_ref, drop index idx_ref, drop column ref_type, change column ref_no task_no varchar(64) not null comment ''执行任务编号，关联execution_task_tb.task_no'', add unique key uk_message_task_relation (message_id, task_no, relation_type), add key idx_task_no (task_no), comment = ''AI消息与执行任务的引用关系表''',
    'select 1'
);
prepare simplify_reference_statement from @simplify_reference_sql;
execute simplify_reference_statement;
deallocate prepare simplify_reference_statement;
