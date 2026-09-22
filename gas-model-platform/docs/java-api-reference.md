# gas-model-platform Java 接口说明

本文档供 Java 业务服务调用 Python 模型平台使用。

## 1. 基本约定

- 服务地址：由 Java 环境配置，例如 `http://gas-model-platform:8090`
- 业务接口前缀：`/api/v1`
- 请求和响应格式：`application/json`
- 日期格式：`yyyy-MM-dd`
- 日期时间格式：ISO-8601，例如 `2026-09-22T13:30:00`
- JSON 字段统一使用 `snake_case`
- Java 调用时建议传递 `X-User-Id`；平台会在响应头返回 `X-Request-Id`

`agent_code` 与 Java 智能体枚举第一个参数保持一致：

| Java枚举 | agent_code |
|---|---|
| `WINTER_SUPPLY` | `winter-supply` |
| `MONTHLY_SALES` | `monthly-sales` |
| `SHORT_CUSTOMER` | `short-term` |

模型调用以 `model_code` 为路由主键。`agent_code` 在训练、回测和预测请求中可不传；
如果传入，必须与该模型注册的 `agent_code` 一致。

## 2. 统一响应

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

失败响应保持相同结构，同时使用对应 HTTP 状态码：

```json
{
  "code": 422,
  "message": "字段必填",
  "data": {
    "errors": [
      {
        "type": "missing",
        "field": "body.model_code",
        "message": "字段必填"
      }
    ]
  }
}
```

| HTTP状态码 | 含义 |
|---:|---|
| 200 | 调用成功；数据校验不通过也返回200，通过 `data.valid` 判断 |
| 404 | 模型编号或模型产物不存在 |
| 422 | 请求结构、参数或训练数据不满足要求 |
| 500 | 模型执行异常 |

## 3. 模型基本信息

### 3.1 查询全部模型

```http
GET /api/v1/models
```

### 3.2 查询指定模型

```http
GET /api/v1/models?model_code=MODEL_JIANGSHU_DIANLI_V1.0
```

`model_code` 为可选查询参数。不传返回全部；传入后返回匹配模型。`data` 始终为数组。

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "agent_code": "short-term",
      "model_code": "MODEL_JIANGSHU_DIANLI_V1.0",
      "model_version": "1.0.0",
      "model_name": "江苏电力日级预测模型 V1",
      "description": "Prophet 与 LightGBM 残差融合，使用 Walk-Forward 回测选择参数。",
      "capabilities": ["train", "backtest", "predict"],
      "training_data_range": {
        "type": "history_length",
        "frequency": "day",
        "minimum": 880,
        "recommended": 1060,
        "continuous": true,
        "period": null,
        "minimum_history_before_period": null,
        "description": "至少需要880天连续日数据，建议提供1060天以上。"
      }
    }
  ]
}
```

`training_data_range.type`：

- `history_length`：按 `frequency` 表示最少历史长度。
- `complete_period`：表示至少需要若干完整业务周期，例如冬供季。

## 4. 训练数据预校验

用户选定数据后、正式训练前调用。

```http
POST /api/v1/models/validate-training-data
```

请求示例：

```json
{
  "model_code": "MODEL_JIANGSHU_DIANLI_V1.0",
  "region_code": "320000",
  "region_name": "江苏",
  "params": {},
  "dataset": [
    {
      "date": "2024-01-01",
      "gas_sales": 100.0,
      "tempmax": 12.5
    }
  ]
}
```

`dataset` 与正式训练使用相同的数据。除 `date`、`gas_sales` 外，可以携带模型所需的
动态特征。校验接口不需要 `train_batch_no`，不会训练或保存模型。

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "agent_code": "short-term",
    "model_code": "MODEL_JIANGSHU_DIANLI_V1.0",
    "valid": false,
    "summary": {
      "row_count": 700,
      "unique_date_count": 700,
      "start_date": "2024-01-01",
      "end_date": "2025-12-01",
      "missing_date_count": 1,
      "duplicate_date_count": 0
    },
    "errors": [
      {
        "code": "INSUFFICIENT_HISTORY",
        "message": "江苏电力模型至少需要880天数据，当前只有700天",
        "expected": 880,
        "actual": 700
      },
      {
        "code": "NON_CONTINUOUS_DATES",
        "message": "训练数据缺少1个日期，日级数据必须连续",
        "expected": 0,
        "actual": 1
      }
    ],
    "warnings": []
  }
}
```

- `valid=false`：禁止提交训练。
- `errors`：阻止训练的问题，可能同时存在多条。
- `warnings`：不阻止训练，但应提示用户。
- `summary`：模型清洗后识别到的数据量、日期范围和业务周期等概况。

正式训练接口会再次执行相同校验，Java 不能只依赖页面校验。

### 4.1 Java 侧调用顺序

Java 管理端按以下顺序执行，校验失败时不得生成训练批次或写入训练记录：

