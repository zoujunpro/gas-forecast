---
name: model-training-management
intent: TRAIN
description: 创建单客户或批量模型训练和强制重训；训练状态、结果和历史记录属于 QUERY。
allowed-tools:
  - training_start
read-only: false
---

# 创建模型训练

本 Skill 只定义创建训练的能力边界。训练数据准备、质量校验、模型执行、回测和结果保存均由 Java 与模型平台完成。

- 客户、地区或行业名称由 `understand_request` 在路由前完成真实性校验；存在歧义或不存在时不进入本 Skill。
- 参数完成后只调用一次 `training_start`。
- 批量训练和强制重训的确认由公共确认流程处理，Java 服务执行最终校验。
- 工具返回任务已受理、需要确认、需要澄清或失败后立即结束本轮，不持续轮询。
