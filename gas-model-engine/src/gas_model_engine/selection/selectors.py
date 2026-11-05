from __future__ import annotations

import math
from dataclasses import dataclass
from typing import Any, Protocol

from ..methods import MethodMetadata, MethodRegistry


@dataclass(frozen=True)
class CandidateScore:
    id: str
    candidate_type: str
    metrics: dict[str, float]
    constituents: tuple[str, ...] = ()


class Selector(Protocol):
    metadata: MethodMetadata

    def rank(
        self, candidates: list[CandidateScore], params: dict[str, Any]
    ) -> list[CandidateScore]: ...


class SelectorRegistry(MethodRegistry[Selector]):
    pass


class MinMetricSelector:
    metadata = MethodMetadata("MIN_METRIC_SELECTOR_V1", "最小误差选择器")

    def rank(self, candidates, params):
        metric = str(params.get("metric", "mape")).lower()
        if not candidates:
            raise ValueError("没有可参与选择的候选模型")
        if any(metric not in candidate.metrics for candidate in candidates):
            raise ValueError(f"部分候选缺少选择指标: {metric}")
        return sorted(
            candidates,
            key=lambda item: (
                item.metrics[metric]
                if math.isfinite(float(item.metrics[metric]))
                else float("inf"),
                item.id,
            ),
        )


def create_default_selector_registry() -> SelectorRegistry:
    return SelectorRegistry([MinMetricSelector()])
