---
name: customer-gas-forecast
description: 发起并跟踪短期、月度销量和冬季保供用气预测。适用于单客户或批量预测、预测状态和结果查询；明确的模型训练管理交给 model-training-management。
allowed-tools:
  - customer_search
  - region_search
  - industry_search
  - business_reference_resolve
  - forecast_start
  - forecast_status_get
  - forecast_result_get
  - forecast_latest_get
  - forecast_history_get
  - task_status_get
  - task_steps_get
---

# 用气预测

围绕用户的最终目标逐步执行：每一步只选择一个工具，读取其结果后再决定是否调用下一个工具；一个请求可以顺序调用多个工具。遇到候选歧义、信息不足、需要确认、业务失败，或训练/预测任务已经受理时，立即停止本轮并回复用户。Java 服务负责权限、数据质量、模型适配、幂等和任务状态机。

处理请求前读取：

- [参数与实体](references/parameters.md)
- [预测决策](references/decision-flow.md)
- [状态与失败](references/status-handling.md)

## 创建预测

1. 缺少客户编码时，有客户名称就调用 `customer_search`；客户信息完全缺失时询问用户。
2. 客户存在多个候选时停止并让用户选择，不得猜测。
3. 根据场景设置 `agentCode` 和预测粒度。用户未指定周期时采用场景默认值，不为已有默认值反复追问。
4. 用户本轮明确给出的客户、行业和地区覆盖历史记忆。
5. 批量预测、自动训练或强制重训必须先获得用户确认；普通单客户预测无需额外确认。
6. 参数齐全后调用一次 `forecast_start`。返回任务或批次编号后结束本轮，禁止持续轮询或重复创建。

## 查询

- 有预测批次号时调用 `forecast_status_get`；只有统一任务号时调用 `task_status_get`。
- 用户需要统一任务步骤明细时调用 `task_steps_get`。
- 查询最新预测时，先唯一确认客户，再调用 `forecast_latest_get`。
- 查询历史记录时，先唯一确认客户，再调用 `forecast_history_get`。
- 查询指定批次结果时先确认真实状态；只有成功后才调用 `forecast_result_get`。

## 失败和停止条件

- `EXECUTING` 或 `WAITING_TRAINING` 表示任务仍在运行，只说明当前阶段并结束本轮。
- 数据质量、客户行业一致性或模型适配失败时立即停止，不自动重试。
- 创建请求超时后不得再次创建；应提示结果未知，并让用户按任务或批次查询状态。
- 工具失败后不得继续显示执行中，也不得编造预测结果。
