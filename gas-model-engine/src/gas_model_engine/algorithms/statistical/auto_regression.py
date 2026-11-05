from ..contracts import AlgorithmFamily, AlgorithmMetadata, PredictionResult
from ._base import StatisticalAlgorithm


class AutoRegressionAlgorithm(StatisticalAlgorithm):
    """自回归模型：利用目标序列自身的多个滞后值和趋势项预测未来。"""

    metadata = AlgorithmMetadata("AUTOREGRESSION_V1", "自回归", AlgorithmFamily.STATISTICAL)

    def get_default_params(self):
        """返回自回归滞后阶数和趋势项的默认配置。"""
        return {"lags": 4, "trend": "ct"}

    def fit(self, train_data, params):
        """使用目标历史序列拟合 AutoReg 模型。"""
        from statsmodels.tsa.ar_model import AutoReg

        values = self._target(train_data)
        return self._model(
            AutoReg(values, **self.normalized_params(params)).fit(),
            metadata={"history_size": len(values)},
        )

    def predict(self, model, future_data):
        """从训练序列末尾开始生成未来多期预测。"""
        start = int(model.metadata["history_size"])
        return PredictionResult(
            model.payload.predict(
                start=start, end=start + len(future_data.features) - 1, dynamic=False
            )
        )
