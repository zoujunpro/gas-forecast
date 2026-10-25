use gas_data;

-- 当前仅保留消息与执行任务、预测结果的引用；历史数据不删除，仅收敛表定义说明。
alter table ai_message_ref_tb
    modify column ref_type varchar(32) not null
        comment '引用类型：EXECUTION_TASK执行任务、FORECAST预测结果';

alter table ai_message_ref_tb
    comment = 'AI消息与执行任务、预测结果的引用关系表';
