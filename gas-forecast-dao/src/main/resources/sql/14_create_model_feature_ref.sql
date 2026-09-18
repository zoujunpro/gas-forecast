use gas_data;

create table if not exists model_feature_ref (
    id bigint primary key auto_increment,
    model_id bigint not null comment '模型ID',
    feature_id bigint not null comment '特征ID',
    required_flag tinyint not null default 0 comment '是否必选特征',
    feature_order int not null default 0 comment '特征顺序',
    created_at timestamp not null default current_timestamp,
    created_by bigint,
    unique key uk_model_feature_ref (model_id, feature_id),
    key idx_model_feature_ref_model (model_id),
    key idx_model_feature_ref_feature (feature_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='模型特征关联表';
