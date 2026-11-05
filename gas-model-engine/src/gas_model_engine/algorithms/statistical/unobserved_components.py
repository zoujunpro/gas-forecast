import numpy as np

from ..contracts import AlgorithmFamily, AlgorithmMetadata, PredictionResult
from ._base import StatisticalAlgorithm


class UnobservedComponentsAlgorithm(StatisticalAlgorithm):
    """不可观测成分模型：用状态空间结构表示水平、趋势和季节等隐含成分。"""

    metadata = AlgorithmMetadata(
        "UNOBSERVED_COMPONENTS_V1", "不可观测成分模型", AlgorithmFamily.STATISTICAL
    )

    def get_default_params(self):
        """返回状态空间模型的水平项和季节周期配置。"""
        return {"level": "local linear trend", "seasonal": 36}

    def fit(self, train_data, params):
        """使用目标序列拟合不可观测成分状态空间模型。"""
        from statsmodels.tsa.statespace.structural import UnobservedComponents

        return self._model(
            UnobservedComponents(self._target(train_data), **self.normalized_params(params)).fit(
                disp=False
            )
        )

    def predict(self, model, future_data):
        """生成未来预测值及其置信区间。"""
        forecast = model.payload.get_forecast(len(future_data.features))
        interval = np.asarray(forecast.conf_int())
        return PredictionResult(forecast.predicted_mean, interval[:, 0], interval[:, 1])
