---
name: business-data-query
intent: QUERY
description: 只读查询客户、地区、行业以及统一任务的状态和执行步骤；不负责预测结果。
allowed-tools:
  - region_list
  - industry_list
  - customer_list
  - task_status_get
  - task_steps_get
read-only: true
---

# 业务数据查询

本 Skill 只定义只读查询边界。意图识别、实体和指代解析、缺参澄清以及工具调用由公共 LangGraph 流程负责。

- 只允许读取真实业务数据，不得创建预测任务。
- 编号类型不明确时允许先校验业务编号。
- 工具返回空数据、歧义或失败时直接保留真实状态，不得编造结果。
- 分页结果必须区分当前页数量与总数量。
- 预测结果的读取、比较和解释统一由 forecast-analysis Skill 处理。
