"""新模型 Handler 模板。

复制到对应智能体目录，替换 EXAMPLE 相关名称，并实现三个业务方法。
模板文件位于 docs，不会被平台注册。
"""

from gas_model_platform.schemas.modeling import (
    BacktestResult,
    ModelContext,
    ModelInfo,
    PredictResult,
    TrainResult,
)


class ModelExampleV1Handler:
    info = ModelInfo(
        agent_code="short-term",
        model_code="MODEL_EXAMPLE_V1",
        model_version="1.0.0",
        model_name="示例模型 V1",
        description="请填写模型用途、数据频率和核心算法。",
        capabilities=["train", "backtest", "predict"],
    )

    def train(self, context: ModelContext) -> TrainResult:
        """校验数据、执行原始训练逻辑、保存产物并返回回测明细。"""
        raise NotImplementedError

    def backtest(self, context: ModelContext) -> BacktestResult:
        """执行与训练一致的回测逻辑并返回实际值/预测值。"""
        raise NotImplementedError

    def predict(self, context: ModelContext) -> PredictResult:
        """通过 train_batch_no 加载产物，仅执行预测。"""
        raise NotImplementedError
