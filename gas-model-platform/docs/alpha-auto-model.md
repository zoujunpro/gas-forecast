# Alpha Auto 自动选模模型

## 来源和迁移边界

模型编码：`MODEL_ALPHA_AUTO_V1.0`

算法来自：

```text
/Users/zoujun/work/workspace/shiyou/quanta-alpha/agent_core
```

本次迁移保留 `ForecastAgent._run_auto` 使用的数据清洗、时间切分、候选模型、
自动选模、超参数优化、独立测试集评估和最终预测工具。未迁移 ReAct、LLM、
会话、记忆、权限、事件图、工作流编排、绘图和 Markdown 报告模块。

平台 Handler 位于：

```text
src/gas_model_platform/models/alpha_model/handler.py
```

原预测工具保存在：

```text
src/gas_model_platform/models/alpha_model/tools/forecast/
```

## 数据要求

- `agent_code`: `short-term`
- `forecast_unit`: `day`
- 训练行固定字段：`date`、`gas_sales`
- `date` 必须唯一、连续、按日覆盖；Handler 会排序，但拒绝重复和缺日
- `gas_sales` 必须为有限非负数
- 最少 30 天，建议 90 天，年度季节性建议 365 天以上
- 当前迁移版本为单变量预测，不读取训练数据中的其他动态特征

## 可选参数

训练请求的 `params` 支持：

| 参数 | 默认值 | 说明 |
|---|---:|---|
| `holdout_size` | `10` | 独立测试集长度，底层保证至少10个点（数据不足时除外） |
| `tune_iterations` | `12` | 随机调参次数，范围限制为1～50 |
| `selection_metric` | `rmse` | 自动选模指标 |
| `candidate_models` | 默认候选集 | 可从下列候选模型中缩小范围 |

默认候选：

```text
naive, sma, ewma, holt_winters, arima, prophet,
xgb, lgbm, prophet_xgboost, prophet_lightgbm
```

## 接口示例

### 校验训练数据

```json
{
  "model_code": "MODEL_ALPHA_AUTO_V1.0",
  "agent_code": "short-term",
  "dataset": [
    {"date": "2026-01-01", "gas_sales": 5.21},
    {"date": "2026-01-02", "gas_sales": 5.38}
  ]
}
```

调用：`POST /models/validate-training-data`

### 训练

```json
{
  "model_code": "MODEL_ALPHA_AUTO_V1.0",
  "agent_code": "short-term",
  "train_batch_no": "TRBATCH-ALPHA-0001",
  "params": {
    "candidate_models": ["naive", "ewma", "holt_winters"],
    "holdout_size": 10,
    "tune_iterations": 4,
    "selection_metric": "rmse"
  },
  "dataset": [
    {"date": "2026-01-01", "gas_sales": 5.21}
  ]
}
```

调用：`POST /train`

### 预测

```json
{
  "model_code": "MODEL_ALPHA_AUTO_V1.0",
  "agent_code": "short-term",
  "train_batch_no": "TRBATCH-ALPHA-0001",
  "forecast_batch_no": "FORECAST-ALPHA-0001",
  "forecast_horizon": 3,
  "forecast_unit": "day",
  "dataset": [
    {"date": "2026-05-01"},
    {"date": "2026-05-02"},
    {"date": "2026-05-03"}
  ]
}
```

调用：`POST /predict`

预测阶段会使用 `train_batch_no` 加载：

```text
{artifact_root}/{train_batch_no}/model.joblib
```

接口不会接收或返回服务器模型文件路径。
