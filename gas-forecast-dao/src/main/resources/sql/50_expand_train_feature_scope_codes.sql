use gas_data;

-- 日销量标准数据中的区域和行业编码可能是 36 位 UUID。
alter table model_train_feature_data_tb
    modify column region_code varchar(64) null comment '区域编码',
    modify column industry_code varchar(64) null comment '行业编码';
