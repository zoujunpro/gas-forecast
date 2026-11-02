# Algorithms 算法模块

`algorithms` 是与业务场景无关的算法内核。短期客户预测、冬季保供、城市燃气等业务可以使用本模块，但业务名称、数据查询、业务特征和训练策略不应写入本模块。

## 1. 模块职责

本模块负责：

- 统一算法训练、预测、保存和加载接口；
- 统一算法参数及输入数据校验；
- 声明算法所属技术簇和能力；
- 注册和解析算法；
- 提供原子算法；
- 提供组合算法；
- 校验组合算法依赖关系。

本模块不负责：

- 查询城市、客户、行业等业务数据；
- 构造天气、节假日、滞后和滚动等业务特征；
- 划分回测窗口；
- 生成 OOF 预测；
- 从候选模型中选择 Top-K；
- 决定某个业务场景使用哪些算法。

这些能力应由特征层、回测层、选型层和模型定义层完成。

## 2. 目录结构

```text
algorithms/
├── base.py                  # 统一算法抽象类
├── contracts.py             # 数据、模型、结果、能力和校验对象
├── registry.py              # 算法注册中心及依赖校验
├── baseline/                # 基线算法
├── statistical/             # 统计时序算法
├── linear/                  # 线性及稳健回归算法
├── tree/                    # 树模型及 Boosting 算法
└── composite/               # 组合算法
```

## 3. 公共接口

所有原子算法和组合算法都继承 `BaseAlgorithm`，对上层暴露相同能力：

```python
class BaseAlgorithm(ABC):
    metadata
    fit(train_data, params)
    predict(model, future_data)
    save(model, path)
    load(path)
    get_default_params()
    validate_params(params)
    validate_input(data, stage)
    get_capabilities()
    dependencies(params=None)
```

### fit

训练算法并返回统一的 `AlgorithmModel`：

```python
model = algorithm.fit(train_data, params)
```

算法只接收特征层已经处理好的标准数据，不在 `fit` 中查询业务数据或构造业务特征。

### predict

使用训练产物生成预测：

```python
result = algorithm.predict(model, future_data)
```

返回统一的 `PredictionResult`，其中包含预测值和可选的上下界。

### save / load

默认使用 `joblib` 保存统一的 `AlgorithmModel`。特殊算法可以覆盖这两个方法。

### validate_params

完成参数类型、范围和必填项校验，并返回补齐默认值后的参数。

### validate_input

校验训练和预测输入，例如：

- 特征、日期和目标长度是否一致；
- 训练目标是否存在；
- 日期是否重复；
- 样本数量是否满足算法要求。

### get_capabilities

声明算法能力，例如：

- 是否支持外生特征；
- 是否支持预测区间；
- 是否支持样本权重；
- 是否支持缺失值；
- 最小训练样本数。

## 4. 标准数据对象

算法输入统一使用 `AlgorithmDataset`：

```python
AlgorithmDataset(
    features=pd.DataFrame(...),
    target=pd.Series(...),       # 预测阶段可以为 None
    dates=pd.DatetimeIndex(...),
    sample_weight=None,
    metadata={},
)
```

字段含义：

| 字段 | 含义 |
|---|---|
| `features` | 特征层输出的特征矩阵 |
| `target` | 训练目标，预测阶段允许为空 |
| `dates` | 每行数据对应的日期 |
| `sample_weight` | 可选样本权重 |
| `metadata` | OOF 预测等附加运行信息 |

算法不能依赖 `region_code`、`customer_code` 等业务字段。

## 5. 原子算法

当前默认注册 30 个去重后的原子算法。

### 5.1 基线算法

| 算法编码 | 说明 |
|---|---|
| `NAIVE_V1` | 使用最后一个观测值预测 |
| `SEASONAL_NAIVE_V1` | 使用上一季节对应值预测 |
| `SEASONAL_MEAN_V1` | 多个历史季节同期值取均值 |
| `SEASONAL_TREND_V1` | 季节同期值加趋势修正 |
| `MOVING_AVERAGE_V1` | 最近 N 期移动平均 |
| `EWMA_V1` | 指数加权移动平均 |

