from __future__ import annotations

from typing import Any

import numpy as np
import pandas as pd

from ..base import BaseAlgorithm
from ..contracts import (
    AlgorithmCapabilities, AlgorithmDataset, AlgorithmFamily, AlgorithmMetadata,
    AlgorithmModel, PredictionResult,
)


class _StatsAlgorithm(BaseAlgorithm):
    def get_capabilities(self):
        return AlgorithmCapabilities(supports_features=False, supports_prediction_interval=True, minimum_training_rows=20)

    def _target(self, data):
        self.ensure_valid_input(data, "TRAIN")
        values = data.target.to_numpy(dtype=float)
        if not np.isfinite(values).all():
            raise ValueError("统计时序算法不接受缺失target")
        return values

    def _model(self, payload, feature_names=None, metadata=None):
        return AlgorithmModel(self.metadata.code, self.metadata.version, payload, feature_names or [], metadata or {})


class AutoRegressionAlgorithm(_StatsAlgorithm):
    metadata = AlgorithmMetadata("AUTOREGRESSION_V1", "自回归", AlgorithmFamily.STATISTICAL)

    def get_default_params(self):
        return {"lags": 4, "trend": "ct"}

    def fit(self, train_data, params):
        from statsmodels.tsa.ar_model import AutoReg
        normalized = self.normalized_params(params)
        values = self._target(train_data)
        result = AutoReg(values, **normalized).fit()
        return self._model(result, metadata={"history_size": len(values)})

    def predict(self, model, future_data):
        start = int(model.metadata["history_size"])
        values = model.payload.predict(start=start, end=start + len(future_data.features) - 1, dynamic=False)
        return PredictionResult(values)


class ArimaAlgorithm(_StatsAlgorithm):
    metadata = AlgorithmMetadata("ARIMA_V1", "ARIMA", AlgorithmFamily.STATISTICAL)

    def get_default_params(self):
        return {"order": (1, 1, 1)}

    def fit(self, train_data, params):
        from statsmodels.tsa.arima.model import ARIMA
        result = ARIMA(self._target(train_data), **self.normalized_params(params)).fit()
        return self._model(result)

    def predict(self, model, future_data):
        forecast = model.payload.get_forecast(len(future_data.features))
        interval = np.asarray(forecast.conf_int())
        return PredictionResult(forecast.predicted_mean, interval[:, 0], interval[:, 1])


class SarimaxAlgorithm(_StatsAlgorithm):
    metadata = AlgorithmMetadata("SARIMAX_V1", "SARIMAX", AlgorithmFamily.STATISTICAL)

    def get_default_params(self):
        return {"order": (1, 1, 1), "seasonal_order": (1, 0, 0, 36), "trend": "t", "enforce_stationarity": False, "enforce_invertibility": False}

    def fit(self, train_data, params):
        from statsmodels.tsa.statespace.sarimax import SARIMAX
        normalized = self.normalized_params(params)
        exog = train_data.features if normalized.pop("use_exog", False) else None
        result = SARIMAX(self._target(train_data), exog=exog, **normalized).fit(disp=False)
        return self._model(result, list(train_data.features.columns) if exog is not None else [], {"use_exog": exog is not None})

    def predict(self, model, future_data):
        exog = future_data.features.reindex(columns=model.feature_names) if model.metadata["use_exog"] else None
        forecast = model.payload.get_forecast(len(future_data.features), exog=exog)
        interval = np.asarray(forecast.conf_int())
        return PredictionResult(forecast.predicted_mean, interval[:, 0], interval[:, 1])


class ExponentialSmoothingAlgorithm(_StatsAlgorithm):
    metadata = AlgorithmMetadata("EXPONENTIAL_SMOOTHING_V1", "指数平滑", AlgorithmFamily.STATISTICAL)

    def get_default_params(self):
        return {"trend": "add", "damped_trend": True, "seasonal": None, "seasonal_periods": None, "initialization_method": "estimated"}

    def fit(self, train_data, params):
        from statsmodels.tsa.holtwinters import ExponentialSmoothing
        normalized = self.normalized_params(params)
        fit_params = normalized.pop("fit_params", {"optimized": True, "use_brute": False})
        result = ExponentialSmoothing(self._target(train_data), **normalized).fit(**fit_params)
        return self._model(result)

    def predict(self, model, future_data):
        return PredictionResult(model.payload.forecast(len(future_data.features)))


class ThetaAlgorithm(_StatsAlgorithm):
    metadata = AlgorithmMetadata("THETA_V1", "Theta", AlgorithmFamily.STATISTICAL)

    def get_default_params(self):
        return {"period": 36, "deseasonalize": True}

    def fit(self, train_data, params):
        from statsmodels.tsa.forecasting.theta import ThetaModel
        return self._model(ThetaModel(self._target(train_data), **self.normalized_params(params)).fit())

    def predict(self, model, future_data):
        return PredictionResult(model.payload.forecast(len(future_data.features)))


class UnobservedComponentsAlgorithm(_StatsAlgorithm):
    metadata = AlgorithmMetadata("UNOBSERVED_COMPONENTS_V1", "不可观测成分模型", AlgorithmFamily.STATISTICAL)

    def get_default_params(self):
        return {"level": "local linear trend", "seasonal": 36}

    def fit(self, train_data, params):
        from statsmodels.tsa.statespace.structural import UnobservedComponents
        result = UnobservedComponents(self._target(train_data), **self.normalized_params(params)).fit(disp=False)
        return self._model(result)

    def predict(self, model, future_data):
        forecast = model.payload.get_forecast(len(future_data.features))
        interval = np.asarray(forecast.conf_int())
        return PredictionResult(forecast.predicted_mean, interval[:, 0], interval[:, 1])


class ProphetAlgorithm(_StatsAlgorithm):
    metadata = AlgorithmMetadata("PROPHET_V1", "Prophet", AlgorithmFamily.STATISTICAL)

    def get_default_params(self):
        return {"yearly_seasonality": "auto", "weekly_seasonality": "auto", "daily_seasonality": False, "seasonality_mode": "additive", "changepoint_prior_scale": 0.05}

    def get_capabilities(self):
        return AlgorithmCapabilities(supports_features=True, supports_prediction_interval=True, minimum_training_rows=30)

    def fit(self, train_data, params):
        from prophet import Prophet
        self.ensure_valid_input(train_data, "TRAIN")
        normalized = self.normalized_params(params)
        model = Prophet(**normalized)
        frame = pd.DataFrame({"ds": train_data.dates, "y": train_data.target})
        feature_names = list(train_data.features.columns)
        for name in feature_names:
            model.add_regressor(name)
            frame[name] = train_data.features[name].to_numpy()
        model.fit(frame)
        return self._model(model, feature_names)

    def predict(self, model, future_data):
        frame = pd.DataFrame({"ds": future_data.dates})
        for name in model.feature_names:
            frame[name] = future_data.features[name].to_numpy()
        result = model.payload.predict(frame)
        return PredictionResult(result["yhat"], result.get("yhat_lower"), result.get("yhat_upper"))


def statistical_algorithms() -> list[BaseAlgorithm]:
    return [
        AutoRegressionAlgorithm(), ArimaAlgorithm(), SarimaxAlgorithm(),
        ExponentialSmoothingAlgorithm(), ThetaAlgorithm(),
        UnobservedComponentsAlgorithm(), ProphetAlgorithm(),
    ]
