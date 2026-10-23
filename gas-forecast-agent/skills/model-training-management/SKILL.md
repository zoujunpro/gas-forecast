---
name: model-training-management
intent: TRAIN
description: 兼容“训练模型”这一用户表达；实际只创建包含训练、回测和预测的统一预测任务。
allowed-tools:
  - forecast_start
read-only: false
---

# 发起统一预测

系统不再提供独立训练任务。用户说“训练模型”时，创建一次统一预测任务；训练数据准备、模型训练、回测和预测均由 Java 与模型平台完成。

- 客户、地区或行业名称由 `understand_request` 在路由前完成真实性校验；存在歧义或不存在时不进入本 Skill。
- 参数完成后只调用一次 `forecast_start`。
- 执行确认由公共确认流程处理，Java 服务执行最终校验。
- 工具返回任务已受理、需要确认、需要澄清或失败后立即结束本轮，不持续轮询。
