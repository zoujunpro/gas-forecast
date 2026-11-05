from __future__ import annotations

from dataclasses import dataclass

import numpy as np

from ..algorithms.contracts import AlgorithmDataset


@dataclass(frozen=True)
class OOFResidualDataset:
    """基础模型样本外预测形成的残差训练集。"""

    dataset: AlgorithmDataset
    base_predictions: np.ndarray
    source_indices: np.ndarray
    fold_count: int


class WalkForwardOOFResidualBuilder:
    """使用扩展窗口生成无训练期泄漏的基础模型 OOF 残差。

    本类只实现公共切分和收集逻辑；使用哪个基础算法以及内部折数由调用方
    参数决定，因此短期、冬季和月度策略均可复用。
    """

    def build(self, train_data, base_algorithm, base_params, params=None):
        options = dict(params or {})
        row_count = len(train_data.features)
        requested_folds = int(options.get("folds", 3))
        algorithm_minimum = base_algorithm.get_capabilities().minimum_training_rows
        minimum_train = max(int(options.get("minimum_train_size", 30)), algorithm_minimum)
        available = row_count - minimum_train
        if "validation_size" in options:
            validation_size = int(options["validation_size"])
            step_size = int(options.get("step_size", validation_size))
            if validation_size < 1 or step_size < 1:
                raise ValueError("OOF验证窗口和步长必须为正整数")
            possible = (
                0 if available < validation_size else (available - validation_size) // step_size + 1
            )
            fold_count = min(requested_folds, possible)
            first_validation = row_count - validation_size - step_size * (fold_count - 1)
        else:
            # 兼容直接调用：未提供执行计划时保持原有的等分行为。
            fold_count = min(requested_folds, available)
            validation_size = max(1, available // max(fold_count, 1))
            step_size = validation_size
            first_validation = row_count - validation_size * fold_count
        if fold_count < 2:
            raise ValueError(
                f"历史数据不足以生成OOF残差: rows={row_count}, minimum_train={minimum_train}"
            )
        predictions = np.full(row_count, np.nan)
        component_predictions: dict[str, np.ndarray] = {}
        for index in range(fold_count):
            validation_start = first_validation + index * step_size
            validation_end = validation_start + validation_size
            fold_train = self._slice(train_data, 0, validation_start)
            fold_validation = self._slice(
                train_data, validation_start, validation_end, target=False
            )
            model = base_algorithm.fit(fold_train, base_params)
            prediction = base_algorithm.predict(model, fold_validation)
            values = prediction.values
            if len(values) != validation_end - validation_start:
                raise ValueError("基础模型OOF预测长度与验证窗口不一致")
            predictions[validation_start:validation_end] = values
            for name, component in prediction.metadata.get("components", {}).items():
                target = component_predictions.setdefault(name, np.full(row_count, np.nan))
                target[validation_start:validation_end] = np.asarray(component, dtype=float)
        mask = np.isfinite(predictions)
        indices = np.flatnonzero(mask)
        residual = train_data.target.to_numpy(dtype=float)[mask] - predictions[mask]
        residual_data = self._take(train_data, indices).with_target(residual)
        for name, values in component_predictions.items():
            residual_data.features[name] = values[mask]
        residual_data.metadata.update(
            {
                "residual_training_mode": "WALK_FORWARD_OOF",
                "oof_fold_count": fold_count,
                "oof_validation_size": validation_size,
                "oof_step_size": step_size,
                "oof_source_indices": indices.tolist(),
                "oof_component_features": sorted(component_predictions),
            }
        )
        return OOFResidualDataset(residual_data, predictions[mask], indices, fold_count)

    @staticmethod
    def _slice(data, start, end, target=True):
        result = WalkForwardOOFResidualBuilder._take(data, np.arange(start, end))
        if not target:
            result.target = None
        return result

    @staticmethod
    def _take(data, indices):
        return AlgorithmDataset(
            features=data.features.iloc[indices].reset_index(drop=True),
            dates=data.dates[indices],
            target=None
            if data.target is None
            else data.target.iloc[indices].reset_index(drop=True),
            sample_weight=None
            if data.sample_weight is None
            else data.sample_weight.iloc[indices].reset_index(drop=True),
            metadata=dict(data.metadata),
        )
