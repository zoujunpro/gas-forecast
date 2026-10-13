use gas_data;

alter table data_daily_sales_tb
    add column stat_month varchar(7)
        generated always as (left(stat_date, 7)) stored comment '所属月份YYYY-MM' after stat_date,
    add column stat_tenday varchar(9)
        generated always as (
            concat(left(stat_date, 7), '-',
                   case when cast(substr(stat_date, 9, 2) as unsigned) <= 10 then '1'
                        when cast(substr(stat_date, 9, 2) as unsigned) <= 20 then '2'
                        else '3' end)
        ) stored comment '所属旬YYYY-MM-1/2/3' after stat_month,
    add key idx_daily_sales_customer_month (customer_code, stat_month),
    add key idx_daily_sales_customer_tenday (customer_code, stat_tenday),
    add key idx_daily_sales_scope_month (region_code, industry_code, stat_month),
    add key idx_daily_sales_scope_tenday (region_code, industry_code, stat_tenday);

alter table data_region_weather_daily_tb
    add column stat_month varchar(7)
        generated always as (left(cast(stat_date as char), 7)) stored comment '所属月份YYYY-MM' after stat_date,
    add column stat_tenday varchar(9)
        generated always as (
            concat(left(cast(stat_date as char), 7), '-',
                   case when day(stat_date) <= 10 then '1'
                        when day(stat_date) <= 20 then '2'
                        else '3' end)
        ) stored comment '所属旬YYYY-MM-1/2/3' after stat_month,
    add key idx_weather_region_month (region_code, weather_type, stat_month),
    add key idx_weather_region_tenday (region_code, weather_type, stat_tenday);
