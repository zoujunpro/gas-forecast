# 动态指标定义输出协议

输出必须是合法 JSON，顶层只包含 `indicator_definitions`：

```json
{
  "indicator_definitions": [
    {
      "indicator_name": "供应紧张程度",
      "definition": "衡量指定区域天然气供应相对于需求的紧张程度",
      "business_object": "SUPPLY",
      "time_granularity": "MONTH",
      "suggested_unit": "SCORE",
      "search_description": "中国天然气供应量、库存量和供需紧张程度",
      "calculation_basis": ["供应量", "库存量", "需求量"],
      "reason": "供应紧张程度可能影响天然气销量",
      "evidence": "报告中的对应原文"
    }
  ]
}
```

字段要求：

| 字段 | 要求 |
|---|---|
| `indicator_name` | 简洁、稳定的中文指标名称，不包含地区和日期 |
| `definition` | 指标衡量的业务含义，不描述具体数值 |
| `business_object` | `SUPPLY`、`DEMAND`、`INVENTORY`、`PRICE`、`POLICY`、`TRANSPORT` 或 `MARKET_SENTIMENT` |
| `time_granularity` | `DAY`、`TENDAY`、`MONTH` 或 `YEAR` |
| `suggested_unit` | `SCORE`、`PERCENT`、`VOLUME`、`PRICE` 或 `BOOLEAN` 等建议单位 |
| `search_description` | 用于搜索钢联真实指标的简洁业务描述，不包含虚构的指标编码 |
| `calculation_basis` | 后续获取或计算指标值所需的依据，至少一项 |
| `reason` | 指标与预测目标之间的影响关系 |
| `evidence` | 支持该指标定义的报告原文片段 |

禁止输出指标值、永久特征标识、SQL、代码或额外解释。
