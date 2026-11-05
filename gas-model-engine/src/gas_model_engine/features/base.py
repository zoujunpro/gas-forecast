from __future__ import annotations

from typing import Any, Protocol

from ..algorithms.contracts import AlgorithmDataset
from ..methods import MethodMetadata, MethodRegistry


class FeatureMethod(Protocol):
    metadata: MethodMetadata

    def transform(
        self,
        train_data: AlgorithmDataset,
        future_data: AlgorithmDataset,
        params: dict[str, Any],
    ) -> tuple[AlgorithmDataset, AlgorithmDataset]: ...


class FeatureMethodRegistry(MethodRegistry[FeatureMethod]):
    pass