1. 根据训练配置加载完整训练数据快照。
2. 使用与正式训练相同的 `model_code`、范围、`params` 和 `dataset` 调用预校验接口。
3. `data.valid=false` 时，将 `errors[].message` 返回页面并终止。
4. 校验通过后生成 `train_batch_no`、保存训练记录并调用 `/api/v1/train`。
5. `/api/v1/train` 仍会再次执行相同校验，防止绕过 Java 前置校验。

Java 服务可通过以下环境变量覆盖模型平台地址：

- `GAS_AGENT_MODELS_URL`：模型列表接口地址。
- `GAS_AGENT_VALIDATE_TRAINING_URL`：训练数据预校验接口地址。
- `GAS_AGENT_TRAIN_URL`：正式训练接口地址。

## 5. 模型训练

```http
POST /api/v1/train
```

请求示例：

```json
{
  "model_code": "MODEL_JIANGSHU_DIANLI_V1.0",
  "train_batch_no": "TRAIN_20260922_001",
  "region_code": "320000",
  "region_name": "江苏",
  "params": {},
  "dataset": [
    {
      "date": "2024-01-01",
      "gas_sales": 100.0,
      "tempmax": 12.5
    }
  ]
}
```

关键字段：

| 字段 | 必填 | 说明 |
|---|---|---|
| `model_code` | 是 | 注册模型编号 |
| `train_batch_no` | 是 | Java生成的训练批次号；同一模型批次禁止覆盖 |
| `params` | 否 | 模型专属参数；预校验和训练必须保持一致 |
| `dataset` | 是 | 与预校验相同的数据快照 |

响应 `data` 关键字段：

| 字段 | 说明 |
|---|---|
| `train_batch_no` | 原样返回训练批次号 |
| `metrics` | 独立评估回测的汇总指标 |
| `selected_model_name` | 最终选中的内部模型 |
| `selection_reason` | 选模依据 |
| `candidate_evaluations` | 候选模型排名与指标 |
| `rolling_backtest_results` | 回测日期、实际值、预测值和误差 |
| `rolling_backtest_fold_metrics` | 各回测折指标 |
| `issues` | 训练过程告警或错误明细 |
| `metadata` | 模型版本、产物摘要等扩展信息 |

Java 应保存响应中的 `model_code + train_batch_no`，预测时原样传回。

## 6. 模型回测

```http
POST /api/v1/backtest
```

请求主体使用公共模型上下文，至少传入：

```json
{
  "model_code": "WINTER_MODEL_V1.0",
  "region_name": "江苏",
  "params": {},
  "dataset": [
    {"date": "2019-01-01", "gas_sales": 1000.0}
  ]
}
```

响应 `data.metrics` 为汇总指标，`data.points` 包含：

```json
{
  "stat_date": "2025-11-01",
  "actual_value": 1200.0,
  "predicted_value": 1180.0,
  "lower_value": null,
  "upper_value": null
}
```

## 7. 模型预测

```http
POST /api/v1/predict
```

请求示例：

```json
{
  "model_code": "MODEL_JIANGSHU_DIANLI_V1.0",
  "train_batch_no": "TRAIN_20260922_001",
  "forecast_batch_no": "FORECAST_20260922_001",
  "forecast_horizon": 2,
  "forecast_unit": "day",
  "region_code": "320000",
  "dataset": [
    {"date": "2026-09-23", "tempmax": 28.0},
    {"date": "2026-09-24", "tempmax": 27.5}
  ],
  "params": {}
}
```

约束：

- `forecast_batch_no` 长度为1至65字符，由Java生成。
- `forecast_horizon` 范围为1至366。
- `forecast_unit` 可取 `day`、`tenday`、`month`，必须与模型一致。
- `dataset` 为未来日期及模型要求的未来动态特征，不要求 `gas_sales`。

响应点：

```json
{
  "forecast_date": "2026-09-23",
  "prediction": 123.45,
  "lower_value": 115.0,
  "upper_value": 132.0,
  "extra": {}
}
```

预测阶段没有真实值，因此不返回 MAPE、RMSE 等评估指标。

## 8. 特征计算

### 8.1 单日期

```http
POST /api/v1/features/compute
```

```json
{
  "agent_code": "winter-supply",
  "target_date": "2026-11-01",
  "region_code": "130000",
  "input_row": {"avg_temp": 4.2},
  "history_dataset": [],
  "params": {}
}
```

### 8.2 批量日期

```http
POST /api/v1/features/batch-compute
```

```json
{
  "agent_code": "short-term",
  "target_dates": ["2026-09-23", "2026-09-24"],
  "input_rows": [
    {"date": "2026-09-23", "avg_temp": 25.0},
    {"date": "2026-09-24", "avg_temp": 24.0}
  ],
  "history_dataset": [],
  "params": {}
}
```

批量接口最多接收366个目标日期。

## 9. 健康检查与在线文档

```http
GET /health
GET /docs
GET /openapi.json
```

Java 联调时应优先以 `/openapi.json` 的当前结构为准；本文档用于说明业务语义和调用流程。
