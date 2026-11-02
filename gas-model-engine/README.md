# Gas Model Engine

与业务场景无关的预测算法库。算法只消费已经完成特征处理的标准数据，统一提供：

- `fit`
- `predict`
- `save` / `load`
- `get_default_params`
- `validate_params`
- `validate_input`
- `get_capabilities`

短期客户预测、冬季保供等业务的特征、候选配置和回测策略不属于本模块。

```bash
python -m pip install -e '.[dev]'
pytest
```
