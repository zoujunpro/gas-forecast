import numpy as np

from ..contracts import AlgorithmFamily, AlgorithmMetadata, PredictionResult
from ._base import StatisticalAlgorithm


class ArimaAlgorithm(StatisticalAlgorithm):
    """ARIMA：结合自回归、差分和移动平均，建模非平稳单变量时间序列。"""

    metadata = AlgorithmMetadata("ARIMA_V1", "ARIMA", AlgorithmFamily.STATISTICAL)

    def get_default_params(self):
        """返回 ARIMA 的默认 (p, d, q) 阶数。"""
        return {"order": (1, 1, 1)}

    def fit(self, train_data, params):
        """使用目标时间序列拟合 ARIMA 模型。"""
        from statsmodels.tsa.arima.model import ARIMA

        return self._model(ARIMA(self._target(train_data), **self.normalized_params(params)).fit())

    def predict(self, model, future_data):
        """生成指定期数的预测值及置信区间。"""
        forecast = model.payload.get_forecast(len(future_data.features))
        interval = np.asarray(forecast.conf_int())
        return PredictionResult(forecast.predicted_mean, interval[:, 0], interval[:, 1])
