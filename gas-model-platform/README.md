# gas-model-platform

统一的小模型运行与管理平台。

本服务只负责 Python 模型侧能力，不承接用户、菜单、权限等业务系统功能。它面向现有三个智能体：

- `winter-supply`: 冬季保供预测
- `monthly-sales`: 月度销量预测
- `short-term`: 短期客户预测

## 核心能力

1. 模型统一接入
2. 特征工程
3. 数据集构建
4. 训练 / 回测
5. 模型评估
6. 模型择优
7. 模型产物保存与预测执行

## 本地启动

```bash
python -m venv .venv
source .venv/bin/activate
pip install -e ".[dev]"
uvicorn gas_model_platform.main:app --reload --host 0.0.0.0 --port 8090
```

## 统一响应结构

健康检查、模型列表、训练、回测、预测和特征计算接口统一返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

请求失败时仍使用相同结构，并保留相应 HTTP 状态码。例如参数错误：

```json
{
  "code": 422,
  "message": "request validation failed",
  "data": {
    "errors": []
  }
}
```

也可以直接运行入口文件，适合 PyCharm Run/Debug：

```bash
python src/gas_model_platform/main.py
```

监听地址通过公共配置中的 `server.host`、`server.port`、`server.reload`
控制，也可用 `GAS_MODEL_SERVER_HOST`、`GAS_MODEL_SERVER_PORT` 和
`GAS_MODEL_SERVER_RELOAD` 覆盖。

## 日志配置

项目公共配置文件为 `config/application.yaml`，作用类似 Spring Boot
`application.yml`。应用启动时读取一次，环境变量的优先级高于 YAML。

支持类似 Spring Profiles 的多环境配置：

- `config/application-dev.yaml`：开发环境
- `config/application-test.yaml`：测试环境
- `config/application-prod.yaml`：生产环境

启动时通过 `GAS_MODEL_PROFILE` 指定环境：

```bash
GAS_MODEL_PROFILE=dev uvicorn gas_model_platform.main:app --reload --port 8090
GAS_MODEL_PROFILE=prod uvicorn gas_model_platform.main:app --host 0.0.0.0 --port 8090
```

加载顺序为：公共配置 → 环境配置 → 环境变量。其中后加载的配置覆盖前面的配置。

服务使用 Python 标准库 `logging.config.dictConfig` 统一配置日志，效果类似
Java Logback。默认同时输出到控制台和
`logs/gas-model-platform.log`，每天午夜滚动并保留 30 天。

`dev` 和 `test` 环境默认只输出控制台日志，不创建日志文件；`prod`
环境同时输出控制台日志和按天滚动的文件日志。可通过
`logging.console_enabled`、`logging.file_enabled` 或对应环境变量调整。

```bash
export GAS_MODEL_LOG_LEVEL=INFO
export GAS_MODEL_LOG_DIR=/data/gas-model-logs
export GAS_MODEL_LOG_RETENTION_DAYS=30
export GAS_MODEL_LOG_REQUEST_BODY_ENABLED=true
export GAS_MODEL_LOG_RESPONSE_BODY_ENABLED=true
export GAS_MODEL_LOG_BODY_MAX_LENGTH=20000
export GAS_MODEL_LOG_BODY_MAX_ITEMS=20
```

也可以用 `GAS_MODEL_CONFIG=/path/to/application.yaml` 指定外部配置文件。

日志包含进程、线程、请求 ID、调用方、模块、函数和行号。HTTP 日志同时记录
请求方法、路径、查询参数、状态码、耗时、入参和出参。调用方优先读取
`X-User-Id` 或 `X-User` 请求头，没有时记录客户端 IP；响应头会返回
`X-Request-Id`。密码、Token、Authorization、API Key 等字段自动脱敏；
长数组和超长报文按照 `body_max_items`、`body_max_length` 配置摘要和截断。

## 接口

- `GET /health`
- `GET /api/v1/models`
- `POST /api/v1/features/compute`
- `POST /api/v1/features/batch-compute`
- `POST /api/v1/train`
- `POST /api/v1/backtest`
- `POST /api/v1/predict`

## 特征计算接口

单日期计算：

```json
{
  "agent_code": "winter-supply",
  "target_date": "2026-11-01",
  "region_code": "hebei",
  "input_row": {
    "avg_temp": 4.2,
    "max_temp": 9.1,
    "min_temp": -1.3
  },
  "history_dataset": [
    {
      "stat_date": "2026-10-31",
      "gas_sales": 1200
    }
  ],
  "params": {
    "lag_days": [1, 7, 30],
    "hdd_base_temperature": 18
  }
}
```

多日期批量计算：

```json
{
  "agent_code": "short-term",
  "target_dates": ["2026-09-01", "2026-09-02", "2026-09-03"],
  "customer_code": "C001",
  "input_rows": [
    {
      "stat_date": "2026-09-01",
      "avg_temp": 27.5
    }
  ],
  "history_dataset": []
}
```

所有模型都通过 `ModelHandler` 适配：

```python
class ModelHandler(Protocol):
    def train(self, context: ModelContext) -> TrainResult: ...
    def backtest(self, context: ModelContext) -> BacktestResult: ...
    def predict(self, context: ModelContext) -> PredictResult: ...
```

## 按模型编号路由

Java 侧传 `modelCode` 和对应业务参数。注册中心以 `modelCode` 为唯一键，
直接映射到相应的模型处理器：

```json
{
  "modelCode": "WINTER_MODEL_001",
  "forecast_horizon": 15,
  "forecast_unit": "tenday",
  "dataset": []
}
```

`agent_code` 是处理器内部的业务分类，不再作为训练、回测和预测接口的路由主键。
各城市、候选模型、产物路径及训练配置等差异由具体处理器读取
`region_code`、`region_name` 和 `params` 后自行处理。

冬季保供对外统一使用 `modelCode=WINTER_MODEL_001`。内部当前包含 30 个
候选模型；训练时在独立评估回测季上按 MAPE、WMAPE 排序，同等精度优先
选择结构更简单的单模型。训练响应中的 `selected_model_name`、
`selection_reason`、`metrics`、`selected_model_params` 和
`candidate_evaluations` 可直接用于 Java 侧记录最佳模型及选择依据。

当前迁移内容：

- 冬供总入口：`src/gas_model_platform/models/winter_supply/handler.py`
- 冬供训练与预测算法：`src/gas_model_platform/models/winter_supply/engine/`
- 冬供结果仓库：`src/gas_model_platform/models/winter_supply/result_store.py`
- 已迁移结果数据：`src/gas_model_platform/resources/winter_supply_data/*.json`

请求带 `dataset` 时会运行真实的训练、滚动回测或预测算法；不带数据时仍读取迁移的 JSON。训练成功后，Java 保存响应中的 `train_batch_no`；预测时通过顶层 `train_batch_no` 传回训练批次，平台根据模型编号和批次定位模型产物。省份信息保存在产物元数据中并在预测时校验，外部接口不接收或返回模型文件路径：

```json
{
  "modelCode": "WINTER_MODEL_001",
  "region_name": "河北",
  "forecast_horizon": 15,
  "forecast_unit": "tenday",
  "train_batch_no": "WGTRAIN-xxxxxxxxxxxxxxxx",
  "dataset": [
    {
      "date": "2026-11-01",
      "avg_temp": 5.1,
      "max_temp": 10.2,
      "min_temp": 0.3,
      "HDD": 129,
      "extreme_cold_days": 0
    }
  ]
}
```
