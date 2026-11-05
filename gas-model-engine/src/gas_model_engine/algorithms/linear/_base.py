from collections.abc import Callable
from typing import Any

import numpy as np
from sklearn.impute import SimpleImputer
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import RobustScaler, StandardScaler

from ..base import BaseAlgorithm
from ..contracts import (
    AlgorithmCapabilities,
    AlgorithmFamily,
    AlgorithmMetadata,
    AlgorithmModel,
    PredictionResult,
)


def regression_pipeline(estimator, robust=False):
    """创建包含缺失值填充、特征缩放和回归器的标准流水线。"""
    scaler = RobustScaler() if robust else StandardScaler()
    return Pipeline(
        [("imputer", SimpleImputer(strategy="median")), ("scaler", scaler), ("model", estimator)]
    )


class SklearnRegressorAlgorithm(BaseAlgorithm):
    code = ""
    name = ""
    defaults: dict[str, Any] = {}
    factory: Callable[[dict[str, Any]], Any]

    @property
    def metadata(self):
        """根据子类声明的编码和名称生成算法元数据。"""
        return AlgorithmMetadata(self.code, self.name, AlgorithmFamily.LINEAR)

    def get_default_params(self):
        """返回具体线性算法声明的默认参数副本。"""
        return dict(self.defaults)

    def get_capabilities(self):
        """声明线性算法的输入能力和最小训练样本数。"""
        return AlgorithmCapabilities(supports_sample_weight=False, minimum_training_rows=3)

    def fit(self, train_data, params):
        """构建回归流水线并使用标准特征数据完成训练。"""
        self.ensure_valid_input(train_data, "TRAIN")
        normalized = self.normalized_params(params)
        estimator = self.factory(normalized)
        estimator.fit(train_data.features, train_data.target)
        return AlgorithmModel(
            self.metadata.code,
            self.metadata.version,
            estimator,
            list(train_data.features.columns),
            {"params": normalized},
        )

    def predict(self, model, future_data):
        """按照训练特征顺序整理未来数据并生成一维预测结果。"""
        frame = future_data.features.reindex(columns=model.feature_names)
        return PredictionResult(np.asarray(model.payload.predict(frame)).reshape(-1))
