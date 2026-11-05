from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Protocol

import numpy as np
from scipy.optimize import nnls

from ..evaluation import calculate_metrics
from ..methods import MethodMetadata, MethodRegistry


@dataclass(frozen=True)
class CombinationCandidate:
    id: str
    method_code: str
    member_ids: tuple[str, ...]
    predictions: np.ndarray
    metrics: dict[str, float]
    algorithm_code: str
    algorithm_params: dict[str, Any]


class CombinationMethod(Protocol):
    metadata: MethodMetadata

    def build(
        self,
        ranked_ids: list[str],
        tune_predictions: dict[str, np.ndarray],
        tune_actual: np.ndarray,
        evaluation_predictions: dict[str, np.ndarray],
        evaluation_actual: np.ndarray,
        member_specs: dict[str, dict[str, Any]],
        params: dict[str, Any],
    ) -> list[CombinationCandidate]: ...


class CombinationMethodRegistry(MethodRegistry[CombinationMethod]):
    pass


class _TopKMethod:
    def _top_values(self, params):
        raw = params.get("top_k", [2, 3, 5])
        values = [raw] if isinstance(raw, int) else list(raw)
        return sorted({int(value) for value in values if int(value) >= 2})

    def build(
        self,
        ranked_ids,
        tune_predictions,
        tune_actual,
        evaluation_predictions,
        evaluation_actual,
        member_specs,
        params,
    ):
        results = []
        for top_k in self._top_values(params):
            members = tuple(ranked_ids[:top_k])
            if len(members) < top_k:
                continue
            tune_matrix = np.column_stack([tune_predictions[item] for item in members])
            _, algorithm_code, extra = self._combine(tune_matrix, tune_actual, members, params)
            evaluation_matrix = np.column_stack([evaluation_predictions[item] for item in members])
            if algorithm_code == "MEDIAN_ENSEMBLE_V1":
                predictions = np.median(evaluation_matrix, axis=1)
            else:
                raw_weights = extra.get("weights") or {item: 1.0 for item in members}
                weights = np.asarray([raw_weights[item] for item in members], dtype=float)
                weights /= weights.sum()
                predictions = evaluation_matrix @ weights
            specs = [member_specs[item] for item in members]
            algorithm_params = {"members": specs, **extra}
            candidate_id = f"{self.metadata.code}_TOP{top_k}"
            results.append(
                CombinationCandidate(
                    candidate_id,
                    self.metadata.code,
                    members,
                    predictions,
                    calculate_metrics(evaluation_actual, predictions),
                    algorithm_code,
                    algorithm_params,
                )
            )
        return results


class EqualTopKMethod(_TopKMethod):
    metadata = MethodMetadata("EQUAL_WEIGHT_V1", "等权TopK组合")

    def _combine(self, matrix, actual, members, params):
        return matrix.mean(axis=1), "WEIGHTED_AVERAGE_V1", {}


class MedianTopKMethod(_TopKMethod):
    metadata = MethodMetadata("MEDIAN_V1", "中位数TopK组合")

    def _combine(self, matrix, actual, members, params):
        return np.median(matrix, axis=1), "MEDIAN_ENSEMBLE_V1", {}


class InverseErrorTopKMethod(_TopKMethod):
    metadata = MethodMetadata("INVERSE_ERROR_V1", "误差倒数TopK组合")

    def _combine(self, matrix, actual, members, params):
        epsilon = float(params.get("epsilon", 1e-8))
        errors = np.sqrt(np.mean((matrix - actual[:, None]) ** 2, axis=0))
        weights = 1.0 / np.maximum(errors, epsilon)
        weights /= weights.sum()
        mapping = dict(zip(members, weights.tolist()))
        return matrix @ weights, "WEIGHTED_AVERAGE_V1", {"weights": mapping}


class NnlsTopKMethod(_TopKMethod):
    metadata = MethodMetadata("NNLS_V1", "非负最小二乘TopK组合")

    def _combine(self, matrix, actual, members, params):
        weights = nnls(matrix, actual)[0]
        if weights.sum() <= 0:
            weights = np.ones(len(members))
        weights /= weights.sum()
        mapping = dict(zip(members, weights.tolist()))
        return matrix @ weights, "WEIGHTED_AVERAGE_V1", {"weights": mapping}


def create_default_combination_registry() -> CombinationMethodRegistry:
    return CombinationMethodRegistry(
        [EqualTopKMethod(), InverseErrorTopKMethod(), MedianTopKMethod(), NnlsTopKMethod()]
    )
