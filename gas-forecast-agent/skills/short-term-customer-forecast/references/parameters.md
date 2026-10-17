# 参数规则

实时MCP JSON Schema是参数格式的最终依据。下面记录当前业务含义。

## 公共身份参数

| 参数 | 规则 |
|---|---|
| `conversationNo` | 记忆、启动、状态和结果工具声明必填时必须提供 |
| `agentCode` | 固定为 `short-term` |
| `customerCode` | 客户唯一解析后优先使用标准编码 |
| `customerName` | 可以用于查询；存在多个匹配时不能直接执行 |
| `idempotencyKey` | 每次用户授权的新操作生成新值；同一操作的重试必须复用 |

## 预测参数

| 参数 | 当前规则 |
|---|---|
| `forecastFrequency` | 只能是 `DAILY` |
| `forecastStartDate` | 可选，格式为 `yyyy-MM-dd`；不能早于客户最新有效日销量的下一天 |
| `forecastEndDate` | 可选，包含结束当天；提供后优先于 `forecastHorizon` |
| `forecastHorizon` | 可选；当前默认30天，优先采用模型最大周期配置，缺省最大值为90天 |
| `autoTrain` | 当前默认 `true` |
| `bulk` | 默认 `false` |
| `confirmed` | 批量操作获得明确确认后才能设置为 `true` |

同时提供开始和结束日期时，日预测周期包含开始和结束当天。模型上限未知时，不要替代Java进行猜测性校验。

## 训练参数

| 参数 | 当前规则 |
|---|---|
| `trainMode` | 可选值为 `ALL`、`RECENT` 或 `RANGE` |
| `trainingPeriods` | `RECENT` 模式必填，表示最近多少个日周期 |
| `trainingStartDate` | `RANGE` 模式下与结束日期同时必填 |
| `trainingEndDate` | `RANGE` 模式下与开始日期同时必填 |
| `forceRetrain` | 默认 `false`，设置为 `true` 前需确认 |

## 普通预测的最小调用示例

```json
{
  "conversationNo": "<会话编号>",
  "agentCode": "short-term",
  "customerCode": "<解析后的客户编码>",
  "bulk": false,
  "forecastFrequency": "DAILY",
  "autoTrain": true,
  "idempotencyKey": "<稳定的操作幂等键>"
}
```

不能机械复制示例：必须保留用户明确指定的日期，并始终遵守实时工具Schema。
