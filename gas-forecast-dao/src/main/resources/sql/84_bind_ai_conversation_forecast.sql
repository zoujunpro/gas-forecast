use gas_data;

-- 预测详情页中的AI分析会话与某一次预测批次唯一绑定。
-- GENERAL会话的business_no为空，MySQL唯一索引仍允许用户创建多个普通会话。
alter table ai_conversation_tb
    add column scene varchar(32) not null default 'GENERAL' comment 'GENERAL/FORECAST_ANALYSIS' after agent_code,
    add column business_type varchar(32) default null comment '绑定业务类型，当前支持FORECAST' after scene,
    add column business_no varchar(64) default null comment '绑定业务编号，预测场景为forecast_batch_no' after business_type;

alter table ai_conversation_tb
    add unique key uk_ai_conversation_business (created_by, scene, business_type, business_no),
    add key idx_ai_conversation_business_no (business_type, business_no);
