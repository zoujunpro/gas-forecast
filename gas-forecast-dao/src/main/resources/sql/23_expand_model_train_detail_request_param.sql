-- 训练请求包含完整 dataset，普通 BLOB 容量不足。
alter table model_train_record_tb
    modify request_param longblob null;
