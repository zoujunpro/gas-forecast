use gas_data;

alter table model_feature_definition_tb
    add column source_type varchar(20) not null default 'DATABASE' comment '数据来源：DATABASE/API/REPORT_LLM' after feature_column,
    add column status varchar(20) not null default 'ACTIVE' comment '特征状态：CANDIDATE/ACTIVE/DISABLED' after source_type;
