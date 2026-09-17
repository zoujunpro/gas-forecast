create database if not exists gas_data default character set utf8mb4 collate utf8mb4_unicode_ci;
use gas_data;

drop table if exists gas_backtest_detail;
drop table if exists gas_feature_rank;
drop table if exists gas_model_rank;
drop table if exists gas_customer_forecast_point;
drop table if exists gas_forecast_point;
drop table if exists gas_forecast_summary;
drop table if exists gas_customer;
drop table if exists gas_province;
drop table if exists gas_area;

create table gas_area (
    id bigint primary key auto_increment,
    area_code char(36) not null,
    area_name varchar(64) not null,
    created_at timestamp not null default current_timestamp,
    unique key uk_gas_area_code (area_code),
    unique key uk_gas_area_name (area_name)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table gas_province (
    id bigint primary key auto_increment,
    province_code char(36) not null,
    province_name varchar(32) not null,
    area_code char(36) not null,
    created_at timestamp not null default current_timestamp,
    unique key uk_gas_province_code (province_code),
    unique key uk_gas_province_name (province_name),
    key idx_gas_province_area_code (area_code)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table gas_customer (
    id bigint primary key auto_increment,
    customer_code char(36) not null,
    customer_name varchar(128) not null,
    area_code char(36) not null,
    province_code char(36) not null,
    customer_type varchar(32) not null,
    split_ratio decimal(10, 6) not null,
    created_at timestamp not null default current_timestamp,
    unique key uk_gas_customer_code (customer_code),
    key idx_gas_customer_province_code (province_code),
    key idx_gas_customer_area_code (area_code)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table gas_forecast_summary (
    id bigint primary key auto_increment,
    province_code char(36) not null,
    province varchar(32) not null,
    best_model varchar(128) not null,
    backtest_mape decimal(18, 8),
    backtest_wmape decimal(18, 8),
    backtest_rmse decimal(18, 4),
    feature_count int,
    forecast_horizon int,
    weather_source varchar(128),
    report_text text,
    chart_path varchar(255),
    created_at timestamp not null default current_timestamp,
    key idx_gas_forecast_summary_province_code (province_code),
    unique key uk_gas_forecast_summary_province (province)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table gas_forecast_point (
    id bigint primary key auto_increment,
    province_code char(36) not null,
    province varchar(32) not null,
    forecast_date date not null,
    tenday_label varchar(32) not null,
    avg_temp decimal(18, 6),
    max_temp decimal(18, 6),
    min_temp decimal(18, 6),
    hdd decimal(18, 6),
    extreme_cold_days int,
    weather_source varchar(128),
    prediction decimal(18, 6),
    lower_value decimal(18, 6),
    upper_value decimal(18, 6),
    created_at timestamp not null default current_timestamp,
    key idx_gas_forecast_point_province_code (province_code),
    unique key uk_gas_forecast_point_province_date (province, forecast_date),
    key idx_gas_forecast_point_date (forecast_date)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table gas_customer_forecast_point (
    id bigint primary key auto_increment,
    area_code char(36) not null,
    province_code char(36) not null,
    customer_code char(36) not null,
    forecast_date date not null,
    tenday_label varchar(32) not null,
    prediction decimal(18, 6),
    lower_value decimal(18, 6),
    upper_value decimal(18, 6),
    created_at timestamp not null default current_timestamp,
    unique key uk_gas_customer_forecast_row (customer_code, forecast_date),
    key idx_gas_customer_forecast_scope (area_code, province_code, forecast_date)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table gas_model_rank (
    id bigint primary key auto_increment,
    province varchar(32) not null,
    model_name varchar(128) not null,
    model_type varchar(32),
    mape decimal(18, 8),
    wmape decimal(18, 8),
    smape decimal(18, 8),
    rmse decimal(18, 6),
    mae decimal(18, 6),
    r2 decimal(18, 8),
    constituents varchar(512),
    created_at timestamp not null default current_timestamp,
    unique key uk_gas_model_rank_province_model (province, model_name),
    key idx_gas_model_rank_mape (province, mape)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table gas_feature_rank (
    id bigint primary key auto_increment,
    province varchar(32) not null,
    feature_name varchar(128) not null,
    score decimal(18, 10),
    tree_importance decimal(18, 10),
    mutual_info decimal(18, 10),
    created_at timestamp not null default current_timestamp,
    unique key uk_gas_feature_rank_province_feature (province, feature_name),
    key idx_gas_feature_rank_score (province, score)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table gas_backtest_detail (
    id bigint primary key auto_increment,
    province varchar(32) not null,
    model_name varchar(128) not null,
    season varchar(32) not null,
    test_date date not null,
    actual decimal(18, 6),
    prediction decimal(18, 6),
    absolute_error decimal(18, 6),
    ape_pct decimal(18, 8),
    created_at timestamp not null default current_timestamp,
    unique key uk_gas_backtest_detail_row (province, model_name, season, test_date),
    key idx_gas_backtest_detail_date (province, test_date)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
