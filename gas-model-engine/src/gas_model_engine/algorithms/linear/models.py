from __future__ import annotations

from collections.abc import Callable
from typing import Any

import numpy as np
from sklearn.cross_decomposition import PLSRegression
from sklearn.impute import SimpleImputer
from sklearn.linear_model import (
    BayesianRidge, ElasticNet, HuberRegressor, Lasso, LinearRegression, Ridge,
)
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import RobustScaler, StandardScaler
from sklearn.svm import LinearSVR

from ..base import BaseAlgorithm
from ..contracts import (
    AlgorithmCapabilities, AlgorithmDataset, AlgorithmFamily, AlgorithmMetadata,
    AlgorithmModel, PredictionResult,
)


class SklearnRegressorAlgorithm(BaseAlgorithm):
    def __init__(self, code: str, name: str, factory: Callable[[dict[str, Any]], Any], defaults=None):
        self._metadata = AlgorithmMetadata(code, name, AlgorithmFamily.LINEAR)
        self._factory = factory
        self._defaults = dict(defaults or {})

    @property
    def metadata(self):
        return self._metadata

    def get_default_params(self):
        return dict(self._defaults)

    def get_capabilities(self):
        return AlgorithmCapabilities(supports_sample_weight=False, minimum_training_rows=3)

    def fit(self, train_data, params):
        self.ensure_valid_input(train_data, "TRAIN")
        normalized = self.normalized_params(params)
        estimator = self._factory(normalized)
        estimator.fit(train_data.features, train_data.target)
        return AlgorithmModel(self.metadata.code, self.metadata.version, estimator, list(train_data.features.columns), {"params": normalized})

    def predict(self, model, future_data):
        frame = future_data.features.reindex(columns=model.feature_names)
        return PredictionResult(np.asarray(model.payload.predict(frame)).reshape(-1))


def _pipe(estimator, robust=False):
    scaler = RobustScaler() if robust else StandardScaler()
    return Pipeline([("imputer", SimpleImputer(strategy="median")), ("scaler", scaler), ("model", estimator)])


def linear_algorithms() -> list[BaseAlgorithm]:
    return [
        SklearnRegressorAlgorithm("LINEAR_REGRESSION_V1", "线性回归", lambda p: _pipe(LinearRegression(**p))),
        SklearnRegressorAlgorithm("RIDGE_V1", "岭回归", lambda p: _pipe(Ridge(**p)), {"alpha": 10.0}),
        SklearnRegressorAlgorithm("LASSO_V1", "Lasso回归", lambda p: _pipe(Lasso(**p)), {"alpha": 1.0, "max_iter": 20000}),
        SklearnRegressorAlgorithm("ELASTIC_NET_V1", "ElasticNet回归", lambda p: _pipe(ElasticNet(**p)), {"alpha": 1.0, "l1_ratio": 0.5, "max_iter": 20000}),
        SklearnRegressorAlgorithm("HUBER_V1", "Huber稳健回归", lambda p: _pipe(HuberRegressor(**p), robust=True), {"epsilon": 1.35, "max_iter": 1000}),
        SklearnRegressorAlgorithm("BAYESIAN_RIDGE_V1", "贝叶斯岭回归", lambda p: _pipe(BayesianRidge(**p))),
        SklearnRegressorAlgorithm("PLS_V1", "偏最小二乘回归", lambda p: _pipe(PLSRegression(**p)), {"n_components": 2, "max_iter": 1000}),
        SklearnRegressorAlgorithm("LINEAR_SVR_V1", "线性支持向量回归", lambda p: _pipe(LinearSVR(**p), robust=True), {"C": 1.0, "epsilon": 0.01, "max_iter": 20000, "random_state": 42}),
    ]
