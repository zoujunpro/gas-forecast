---
name: customer-gas-forecast
intent: FORECAST
description: 创建短期、月度销量或冬季保供预测；查询预测数据属于 QUERY，结果解读属于 ANALYZE。
allowed-tools:
  - forecast_start
read-only: false
---

# 创建用气预测

本 Skill 只定义创建预测的能力边界。训练、模型选择和预测执行均由 `forecast_start` 对应的 Java 业务服务完成。

- 客户、地区或行业名称由 `understand_request` 在路由前完成真实性校验；存在歧义或不存在时不进入本 Skill。
- 参数完成后只调用一次 `forecast_start`。
- 批量预测、自动训练或强制重训的确认由公共确认流程处理，Java 服务执行最终校验。
- 工具返回任务已受理、需要确认、需要澄清或失败后立即结束本轮，不持续轮询。
