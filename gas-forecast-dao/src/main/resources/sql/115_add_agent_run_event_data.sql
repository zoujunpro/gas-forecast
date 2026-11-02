-- 每个过程事件可保存面向用户展示的结构化产物摘要；完整训练、回测、预测明细仍保存在业务结果表。
ALTER TABLE agent_run_event_tb
    ADD COLUMN event_data_json json DEFAULT NULL COMMENT '事件结构化产物摘要，供前端按卡片或表格展示' AFTER message;
