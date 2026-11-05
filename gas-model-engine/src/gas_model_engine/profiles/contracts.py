from __future__ import annotations

from dataclasses import dataclass, field
from enum import StrEnum
from typing import Any


class AggregationMode(StrEnum):
    MEAN = "MEAN"
    MEDIAN = "MEDIAN"


@dataclass(frozen=True)
class MethodSpec:
    """一个可注册方法的引用；编码标识实现，params描述本次用法。"""

    code: str
    params: dict[str, Any] = field(default_factory=dict)
    required: bool = False


@dataclass(frozen=True)
class BacktestSpec:
    """回测方法及动态窗口参数。"""

    code: str = "EXPANDING_WALK_FORWARD_V1"
    params: dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class CombinationSpec:
    """结果组合方法；top_k等变化属于参数，不产生新的方法编码。"""

    code: str
    params: dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class SelectionSpec:
    """最终模型选择方法。"""

    code: str = "MIN_METRIC_SELECTOR_V1"
    params: dict[str, Any] = field(default_factory=lambda: {"metric": "mape"})


@dataclass(frozen=True)
class CandidateSpec:
    id: str
    algorithm_code: str
    params: dict[str, Any] = field(default_factory=dict)
    required: bool = False
    search_space: dict[str, tuple[Any, ...] | list[Any]] = field(default_factory=dict)


@dataclass(frozen=True)
class AlgorithmProfile:
    """一个业务场景可用的算法范围、参数预设和结果汇总规则。"""

    code: str
    version: str
    candidates: tuple[CandidateSpec, ...]
    aggregation: AggregationMode
    strategy_code: str = "DIRECT_PROFILE_V1"
    feature_policy_code: str | None = None
    features: tuple[MethodSpec, ...] = ()
    backtest: BacktestSpec = field(default_factory=BacktestSpec)
    combinations: tuple[CombinationSpec, ...] = ()
    selection: SelectionSpec = field(default_factory=SelectionSpec)
