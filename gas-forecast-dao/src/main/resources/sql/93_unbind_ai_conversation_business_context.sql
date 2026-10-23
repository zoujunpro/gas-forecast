use gas_data;

-- 会话只表示连续对话，不再按地区、行业或客户拆分。
-- 已经创建的训练、预测范围仍保存在execution_batch_tb和execution_task_tb中。

set @has_business_index = (
    select count(*) from information_schema.statistics
    where table_schema = database()
      and table_name = 'ai_conversation_tb'
      and index_name = 'idx_user_business'
);
set @drop_business_index_sql = if(
    @has_business_index > 0,
    'alter table ai_conversation_tb drop index idx_user_business',
    'select 1'
);
prepare drop_business_index_statement from @drop_business_index_sql;
execute drop_business_index_statement;
deallocate prepare drop_business_index_statement;

set @has_region_code = (
    select count(*) from information_schema.columns
    where table_schema = database()
      and table_name = 'ai_conversation_tb'
      and column_name = 'region_code'
);
set @drop_region_code_sql = if(
    @has_region_code > 0,
    'alter table ai_conversation_tb drop column region_code',
    'select 1'
);
prepare drop_region_code_statement from @drop_region_code_sql;
execute drop_region_code_statement;
deallocate prepare drop_region_code_statement;

set @has_industry_code = (
    select count(*) from information_schema.columns
    where table_schema = database()
      and table_name = 'ai_conversation_tb'
      and column_name = 'industry_code'
);
set @drop_industry_code_sql = if(
    @has_industry_code > 0,
    'alter table ai_conversation_tb drop column industry_code',
    'select 1'
);
prepare drop_industry_code_statement from @drop_industry_code_sql;
execute drop_industry_code_statement;
deallocate prepare drop_industry_code_statement;

set @has_customer_code = (
    select count(*) from information_schema.columns
    where table_schema = database()
      and table_name = 'ai_conversation_tb'
      and column_name = 'customer_code'
);
set @drop_customer_code_sql = if(
    @has_customer_code > 0,
    'alter table ai_conversation_tb drop column customer_code',
    'select 1'
);
prepare drop_customer_code_statement from @drop_customer_code_sql;
execute drop_customer_code_statement;
deallocate prepare drop_customer_code_statement;

alter table ai_conversation_tb
    modify column memory_json json default null
        comment '可变会话记忆，包括当前业务焦点、已确认参数和待处理动作；不作为会话绑定条件';
