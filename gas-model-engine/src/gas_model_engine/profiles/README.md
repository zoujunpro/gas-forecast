# 算法 Profile

`profiles` 描述特定预测场景允许使用的算法集合，不代表完整的智能体。

一个 Profile 包含：

- Profile 编码和版本；
- 候选算法及其参数预设；
- 候选算法是否必须成功；
- 多个预测结果的默认汇总方式；
- 完整流程使用的 `scenario_code`；
- 可注册特征、回测、结果组合和选优方法的编码及参数。

当前内置 Profile：

- `short-term`：短期预测候选算法范围；
- `winter-supply`：冬季保供预测候选算法范围。
- `monthly-industry`：月度行业预测候选算法范围。

三者分别引用独立的 Feature Policy：短期按客户、冬季按地区、月度按行业解析特征。

`execution.AlgorithmProfileExecutor` 是框架内部的简单批量执行器；
`execution.ForecastPipeline` 统一负责特征、回测、组合、选优和全量重训。
上层业务统一调用 `ForecastPipeline`。本模块是确定性模型引擎，不依赖智能体。

完整预测流程统一通过 `execution.ForecastPipeline` 发起。所有场景执行同一条生产流程，
`scenario_code` 只选择数据清洗、校验和参数钩子。

- `DEFAULT_FORECAST_SCENARIO_V1` 使用公共默认场景钩子；
- `SHORT_TERM_CUSTOMER_V1` 提供短期客户清洗、校验和计划参数；
- `WINTER_SUPPLY_V1` 提供冬季保供场景钩子；
- `MONTHLY_INDUSTRY_V1` 提供月度行业场景钩子；
- `LEGACY_SHORT_TERM_RESIDUAL_V1` 提供旧短期残差场景钩子。

这些钩子不能自行实现 `execute()`；上层接口和生产预测流程不会因场景变化。

调用示例：

```python
from gas_model_engine import (
    ProfilePredictRequest,
    ProfileTrainRequest,
    create_default_profile_executor,
)

executor = create_default_profile_executor()
bundle = executor.train(ProfileTrainRequest("short-term", train_data))
result = executor.predict(ProfilePredictRequest(bundle, future_data))
```

完整流程入口：

```python
from gas_model_engine import ForecastRequest, create_default_forecast_pipeline

pipeline = create_default_forecast_pipeline()
result = pipeline.run(ForecastRequest("profile-code", train_data, future_data))
```
