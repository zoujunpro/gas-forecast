---
name: model-training-management
description: 管理燃气预测模型的训练、重训、进度、结果和失败恢复。适用于用户明确要求训练或诊断训练任务；普通预测由 customer-gas-forecast 处理。
allowed-tools:
  - customer_search
  - region_search
  - industry_search
  - business_reference_resolve
  - training_start
  - training_status_get
  - training_result_get
  - training_latest_get
  - training_history_get
  - task_status_get
  - task_steps_get
  - task_resume
  - task_retry
---

# 模型训练管理

围绕用户的最终目标逐步执行：每一步只选择一个工具，读取其结果后再决定是否调用下一个工具；一个请求可以顺序调用多个工具。遇到候选歧义、信息不足、需要确认、业务失败，或训练任务已经受理时，立即停止本轮并回复用户。Java 服务执行最终数据质量、实体一致性、模型适配、幂等和状态迁移校验。

处理请求前读取：

- [训练参数与校验](references/training-rules.md)
- [任务状态与恢复](references/recovery.md)
- [训练结果解释](references/result-interpretation.md)

## 训练

1. 缺少客户编码时调用 `customer_search`；多个候选时让用户选择。
2. 明确训练范围和是否强制重训。批量训练或强制重训必须先获得确认；普通单客户首次训练无需额外确认。
3. 参数齐全后调用一次 `training_start`，拿到批次号后结束本轮，禁止持续轮询或重复提交。
4. 询问进度、结果、最新训练或历史时，分别调用对应的查询工具。

## 恢复

先用 `task_status_get` 或 `task_steps_get` 获取真实状态。只有服务端明确允许且用户确认后，才能调用 `task_resume` 或 `task_retry`。

## 失败和停止条件

- 数据质量阻断、实体冲突或非重试错误立即停止，并展示服务端原因。
- 训练仍在执行时结束本轮，不循环查询。
- 创建请求超时后不得直接重试，先查询真实任务状态。
- 失败、取消或完成后不得继续显示执行中。
