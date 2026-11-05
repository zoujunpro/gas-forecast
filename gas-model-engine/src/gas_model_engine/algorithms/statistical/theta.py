from ..contracts import AlgorithmFamily, AlgorithmMetadata, PredictionResult
from ._base import StatisticalAlgorithm


class ThetaAlgorithm(StatisticalAlgorithm):
    """Theta 模型：通过改变序列曲率并组合趋势信息完成中短期预测。"""

    metadata = AlgorithmMetadata("THETA_V1", "Theta", AlgorithmFamily.STATISTICAL)

    def get_default_params(self):
        """返回 Theta 模型的季节周期和去季节化配置。"""
        return {"period": 36, "deseasonalize": True}

    def fit(self, train_data, params):
        """使用目标时间序列拟合 Theta 模型。"""
        from statsmodels.tsa.forecasting.theta import ThetaModel

        return self._model(
            ThetaModel(self._target(train_data), **self.normalized_params(params)).fit()
        )

    def predict(self, model, future_data):
        """使用 Theta 模型生成未来多期预测。"""
        return PredictionResult(model.payload.forecast(len(future_data.features)))
