from __future__ import annotations

from ..profiles import AlgorithmProfileRegistry
from .contracts import ForecastRequest, ForecastResult
from .profile_executor import AlgorithmProfileExecutor
from .strategies import ForecastStrategyRegistry


class ForecastPipeline:
    """模型引擎面向上层业务的统一预测入口。

    根据 Profile 分派确定性策略，组织特征、回测、候选训练、合法组合、
    统一排名和最终预测；具体算法通过内部 ``AlgorithmEngine`` 执行。
    """

    def __init__(
        self,
        profile_executor: AlgorithmProfileExecutor,
        profile_registry: AlgorithmProfileRegistry,
        strategy_registry: ForecastStrategyRegistry,
    ) -> None:
        self.profile_executor = profile_executor
        self.profile_registry = profile_registry
        self.strategy_registry = strategy_registry

    def run(self, request: ForecastRequest) -> ForecastResult:
        """解析 Profile 和预测策略，并将完整执行过程委托给对应策略。"""
        profile = self.profile_registry.get(request.profile_code)
        strategy = self.strategy_registry.get(profile.strategy_code)
        return strategy.execute(request, profile, self.profile_executor)
