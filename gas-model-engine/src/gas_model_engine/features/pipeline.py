from __future__ import annotations

import numpy as np

from ..algorithms.contracts import AlgorithmDataset
from ..profiles.contracts import MethodSpec
from .base import FeatureMethodRegistry
from .methods import create_default_feature_registry


class ConfiguredFeatureProcessor:
    """按配置顺序解析特征方法；可选方法失败时记录原因并继续。"""

    def __init__(self, registry: FeatureMethodRegistry | None = None) -> None:
        self.registry = registry or create_default_feature_registry()

    def prepare(
        self,
        train_data: AlgorithmDataset,
        future_data: AlgorithmDataset,
        specs: tuple[MethodSpec, ...],
    ) -> tuple[AlgorithmDataset, AlgorithmDataset]:
        train, future = train_data, future_data
        applied: list[str] = []
        skipped: dict[str, str] = {}
        for spec in specs:
            try:
                train, future = self.registry.get(spec.code).transform(
                    train, future, dict(spec.params)
                )
                applied.append(spec.code)
            except Exception as exc:
                if spec.required:
                    raise RuntimeError(f"必需特征方法执行失败: {spec.code}: {exc}") from exc
                skipped[spec.code] = str(exc)
        warmup_columns = [
            column
            for column in train.features.columns
            if column.startswith("target_") or column in {"hdd_lag1"}
        ]
        if warmup_columns:
            valid = np.isfinite(train.features[warmup_columns].to_numpy(dtype=float)).all(axis=1)
            train = self._filter_rows(train, valid)
        train.metadata.update({"applied_features": applied, "skipped_features": skipped})
        future.metadata.update({"applied_features": applied, "skipped_features": skipped})
        return train, future

    @staticmethod
    def _filter_rows(data: AlgorithmDataset, mask) -> AlgorithmDataset:
        """删除特征预热期，保持日期、目标和样本权重严格对齐。"""
        indexes = np.flatnonzero(mask)
        return AlgorithmDataset(
            features=data.features.iloc[indexes].reset_index(drop=True),
            dates=data.dates[indexes],
            target=None
            if data.target is None
            else data.target.iloc[indexes].reset_index(drop=True),
            sample_weight=None
            if data.sample_weight is None
            else data.sample_weight.iloc[indexes].reset_index(drop=True),
            metadata=dict(data.metadata),
        )
