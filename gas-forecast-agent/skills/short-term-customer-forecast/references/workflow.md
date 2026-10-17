# 短期客户日预测流程

## 判断用户意图

| 用户意图 | 主要操作 |
|---|---|
| 发起新的日预测 | 解析客户，然后调用 `forecast_start` |
| 明确要求训练模型 | 解析客户；如需强制重训则先确认；然后调用 `training_start` |
| 查询执行到哪里 | 有 `taskNo` 时调用 `task_status_get`，否则调用对应的批次状态工具 |
| 查看预测数据 | 确认预测成功，然后调用 `forecast_result_get` |
| 查看模型质量 | 确认训练成功，然后调用 `training_result_get` |
| 继续中断的智能体任务 | 依次调用 `task_status_get`、`task_steps_get`，再按照恢复建议处理 |

如果用户只是查询全部客户、客户档案、地区或行业信息，本Skill停止处理并将请求交回会话路由；这些查询不属于短期训练预测业务。

## 业务主流程

```text
识别训练或预测意图
→ 收集必要参数
→ 为训练预测解析唯一客户或批量范围
→ 智能体执行前置检查
→ 需要时取得用户确认
→ Java工具执行最终业务校验
→ 创建训练或预测任务
→ 保存真实任务号或批次号
→ 查询进度
→ 获取结果
→ 向用户解释结果或下一步
```

## 单客户预测

1. 使用用户输入的客户名称，以及可选地区和行业条件调用 `customer_search`。
2. 只有结果为 `UNIQUE` 时才能继续。
3. 调用 `forecast_start` 时传入 `conversationNo`、`agentCode: short-term`、解析后的 `customerCode`、`bulk: false`、`forecastFrequency: DAILY` 和稳定的 `idempotencyKey`。
4. 只有用户明确指定预测日期或天数时才传这些字段。没有指定时，由Java从客户最新销量日期的下一天开始，并采用模型配置或默认预测天数；当前安全默认值为30天。
5. 默认保持 `autoTrain: true`。只有用户明确要求没有模型时直接失败，才设置为 `false`。
6. 分别判断每个客户的返回任务：
   - 返回 `EXECUTING` 和 `forecastBatchNo`：使用 `forecast_status_get` 查询进度。
   - 返回 `WAITING_TRAINING` 和 `trainBatchNo`：使用 `training_status_get` 查询训练进度；训练成功后，应先确认后端是否已经自动提交预测，不能直接重复创建。
   - 返回失败：按照结构化失败原因处理。
7. 预测成功后调用 `forecast_result_get`。除非用户只需要概要，默认设置 `includePoints: true`。

## 用户明确发起训练

用户直接要求训练、重新训练，或者明确关闭自动训练时，使用 `training_start`。

- `ALL`：使用所有符合条件的历史数据。
- `RECENT`：必须提供 `trainingPeriods`。
- `RANGE`：必须同时提供 `trainingStartDate` 和 `trainingEndDate`。
- 用户明确指定训练日期时，优先于配置默认值，并按 `RANGE` 提交。
- `forceRetrain: true` 会创建新的训练工作，必须先向用户说明并确认。

使用 `training_status_get` 查询进度。只有训练成功后才能使用 `training_result_get` 读取指标。

## 批量执行

1. 将地区和行业解析成标准编码。城市燃气范围应通过 `industry_search` 解析“城市燃气”别名，禁止硬编码行业编码。
2. 向用户说明地区、行业、时间范围，以及本次将创建多个客户任务。
3. 等待用户明确确认。
4. 调用启动工具时设置 `bulk: true` 和 `confirmed: true`。
5. 分别报告 `total`、`submitted`、`failed`、返回的全部批次号以及逐客户失败原因。部分成功不能描述成全部成功。

## 进度与恢复

- 有 `taskNo`：调用 `task_status_get(includeRecovery=true)`；需要明细时再调用 `task_steps_get`。
- 只有 `trainBatchNo`：调用 `training_status_get`。
- 只有 `forecastBatchNo`：调用 `forecast_status_get`。
- 任务仍在执行时继续等待，禁止创建重复任务。
- 只有真实任务状态为 `FAILED` 或 `INTERRUPTED`，用户已经明确确认，并且服务端建议为 `RESUME` 而不是 `WAIT` 或 `RETRY` 时，才能调用 `task_resume`。
