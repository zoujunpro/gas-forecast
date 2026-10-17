use gas_data;

-- 普通AI问答不预先绑定智能体；预测分析会话仍保存预测记录所属的agent_code。
alter table ai_conversation_tb
    modify column agent_code varchar(64) null default null comment '普通会话为空，预测会话为预测所属智能体';

