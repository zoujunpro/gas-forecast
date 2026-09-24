use gas_data;

set @drop_button_code_sql = (
    select if(count(*) > 0, 'alter table sys_permission_tb drop column button_code', 'select 1')
    from information_schema.columns
    where table_schema = database()
      and table_name = 'sys_permission_tb'
      and column_name = 'button_code'
);
prepare drop_button_code_stmt from @drop_button_code_sql;
execute drop_button_code_stmt;
deallocate prepare drop_button_code_stmt;
