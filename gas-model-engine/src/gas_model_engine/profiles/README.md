# 算法 Profile

`profiles` 描述特定预测场景允许使用的算法集合，不代表完整的智能体。

一个 Profile 包含：

- Profile 编码和版本；
- 候选算法及其参数预设；
- 候选算法是否必须成功；
- 多个预测结果的默认汇总方式；
- 完整流程使用的 `strategy_code`；
- 可注册特征、回测、结果组合和选优方法的编码及参数。

当前内置 Profile：

- `short-term`：短期预测候选算法范围；
- `winter-supply`：冬季保供预测候选算法范围。
- `monthly-industry`：月度行业预测候选算法范围。

三者分别引用独立的 Feature Policy：短期按客户、冬季按地区、月度按行业解析特征。

`execution.AlgorithmProfileExecutor` 是框架内部的简单批量执行器；
`execution.ForecastPipeline` 的配置驱动策略负责特征、回测、组合、选优和全量重训。
上层业务统一调用 `ForecastPipeline`。本模块是确定性模型引擎，不依赖智能体。

完整预测流程统一通过 `execution.ForecastPipeline` 发起，但 Pipeline 只根据
`strategy_code` 分派流程，不擅自改变业务模型的回测、组合或选优逻辑。

- `DIRECT_PROFILE_V1` 是显式简单策略，只训练候选并按固定规则汇总；
- `CONFIG_DRIVEN_FORECAST_V1` 是可复用的公共配置驱动模板；
- `SHORT_TERM_CUSTOMER_V1` 是短期客户策略，复用公共模板并提供短期默认执行计划；
- `WINTER_SUPPLY_V1` 是冬季保供独立策略，必须等价迁移冬季原流程；
- `LEGACY_SHORT_TERM_RESIDUAL_V1` 为旧短期残差流程保留独立迁移位置。

所有策略统一接收 `ForecastRequest` 并返回 `ForecastResult`，上层接口不会因场景变化。

冬季旧流程尚未完成迁移时会明确抛出 `NotImplementedError`，不会回退到简单平均、
Top-K 或其他通用组合。

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