`RecentMean3`、`RecentMean6` 应使用 `MOVING_AVERAGE_V1` 的不同 `window` 参数表示。

### 5.2 统计时序算法

| 算法编码 | 说明 |
|---|---|
| `AUTOREGRESSION_V1` | 自回归模型 |
| `ARIMA_V1` | ARIMA 模型 |
| `SARIMAX_V1` | 季节 ARIMA，支持可选外生变量 |
| `EXPONENTIAL_SMOOTHING_V1` | 指数平滑、ETS、Holt-Winters |
| `THETA_V1` | Theta 时序模型 |
| `UNOBSERVED_COMPONENTS_V1` | 不可观测成分状态空间模型 |
| `PROPHET_V1` | Prophet 趋势和季节模型 |

ETS 和 Holt-Winters 统一由 `EXPONENTIAL_SMOOTHING_V1` 通过参数区分。

### 5.3 线性及稳健回归算法

| 算法编码 | 说明 |
|---|---|
| `LINEAR_REGRESSION_V1` | 普通线性回归 |
| `RIDGE_V1` | L2 正则回归 |
| `LASSO_V1` | L1 正则回归 |
| `ELASTIC_NET_V1` | L1 与 L2 混合正则 |
| `HUBER_V1` | Huber 稳健回归 |
| `BAYESIAN_RIDGE_V1` | 贝叶斯岭回归 |
| `PLS_V1` | 偏最小二乘回归 |
| `LINEAR_SVR_V1` | 线性支持向量回归 |

`Ridge` 和 `Ridge_Strong` 是同一个算法的参数变体：

```python
{"code": "RIDGE_V1", "params": {"alpha": 10}}
{"code": "RIDGE_V1", "params": {"alpha": 100}}
```

### 5.4 树模型及 Boosting 算法

| 算法编码 | 说明 |
|---|---|
| `DECISION_TREE_V1` | 决策树回归 |
| `RANDOM_FOREST_V1` | 随机森林 |
| `EXTRA_TREES_V1` | 极端随机树 |
| `GRADIENT_BOOSTING_V1` | 梯度提升树 |
| `ADABOOST_V1` | AdaBoost 回归 |
| `BAGGING_TREE_V1` | Bagging 决策树 |
| `LIGHTGBM_V1` | LightGBM 回归 |
| `XGBOOST_V1` | XGBoost 回归 |
| `CATBOOST_V1` | CatBoost 回归 |

`RandomForest_Shallow`、`ExtraTrees_Shallow` 和 `GradientBoosting_LowLR` 是参数配置，不是独立算法。

## 6. 组合算法

组合算法与原子算法平级注册，也实现相同的 `fit` 和 `predict` 接口；区别在于组合算法内部通过 `AlgorithmRegistry` 调用成员算法。

| 算法编码 | 说明 | 是否需要 OOF |
|---|---|---:|
| `WEIGHTED_AVERAGE_V1` | 等权或固定权重平均 | 否 |
| `MEDIAN_ENSEMBLE_V1` | 取成员预测中位数 | 否 |
| `WINSORIZED_AVERAGE_V1` | 缩尾后计算平均值 | 否 |
| `INVERSE_ERROR_WEIGHT_V1` | 按 OOF 误差倒数学习权重 | 是 |
| `NNLS_WEIGHT_V1` | 非负最小二乘学习权重 | 是 |
| `RIDGE_STACKING_V1` | 使用 Ridge 学习二层组合关系 | 是 |
| `RESIDUAL_COMBINATION_V1` | 基础预测加残差预测 | 否 |

### 6.1 等权与固定权重

不传权重时为等权组合：

```python
params = {
    "members": [
        {"code": "PROPHET_V1"},
        {"code": "LIGHTGBM_V1"},
    ]
}
```

指定权重：

