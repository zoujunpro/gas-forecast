# 模型接入标准

本文是 `gas-model-platform` 的强制接入契约。后续模型必须按本标准实现、测试和注册。

## 1. 基本原则

1. 一个对外 `model_code` 只对应一个 Handler。
2. 除冬季智能体外，一个模型、一个版本、一个 Python 实现文件。
3. 原模型迁移时必须保持业务算法不变；接口适配、产物保存和日志属于框架层。
4. 所有模型必须提供训练、回测、预测三个能力，并返回平台统一对象。
5. Java 或前端不接触模型文件路径，只保存并回传 `model_code + train_batch_no`。

## 2. 命名和目录

普通模型统一使用：

```text
model_code: MODEL_<业务名称>_V<主版本>.<次版本>
文件名:    model_<业务名称小写>_v<主版本>.py
类名:      Model<业务名称驼峰>V<主版本>Handler
```

示例：

```text
MODEL_JIANGSHU_DIANLI_V1.0
models/short_agent/model_jiangshu_dianli_v1.py
ModelJiangshuDianliV1Handler
```

`model_version` 使用语义化版本 `x.y.z`。算法、特征或选模规则不兼容时升级主版本；兼容性能力增加升级次版本；缺陷修复升级修订版本。

冬季智能体是唯一允许保留多文件 `engine/` 结构的现有例外。分类目录为
`winter_agent`，V1模型包为 `winter_agent/winter_agent_v1`，对外编号为
`WINTER_MODEL_V1.0`。
如果增加新的 `agent_code` 分类，还必须同步扩展 `schemas/modeling.py` 中的
`AgentCode`，不能由单个模型私自定义。

## 3. Handler契约

每个 Handler 必须提供：

```python
class ExampleHandler:
    info: ModelInfo

    def validate_training_data(self, context: ModelContext) -> TrainingDataValidationResult: ...
    def train(self, context: ModelContext) -> TrainResult: ...
    def backtest(self, context: ModelContext) -> BacktestResult: ...
    def predict(self, context: ModelContext) -> PredictResult: ...
```

`info.capabilities` 必须包含 `train`、`backtest`、`predict`。具备 `train`
能力的模型还必须在 `info.training_data_range` 中声明训练所需的数据范围，并
实现 `validate_training_data`。注册中心会在启动时自动校验编号、版本、方法
完整性和数据范围声明。正式训练前，平台会强制执行同一校验。

`agent_code` 必须与 Java 智能体枚举的第一个参数完全一致：

- `winter-supply`
- `monthly-sales`
- `short-term`

训练数据范围只描述时间跨度，不描述字段要求。支持两种类型：

- `history_length`：按日、旬或月声明最少和建议历史长度。
- `complete_period`：声明至少需要几个完整业务周期，以及首个周期前的历史条数。

示例：

```python
training_data_range=TrainingDataRange(
    type="history_length",
    frequency="day",
    minimum=880,
    recommended=1060,
    continuous=True,
    description="至少需要880天连续日数据，建议提供1060天以上。",
)
```

## 4. 请求字段

公共字段只能放在请求顶层：

| 字段 | 用途 | 规则 |
|---|---|---|
| `model_code` | 唯一模型路由键 | 必填 |
| `train_batch_no` | 训练产物批次 | 训练、预测必填，禁止放入 `params` |
| `forecast_batch_no` | 本次预测任务批次 | 预测必填，由调用方生成，响应原样返回 |
| `forecast_horizon` | 预测期数 | 预测必填 |
| `forecast_unit` | `day`、`tenday`、`month` | 必须与模型一致 |
| `region_code/region_name` | 地区维度 | 仅业务需要时校验 |
| `dataset` | 训练或预测数据 | 使用 JSON 记录数组 |
| `params` | 模型专属可选参数 | 不得放公共字段或文件路径 |

训练 `dataset` 中日期字段统一使用 `date`，目标值字段统一使用 `gas_sales`。
模型原算法使用其他内部字段名（例如 `y`）时，由 Handler 边界适配层完成转换，
不得因此修改原模型的核心算法逻辑。

训练与预测数据行使用固定实体：

