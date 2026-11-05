from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

import numpy as np
import pandas as pd


@dataclass(frozen=True)
class BenchmarkDataset:
    """新旧预测实现共享的验收样本。

    ``payload`` 原样传给两侧适配器，可以放旧代码所需的 DataFrame，或已经
    构造好的 ForecastRequest。真实值只用于评分，不能被预测适配器用于训练。
    """

    dates: pd.DatetimeIndex
    actual: np.ndarray
    fold_ids: np.ndarray
    payload: Any = None

    def __post_init__(self) -> None:
        dates = pd.DatetimeIndex(pd.to_datetime(self.dates)).normalize()
        actual = np.asarray(self.actual, dtype=float).reshape(-1)
        fold_ids = np.asarray(self.fold_ids).reshape(-1)
        if not len(dates) or len(dates) != len(actual) or len(actual) != len(fold_ids):
            raise ValueError("dates、actual、fold_ids必须长度相同且不能为空")
        if dates.has_duplicates:
            raise ValueError("验收日期不能重复")
        if not np.isfinite(actual).all():
            raise ValueError("actual必须全部为有限数值")
        object.__setattr__(self, "dates", dates)
        object.__setattr__(self, "actual", actual)
        object.__setattr__(self, "fold_ids", fold_ids)


@dataclass(frozen=True)
class ModelBenchmark:
    """单个预测实现的性能和效果结果。"""

    name: str
    metrics: dict[str, float]
    fold_metrics: dict[str, dict[str, float]]
    error_std: float
    spring_festival_metrics: dict[str, float] | None
    elapsed_seconds: float
    predictions: np.ndarray = field(repr=False)


@dataclass(frozen=True)
class BenchmarkReport:
    """同一验收样本上旧实现和新 Pipeline 的公平对比报告。"""

    old: ModelBenchmark
    new: ModelBenchmark
    metric_delta: dict[str, float]
    sample_count: int
    fold_count: int
    spring_festival_sample_count: int
