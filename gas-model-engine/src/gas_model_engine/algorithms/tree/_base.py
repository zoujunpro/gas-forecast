from collections.abc import Callable
from typing import Any

import numpy as np
from sklearn.impute import SimpleImputer
from sklearn.pipeline import Pipeline

from ..base import BaseAlgorithm
from ..contracts import (
    AlgorithmCapabilities,
    AlgorithmFamily,
    AlgorithmMetadata,
    AlgorithmModel,
    PredictionResult,
)


def tree_pipeline(estimator):
    """创建包含缺失值填充和树模型的标准流水线。"""
    return Pipeline([("imputer", SimpleImputer(strategy="median")), ("model", estimator)])


class TreeRegressorAlgorithm(BaseAlgorithm):
    code = ""
    name = ""
    defaults: dict[str, Any] = {}
    factory: Callable[[dict[str, Any]], Any]

    @property
    def metadata(self):
        """根据子类声明的编码和名称生成算法元数据。"""
        return AlgorithmMetadata(self.code, self.name, AlgorithmFamily.TREE)

    def get_default_params(self):
        """返回具体树算法声明的默认参数副本。"""
        return dict(self.defaults)

    def get_capabilities(self):
        """声明树算法支持样本权重及其最小训练样本数。"""
        return AlgorithmCapabilities(supports_sample_weight=True, minimum_training_rows=10)

    def fit(self, train_data, params):
        """构建树模型流水线，并使用可选样本权重完成训练。"""
        self.ensure_valid_input(train_data, "TRAIN")
        normalized = self.normalized_params(params)
        early_stopping = dict(normalized.pop("early_stopping", {}))
        estimator = self.factory(normalized)
        if bool(early_stopping.get("enabled", False)):
            best_iteration = self._fit_with_early_stopping(estimator, train_data, early_stopping)
            return AlgorithmModel(
                self.metadata.code,
                self.metadata.version,
                estimator,
                list(train_data.features.columns),
                {
                    "params": normalized,
                    "early_stopping": early_stopping,
                    "best_iteration": best_iteration,
                },
            )
        fit_params = (
            {"model__sample_weight": train_data.sample_weight}
            if train_data.sample_weight is not None
            else {}
        )
        estimator.fit(train_data.features, train_data.target, **fit_params)
        return AlgorithmModel(
            self.metadata.code,
            self.metadata.version,
            estimator,
            list(train_data.features.columns),
            {"params": normalized},
        )

    def _fit_with_early_stopping(self, estimator, train_data, config):
        """按时间顺序留出训练末段，并委托具体提升树实现早停。"""
        row_count = len(train_data.features)
        fraction = float(config.get("validation_fraction", 0.2))
        minimum_validation_rows = int(config.get("minimum_validation_rows", 10))
        validation_rows = max(minimum_validation_rows, int(np.ceil(row_count * fraction)))
        minimum_fit_rows = self.get_capabilities().minimum_training_rows
        if validation_rows >= row_count - minimum_fit_rows:
            raise ValueError(
                f"数据不足以执行早停: rows={row_count}, validation={validation_rows}, "
                f"minimum_fit={minimum_fit_rows}"
            )
        split = row_count - validation_rows
        imputer = estimator.named_steps["imputer"]
        model = estimator.named_steps["model"]
        x_train = imputer.fit_transform(train_data.features.iloc[:split])
        x_validation = imputer.transform(train_data.features.iloc[split:])
        y_train = train_data.target.iloc[:split]
        y_validation = train_data.target.iloc[split:]
        train_weight = (
            None if train_data.sample_weight is None else train_data.sample_weight.iloc[:split]
        )
        self._fit_booster_with_validation(
            model,
            x_train,
            y_train,
            x_validation,
            y_validation,
            train_weight,
            int(config.get("rounds", 30)),
        )
        return getattr(model, "best_iteration_", getattr(model, "best_iteration", None))

    def _fit_booster_with_validation(
        self, model, x_train, y_train, x_validation, y_validation, sample_weight, rounds
    ):
        """具体XGBoost/LightGBM实现验证集参数；其他树模型默认不支持。"""
        raise ValueError(f"{self.metadata.code}不支持early_stopping参数")

    def predict(self, model, future_data):
        """按照训练特征顺序整理未来数据并生成一维预测结果。"""
        frame = future_data.features.reindex(columns=model.feature_names)
        return PredictionResult(np.asarray(model.payload.predict(frame)).reshape(-1))