- `TrainDatasetRow`：固定包含 `date`、`gas_sales`，其余同级字段为模型动态特征。
- `PredictDatasetRow`：固定包含 `date`，其余同级字段为模型未来动态特征；不要求提供 `gas_sales`。
- 动态特征保持扁平结构，不再额外包装为 `features` 对象。

模型可以在边界层接受字段别名，但进入算法前必须转换成模型内部唯一的标准字段。字段是否必填、单位、频率和缺失值规则必须写入模型文档并由代码校验。

## 5. 统一返回

### 训练

必须返回 `TrainResult`，至少正确填写：

- `agent_code`、`model_code`、`train_batch_no`
- `metrics`、`feature_names`
- `selected_model_name`、`selection_reason`
- `selected_model_params`
- `candidate_evaluations`
- `rolling_backtest_results`：包含日期、实际值、预测值和误差
- `rolling_backtest_fold_metrics`
- `issues` 和必要的 `metadata`

### 回测

必须返回 `BacktestResult`。`points` 必须包含 `stat_date`、`actual_value` 和 `predicted_value`，供页面直接绘制实际值/预测值对比图。

### 预测

必须返回 `PredictResult`。顶层统一返回调用方传入的 `forecast_batch_no`；预测点统一使用
`forecast_date`、`prediction`、`lower_value`、`upper_value`。预测阶段没有真实值，
因此不得返回 MAPE、WMAPE、sMAPE、RMSE、MAE、R2 等评估指标。

所有HTTP接口继续由平台包装为：

```json
{"code": 0, "message": "success", "data": {}}
```

## 6. 模型产物

产物固定保存到：

```text
{artifact_root}/{train_batch_no}/model.joblib
```

要求：

1. `train_batch_no` 在整个平台必须全局唯一，同一训练批次禁止覆盖。
2. 批次号只能包含字母、数字、点、下划线和中划线。
3. 产物必须记录 `model_version` 和 `artifact_version`。
4. 加载时校验产物类型和版本。
5. 训练响应返回产物 SHA-256，不返回服务器文件路径。
6. 预测通过 `train_batch_no` 定位产物，`model_code` 只负责选择处理器并校验产物类型。


## 7. 迁移要求

迁移已有模型时，应把原脚本分成两层：

- 算法层：数据清洗、特征、切分、调参、训练、选模、预测公式。
- 适配层：JSON输入、统一结果、日志、异常翻译、产物保存和注册。

算法层默认不得修改。确需修改时，必须在迁移说明中逐项列出，并增加“原模型与迁移模型同输入对比”的黄金测试。随机算法必须显式保存或固定随机种子。

## 8. 日志与异常

1. 业务校验错误使用中文 `ValueError`，说明具体字段和期望值。
2. 不得静默吞掉训练异常；可降级的候选模型异常写入 `ModelIssue`。
3. 第三方英文警告应翻译为中文后返回，同时保留 `category`。
4. 使用平台日志，不使用 `print`。
5. 不记录密码、Token、完整超大数据集或模型二进制内容。

## 9. 注册

实现完成后在 `models/registry.py` 显式注册：

```python
MODEL_REGISTRY = {
    "MODEL_EXAMPLE_V1.0": ModelExampleV1Handler(),
}
```

不得根据文件名动态扫描注册，避免未审核模型意外上线。

## 10. 必须具备的测试

每个模型至少提供：

1. 注册和元数据契约测试。
2. 字段别名及必填字段测试。
3. 最小训练流水线测试。
4. 回测实际值/预测值对比测试。
5. 产物保存、加载和版本不兼容测试。
6. 预测日期、频率、长度测试。
7. 重复批次禁止覆盖测试。
8. 原模型迁移的黄金结果对比测试。

合并前必须通过：

```bash
.venv/bin/python -m compileall -q src
.venv/bin/python -m pytest -q
```

## 11. 接入检查清单

- [ ] 编号、文件名、类名和版本符合规范
- [ ] 三个 Handler 方法均已实现
- [ ] 输入字段、单位、频率和缺失规则已明确
- [ ] 训练返回选模依据和滚动回测对比数据
- [ ] 产物可保存、加载并通过批次预测
- [ ] 告警和错误可在接口中看到且为中文
- [ ] 已显式注册
- [ ] 契约测试、算法测试和迁移黄金测试全部通过
