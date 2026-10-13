use gas_data;

set @drop_feature_type = if(
    exists(select 1
           from information_schema.columns
           where table_schema = database()
             and table_name = 'model_feature_definition_tb'
             and column_name = 'feature_type'),
    'alter table model_feature_definition_tb drop column feature_type',
    'select 1'
);

prepare stmt from @drop_feature_type;
execute stmt;
deallocate prepare stmt;
