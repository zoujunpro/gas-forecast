use gas_data;

-- 兼容已手工创建的execution_task_tb：task_type改为business_type，
-- train_batch_no/forecast_batch_no合并为一个多态业务编号business_no。
set @has_business_type = (
    select count(*) from information_schema.columns
    where table_schema = database() and table_name = 'execution_task_tb' and column_name = 'business_type'
);
set @has_task_type = (
    select count(*) from information_schema.columns
    where table_schema = database() and table_name = 'execution_task_tb' and column_name = 'task_type'
);
set @rename_task_type_sql = if(
    @has_business_type = 0 and @has_task_type > 0,
    'alter table execution_task_tb change column task_type business_type varchar(32) not null comment ''业务类型：TRAIN模型训练、FORECAST模型预测''',
    'select 1'
);
prepare rename_task_type_statement from @rename_task_type_sql;
execute rename_task_type_statement;
deallocate prepare rename_task_type_statement;

set @has_business_type = (
    select count(*) from information_schema.columns
    where table_schema = database() and table_name = 'execution_task_tb' and column_name = 'business_type'
);
set @add_business_type_sql = if(
    @has_business_type = 0,
    'alter table execution_task_tb add column business_type varchar(32) not null comment ''业务类型：TRAIN模型训练、FORECAST模型预测'' after batch_no',
    'select 1'
);
prepare add_business_type_statement from @add_business_type_sql;
execute add_business_type_statement;
deallocate prepare add_business_type_statement;

set @has_business_no = (
    select count(*) from information_schema.columns
    where table_schema = database() and table_name = 'execution_task_tb' and column_name = 'business_no'
);
set @add_business_no_sql = if(
    @has_business_no = 0,
    'alter table execution_task_tb add column business_no varchar(64) default null comment ''对应训练或预测业务批次号，业务批次尚未创建时为空'' after business_type',
    'select 1'
);
prepare add_business_no_statement from @add_business_no_sql;
execute add_business_no_statement;
deallocate prepare add_business_no_statement;

set @has_train_batch_no = (
    select count(*) from information_schema.columns
    where table_schema = database() and table_name = 'execution_task_tb' and column_name = 'train_batch_no'
);
set @has_forecast_batch_no = (
    select count(*) from information_schema.columns
    where table_schema = database() and table_name = 'execution_task_tb' and column_name = 'forecast_batch_no'
);
set @copy_business_no_sql = case
    when @has_train_batch_no > 0 and @has_forecast_batch_no > 0 then
        'update execution_task_tb set business_no = case when business_type = ''TRAIN'' then train_batch_no when business_type = ''FORECAST'' then forecast_batch_no else coalesce(train_batch_no, forecast_batch_no) end where business_no is null'
    when @has_train_batch_no > 0 then
        'update execution_task_tb set business_no = train_batch_no where business_no is null'
    when @has_forecast_batch_no > 0 then
        'update execution_task_tb set business_no = forecast_batch_no where business_no is null'
    else 'select 1'
end;
prepare copy_business_no_statement from @copy_business_no_sql;
execute copy_business_no_statement;
deallocate prepare copy_business_no_statement;

set @has_train_batch_index = (
    select count(*) from information_schema.statistics
    where table_schema = database() and table_name = 'execution_task_tb' and index_name = 'idx_train_batch_no'
);
set @drop_train_batch_index_sql = if(
    @has_train_batch_index > 0,
    'alter table execution_task_tb drop index idx_train_batch_no',
    'select 1'
);
prepare drop_train_batch_index_statement from @drop_train_batch_index_sql;
execute drop_train_batch_index_statement;
deallocate prepare drop_train_batch_index_statement;

set @has_forecast_batch_index = (
    select count(*) from information_schema.statistics
    where table_schema = database() and table_name = 'execution_task_tb' and index_name = 'idx_forecast_batch_no'
);
set @drop_forecast_batch_index_sql = if(
    @has_forecast_batch_index > 0,
    'alter table execution_task_tb drop index idx_forecast_batch_no',
    'select 1'
);
prepare drop_forecast_batch_index_statement from @drop_forecast_batch_index_sql;
execute drop_forecast_batch_index_statement;
deallocate prepare drop_forecast_batch_index_statement;

set @drop_train_batch_column_sql = if(
    @has_train_batch_no > 0,
    'alter table execution_task_tb drop column train_batch_no',
    'select 1'
);
prepare drop_train_batch_column_statement from @drop_train_batch_column_sql;
execute drop_train_batch_column_statement;
deallocate prepare drop_train_batch_column_statement;

set @drop_forecast_batch_column_sql = if(
    @has_forecast_batch_no > 0,
    'alter table execution_task_tb drop column forecast_batch_no',
    'select 1'
);
prepare drop_forecast_batch_column_statement from @drop_forecast_batch_column_sql;
execute drop_forecast_batch_column_statement;
deallocate prepare drop_forecast_batch_column_statement;

alter table execution_task_tb
    modify column business_type varchar(32) not null
        comment '业务类型：TRAIN模型训练、FORECAST模型预测' after batch_no,
    modify column business_no varchar(64) default null
        comment '对应训练或预测业务批次号，业务批次尚未创建时为空' after business_type;

set @has_business_ref_index = (
    select count(*) from information_schema.statistics
    where table_schema = database() and table_name = 'execution_task_tb' and index_name = 'idx_business_ref'
);
set @add_business_ref_index_sql = if(
    @has_business_ref_index = 0,
    'alter table execution_task_tb add key idx_business_ref (business_type, business_no)',
    'select 1'
);
prepare add_business_ref_index_statement from @add_business_ref_index_sql;
execute add_business_ref_index_statement;
deallocate prepare add_business_ref_index_statement;

alter table agent_run_tb
    modify column message_id bigint unsigned not null
        comment '触发本次Agent执行的用户消息ID，关联ai_conversation_message_tb.id';

alter table ai_message_ref_tb
    modify column message_id bigint unsigned not null comment '关联消息ID，关联ai_conversation_message_tb.id';
