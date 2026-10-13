use gas_data;

-- 日销量数据不维护单位概念；兼容已经删除过该字段的环境。
set @drop_daily_sales_unit_sql = (
    select if(
        count(*) > 0,
        'alter table data_daily_sales_tb drop column unit',
        'select 1'
    )
    from information_schema.columns
    where table_schema = database()
      and table_name = 'data_daily_sales_tb'
      and column_name = 'unit'
);

prepare drop_daily_sales_unit_stmt from @drop_daily_sales_unit_sql;
execute drop_daily_sales_unit_stmt;
deallocate prepare drop_daily_sales_unit_stmt;
