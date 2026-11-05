import numpy as np

from ..contracts import AlgorithmFamily, AlgorithmMetadata, PredictionResult
from ._base import StatisticalAlgorithm


class SarimaxAlgorithm(StatisticalAlgorithm):
    """SARIMAX：在 ARIMA 基础上建模季节性，并可使用外生特征辅助预测。"""

    metadata = AlgorithmMetadata("SARIMAX_V1", "SARIMAX", AlgorithmFamily.STATISTICAL)

    def get_default_params(self):
        """返回 SARIMAX 的非季节、季节和趋势默认参数。"""
        return {
            "order": (1, 1, 1),
            "seasonal_order": (1, 0, 0, 36),
            "trend": "t",
            "enforce_stationarity": False,
            "enforce_invertibility": False,
        }

    def fit(self, train_data, params):
        """使用目标序列和可选外生特征拟合 SARIMAX。"""
        from statsmodels.tsa.statespace.sarimax import SARIMAX

        normalized = self.normalized_params(params)
        exog = train_data.features if normalized.pop("use_exog", False) else None
        result = SARIMAX(self._target(train_data), exog=exog, **normalized).fit(disp=False)
        return self._model(
            result,
            list(train_data.features.columns) if exog is not None else [],
            {"use_exog": exog is not None},
        )

    def predict(self, model, future_data):
        """使用可选未来外生特征生成预测值和置信区间。"""
        exog = (
            future_data.features.reindex(columns=model.feature_names)
            if model.metadata["use_exog"]
            else None
        )
        forecast = model.payload.get_forecast(len(future_data.features), exog=exog)
        interval = np.asarray(forecast.conf_int())
        return PredictionResult(forecast.predicted_mean, interval[:, 0], interval[:, 1])