```python
params = {
    "members": [
        {"code": "PROPHET_V1"},
        {"code": "LIGHTGBM_V1"},
    ],
    "weights": {
        "PROPHET_V1": 0.4,
        "LIGHTGBM_V1": 0.6,
    },
}
```

### 6.2 同一算法的多个参数变体

成员 `id` 用于区分同一算法的不同参数配置：

```python
params = {
    "members": [
        {
            "id": "ridge_regular",
            "code": "RIDGE_V1",
            "params": {"alpha": 10},
        },
        {
            "id": "ridge_strong",
            "code": "RIDGE_V1",
            "params": {"alpha": 100},
        },
    ],
    "weights": {
        "ridge_regular": 0.6,
        "ridge_strong": 0.4,
    },
}
```

### 6.3 学习型组合与 OOF

以下组合算法必须使用 Walk-forward 或其他时序回测产生的 OOF 预测学习权重：

```text
INVERSE_ERROR_WEIGHT_V1
NNLS_WEIGHT_V1
RIDGE_STACKING_V1
```

调用方通过 `AlgorithmDataset.metadata` 提供：

```python
train_data.metadata["member_oof_predictions"] = {
    "prophet": prophet_oof,
    "lightgbm": lightgbm_oof,
}
```

成员配置中的 `id` 必须与 OOF 字典的键一致：

```python
params = {
    "members": [
        {"id": "prophet", "code": "PROPHET_V1"},
        {"id": "lightgbm", "code": "LIGHTGBM_V1"},
    ]
}
```

禁止使用基础模型对训练集的直接拟合结果训练权重，否则会造成数据泄漏。

### 6.4 残差组合

残差组合表达：

```text
最终预测 = 基础算法预测 + 残差算法预测
```

例如 Prophet 加 LightGBM 残差：

```python
params = {
    "base": {
        "code": "PROPHET_V1",
        "params": {},
    },
    "residual": {
        "code": "LIGHTGBM_V1",
        "params": {},
    },
}
```

同一个组合算法也可表达 Prophet 加 AR 或 Prophet 加 XGBoost。

## 7. 注册与调用

创建默认注册中心：

```python
from gas_model_engine import create_default_registry

registry = create_default_registry()
algorithm = registry.get("RIDGE_V1")
```

训练：

```python
validation = algorithm.validate_params({"alpha": 10})
if not validation.valid:
    raise ValueError(validation.errors)

model = algorithm.fit(
    train_data,
    validation.normalized_params,
)
```

预测：

```python
result = algorithm.predict(model, future_data)
values = result.values
```

保存和加载：

```python
algorithm.save(model, "artifacts/model.joblib")
loaded = algorithm.load("artifacts/model.joblib")
```

查看全部算法：

```python
for metadata in registry.list_metadata():
    print(metadata.code, metadata.family, metadata.kind)
```

## 8. 新增算法规范

新增原子算法时：

1. 继承 `BaseAlgorithm`；
2. 定义唯一的 `AlgorithmMetadata`；
3. 实现 `fit` 和 `predict`；
4. 提供合理的默认参数；
5. 实现参数范围校验；
6. 声明算法能力；
7. 在默认注册入口注册；
8. 添加训练、预测、保存和加载测试。

示例：

```python
class ExampleAlgorithm(BaseAlgorithm):
    metadata = AlgorithmMetadata(
        code="EXAMPLE_V1",
        name="示例算法",
        family=AlgorithmFamily.LINEAR,
    )

    def get_default_params(self):
        return {"alpha": 1.0}

    def fit(self, train_data, params):
        ...

    def predict(self, model, future_data):
        ...
```

## 9. 依赖安装

基础依赖：

```bash
python -m pip install -e .
```

安装 Boosting 算法依赖：

```bash
python -m pip install -e '.[boosting]'
```

安装 Prophet：

```bash
python -m pip install -e '.[prophet]'
```

安装全部依赖和测试工具：

```bash
python -m pip install -e '.[all]'
```
