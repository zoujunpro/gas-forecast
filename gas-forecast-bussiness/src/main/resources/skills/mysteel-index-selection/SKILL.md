---
name: mysteel-index-selection
description: 汇总多个动态指标定义的钢联真实候选，并通过一次全局判断选择适合当前训练的 indexCode。不得生成候选列表之外的指标编码。
---

# 钢联指标选择

## 目标

为动态指标定义匹配可查询历史数据的钢联真实指标。

## 流程

1. 接收 Java 已按多个指标定义查询并汇总的 `indicator_groups`。
2. 在一次判断中比较全部候选指标与各自定义、业务对象、时间粒度和训练范围。
3. 只从各分组候选列表中选择零个或多个 `index_code`，并返回对应 `definition_id`。
4. 严格按照 [references/selection-schema.md](references/selection-schema.md) 返回 JSON。
5. Java 后端再次校验所选编码是否属于本次候选列表，通过后才能调用 `mysteel_index_data` 查询数据。

## 约束

- 禁止自行构造、改写或猜测 `index_code`。
- 无合适候选时返回空的 `selected_indexes`，不得勉强选择。
- 优先选择名称和口径直接匹配的指标，不根据模糊关联扩大范围。
- 全局去除重复或高度相似的指标，并遵守输入的 `max_features` 上限。
- 每个选择必须说明与动态指标定义的对应关系。
- 本步骤只选择指标编码，不生成或修改指标数据。
