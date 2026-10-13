# 钢联指标选择输出协议

```json
{
  "selected_indexes": [
    {
      "definition_id": "definition_001",
      "index_code": "ID010001",
      "index_name": "中国天然气月度供应量",
      "reason": "该指标直接反映天然气供应变化"
    }
  ]
}
```

要求：

- 输入包含多个 `indicator_groups`，需要在一次判断中比较全部指标定义及候选项。
- `definition_id` 必须来自输入中的指标分组，并与所选候选项所属分组一致。
- `index_code` 和 `index_name` 必须逐字来自对应分组的同一候选项。
- `reason` 说明候选指标与动态指标定义的对应关系。
- 综合考虑相关性和重复性，最终选择数量不得超过输入的 `max_features`。
- 不同分组出现相同或高度相似的候选指标时，只保留最合适的一项。
- 全部候选都不合适时返回 `{"selected_indexes":[]}`。
- 不输出指标值、SQL、代码或候选列表之外的编码。
