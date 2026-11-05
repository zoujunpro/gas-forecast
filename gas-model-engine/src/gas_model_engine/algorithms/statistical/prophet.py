import pandas as pd

from ..contracts import AlgorithmCapabilities, AlgorithmFamily, AlgorithmMetadata, PredictionResult
from ._base import StatisticalAlgorithm


class ProphetAlgorithm(StatisticalAlgorithm):
    """Prophet：以可解释的趋势和多重季节项建模，并支持额外回归特征。"""

    metadata = AlgorithmMetadata("PROPHET_V1", "Prophet", AlgorithmFamily.STATISTICAL)

    def get_default_params(self):
        """返回 Prophet 的季节性和趋势变化默认配置。"""
        return {
            "yearly_seasonality": "auto",
            "weekly_seasonality": "auto",
            "daily_seasonality": False,
            "seasonality_mode": "additive",
            "changepoint_prior_scale": 0.05,
            "seasonality_prior_scale": 10.0,
            "holidays_prior_scale": 10.0,
        }

    def get_capabilities(self):
        """声明 Prophet 支持外生特征和预测区间。"""
        return AlgorithmCapabilities(
            supports_features=True, supports_prediction_interval=True, minimum_training_rows=30
        )

    def fit(self, train_data, params):
        """组装 Prophet 训练数据、注册回归特征并拟合模型。"""
        from prophet import Prophet

        self.ensure_valid_input(train_data, "TRAIN")
        model = Prophet(**self.normalized_params(params))
        frame = pd.DataFrame({"ds": train_data.dates, "y": train_data.target})
        names = list(train_data.features.columns)
        for name in names:
            model.add_regressor(name)
            frame[name] = train_data.features[name].to_numpy()
        model.fit(frame)
        return self._model(model, names)

    def predict(self, model, future_data):
        """构造未来日期及回归特征，返回预测值和上下界。"""
        frame = pd.DataFrame({"ds": future_data.dates})
        for name in model.feature_names:
            frame[name] = future_data.features[name].to_numpy()
        result = model.payload.predict(frame)
        components = {}
        for name in ("trend", "weekly", "yearly", "additive_terms", "multiplicative_terms"):
            if name in result:
                components[f"prophet_{name}"] = result[name].to_numpy(dtype=float)
        components["prophet_yhat"] = result["yhat"].to_numpy(dtype=float)
        return PredictionResult(
            result["yhat"],
            result.get("yhat_lower"),
            result.get("yhat_upper"),
            metadata={"components": components},
        )
