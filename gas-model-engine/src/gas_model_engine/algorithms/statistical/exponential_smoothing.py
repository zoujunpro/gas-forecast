from ..contracts import AlgorithmFamily, AlgorithmMetadata, PredictionResult
from ._base import StatisticalAlgorithm


class ExponentialSmoothingAlgorithm(StatisticalAlgorithm):
    """指数平滑：分解并平滑水平、趋势和可选季节成分，适合规律时序预测。"""

    metadata = AlgorithmMetadata(
        "EXPONENTIAL_SMOOTHING_V1", "指数平滑", AlgorithmFamily.STATISTICAL
    )

    def get_default_params(self):
        """返回指数平滑模型的趋势、季节和初始化默认配置。"""
        return {
            "trend": "add",
            "damped_trend": True,
            "seasonal": None,
            "seasonal_periods": None,
            "initialization_method": "estimated",
        }

    def fit(self, train_data, params):
        """使用目标序列拟合指数平滑模型。"""
        from statsmodels.tsa.holtwinters import ExponentialSmoothing

        normalized = self.normalized_params(params)
        fit_params = normalized.pop("fit_params", {"optimized": True, "use_brute": False})
        return self._model(
            ExponentialSmoothing(self._target(train_data), **normalized).fit(**fit_params)
        )

    def predict(self, model, future_data):
        """使用已拟合模型生成指定期数的预测。"""
        return PredictionResult(model.payload.forecast(len(future_data.features)))
