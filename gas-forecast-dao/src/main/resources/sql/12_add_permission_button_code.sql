use gas_data;

set @add_button_code_sql = (
    select if(
        count(*) = 0,
        'alter table sys_permission_tb add column button_code varchar(64) null comment ''custom button identifier'' after perms',
        'select 1'
    )
    from information_schema.columns
    where table_schema = database()
      and table_name = 'sys_permission_tb'
      and column_name = 'button_code'
);

prepare add_button_code_stmt from @add_button_code_sql;
execute add_button_code_stmt;
deallocate prepare add_button_code_stmt;

alter table sys_permission_tb
    modify permission_type varchar(16) not null comment 'DIRECTORY, MENU or BUTTON';
