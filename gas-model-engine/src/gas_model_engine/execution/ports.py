from __future__ import annotations

from typing import Any, Protocol

from ..algorithms.contracts import AlgorithmDataset
from ..profiles import AlgorithmProfile
from .contracts import BacktestResult


class FeatureProcessor(Protocol):
    """特征层扩展接口；后续可接入时间、天气和业务特征处理。"""

    def prepare(
        self,
        train_data: AlgorithmDataset,
        future_data: AlgorithmDataset,
    ) -> tuple[AlgorithmDataset, AlgorithmDataset]: ...


class BacktestRunner(Protocol):
    """Walk-forward 回测接口；负责生成指标和无数据泄漏的 OOF 预测。"""

    def run(self, profile: AlgorithmProfile, train_data: AlgorithmDataset) -> BacktestResult: ...


class CombinationBuilder(Protocol):
    """按照具体策略生成允许参与比较的组合模型，不能擅自加入新组合。"""

    def build(self, profile: AlgorithmProfile, backtest: BacktestResult) -> tuple[Any, ...]: ...


class FinalModelSelector(Protocol):
    """在策略允许的原子模型和组合模型中选择最终模型。"""

    def select(self, candidates: tuple[Any, ...]) -> Any: ...
