from __future__ import annotations

from typing import Any

import numpy as np

from ..base import BaseAlgorithm
from ..contracts import (
    AlgorithmCapabilities, AlgorithmDataset, AlgorithmFamily, AlgorithmMetadata,
    AlgorithmModel, PredictionResult, ValidationIssue, ValidationResult,
)


class _HistoryAlgorithm(BaseAlgorithm):
    def get_capabilities(self) -> AlgorithmCapabilities:
        return AlgorithmCapabilities(supports_features=False, minimum_training_rows=1)

    def fit(self, train_data: AlgorithmDataset, params: dict[str, Any]) -> AlgorithmModel:
        self.ensure_valid_input(train_data, "TRAIN")
        normalized = self.normalized_params(params)
        values = train_data.target.to_numpy(dtype=float)
        if not np.isfinite(values).all():
            raise ValueError("target包含非有限数值")
        self._validate_history(values, normalized)
        return AlgorithmModel(
            self.metadata.code, self.metadata.version,
            payload=values.tolist(), metadata={"params": normalized},
        )

    def predict(self, model: AlgorithmModel, future_data: AlgorithmDataset) -> PredictionResult:
        history = list(map(float, model.payload))
        params = dict(model.metadata["params"])
        predictions: list[float] = []
        for _ in range(len(future_data.features)):
            value = float(self._next(history, params))
            predictions.append(value)
            history.append(value)
        return PredictionResult(np.asarray(predictions))

    def _validate_history(self, history: np.ndarray, params: dict[str, Any]) -> None:
        return None

    def _next(self, history: list[float], params: dict[str, Any]) -> float:
        raise NotImplementedError


class NaiveAlgorithm(_HistoryAlgorithm):
    metadata = AlgorithmMetadata("NAIVE_V1", "朴素预测", AlgorithmFamily.BASELINE)

    def _next(self, history, params):
        return history[-1]


class MovingAverageAlgorithm(_HistoryAlgorithm):
    metadata = AlgorithmMetadata("MOVING_AVERAGE_V1", "移动平均", AlgorithmFamily.BASELINE)

    def get_default_params(self):
        return {"window": 3}

    def validate_params(self, params):
        result = super().validate_params(params)
        if result.valid and (not isinstance(result.normalized_params["window"], int) or result.normalized_params["window"] < 1):
            return ValidationResult(False, result.normalized_params, [ValidationIssue("INVALID_WINDOW", "window必须是正整数", "window")])
        return result

    def _next(self, history, params):
        return np.mean(history[-params["window"]:])


class EwmaAlgorithm(_HistoryAlgorithm):
    metadata = AlgorithmMetadata("EWMA_V1", "指数加权移动平均", AlgorithmFamily.BASELINE)

    def get_default_params(self):
        return {"alpha": 0.3}

    def validate_params(self, params):
        result = super().validate_params(params)
        alpha = result.normalized_params.get("alpha")
        if result.valid and (not isinstance(alpha, (int, float)) or not 0 < alpha <= 1):
            return ValidationResult(False, result.normalized_params, [ValidationIssue("INVALID_ALPHA", "alpha必须在(0, 1]内", "alpha")])
        return result

    def _next(self, history, params):
        alpha = params["alpha"]
        level = history[0]
        for value in history[1:]:
            level = alpha * value + (1 - alpha) * level
        return level


class SeasonalNaiveAlgorithm(_HistoryAlgorithm):
    metadata = AlgorithmMetadata("SEASONAL_NAIVE_V1", "季节朴素预测", AlgorithmFamily.BASELINE)

    def get_default_params(self):
        return {"period": 7}

    def validate_params(self, params):
        result = super().validate_params(params)
        period = result.normalized_params.get("period")
        if result.valid and (not isinstance(period, int) or period < 1):
            return ValidationResult(False, result.normalized_params, [ValidationIssue("INVALID_PERIOD", "period必须是正整数", "period")])
        return result

    def _validate_history(self, history, params):
        if len(history) < params["period"]:
            raise ValueError(f"季节朴素预测至少需要{params['period']}条数据")

    def _next(self, history, params):
        return history[-params["period"]]


class SeasonalMeanAlgorithm(SeasonalNaiveAlgorithm):
    metadata = AlgorithmMetadata("SEASONAL_MEAN_V1", "季节同期均值", AlgorithmFamily.BASELINE)

    def get_default_params(self):
        return {"period": 36, "seasons": 2}

    def _validate_history(self, history, params):
        required = params["period"] * params["seasons"]
        if len(history) < required:
            raise ValueError(f"季节同期均值至少需要{required}条数据")

    def _next(self, history, params):
        values = [history[-params["period"] * index] for index in range(1, params["seasons"] + 1)]
        return np.mean(values)


class SeasonalTrendAlgorithm(SeasonalNaiveAlgorithm):
    metadata = AlgorithmMetadata("SEASONAL_TREND_V1", "季节趋势预测", AlgorithmFamily.BASELINE)

    def get_default_params(self):
        return {"period": 36, "trend_weight": 0.5}

    def _validate_history(self, history, params):
        if len(history) < params["period"] * 2:
            raise ValueError(f"季节趋势预测至少需要{params['period'] * 2}条数据")

    def _next(self, history, params):
        recent = history[-params["period"]]
        previous = history[-params["period"] * 2]
        return recent + params["trend_weight"] * (recent - previous)
