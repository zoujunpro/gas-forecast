from __future__ import annotations

from collections.abc import Callable
from typing import Any

import numpy as np
from sklearn.ensemble import (
    AdaBoostRegressor, BaggingRegressor, ExtraTreesRegressor,
    GradientBoostingRegressor, RandomForestRegressor,
)
from sklearn.impute import SimpleImputer
from sklearn.pipeline import Pipeline
from sklearn.tree import DecisionTreeRegressor

from ..base import BaseAlgorithm
from ..contracts import (
    AlgorithmCapabilities, AlgorithmFamily, AlgorithmMetadata, AlgorithmModel,
    PredictionResult,
)


class TreeRegressorAlgorithm(BaseAlgorithm):
    def __init__(self, code: str, name: str, factory: Callable[[dict[str, Any]], Any], defaults=None):
        self._metadata = AlgorithmMetadata(code, name, AlgorithmFamily.TREE)
        self._factory = factory
        self._defaults = dict(defaults or {})

    @property
    def metadata(self):
        return self._metadata

    def get_default_params(self):
        return dict(self._defaults)

    def get_capabilities(self):
        return AlgorithmCapabilities(supports_sample_weight=True, minimum_training_rows=10)

    def fit(self, train_data, params):
        self.ensure_valid_input(train_data, "TRAIN")
        normalized = self.normalized_params(params)
        estimator = self._factory(normalized)
        fit_params = {}
        if train_data.sample_weight is not None:
            fit_params["model__sample_weight"] = train_data.sample_weight
        estimator.fit(train_data.features, train_data.target, **fit_params)
        return AlgorithmModel(self.metadata.code, self.metadata.version, estimator, list(train_data.features.columns), {"params": normalized})

    def predict(self, model, future_data):
        frame = future_data.features.reindex(columns=model.feature_names)
        return PredictionResult(np.asarray(model.payload.predict(frame)).reshape(-1))


def _pipe(estimator):
    return Pipeline([("imputer", SimpleImputer(strategy="median")), ("model", estimator)])


def _lightgbm(params):
    from lightgbm import LGBMRegressor
    return _pipe(LGBMRegressor(**params))


def _xgboost(params):
    from xgboost import XGBRegressor
    return _pipe(XGBRegressor(**params))


def _catboost(params):
    from catboost import CatBoostRegressor
    return _pipe(CatBoostRegressor(**params))


def tree_algorithms() -> list[BaseAlgorithm]:
    return [
        TreeRegressorAlgorithm("DECISION_TREE_V1", "决策树", lambda p: _pipe(DecisionTreeRegressor(**p)), {"max_depth": 6, "min_samples_leaf": 3, "random_state": 42}),
        TreeRegressorAlgorithm("RANDOM_FOREST_V1", "随机森林", lambda p: _pipe(RandomForestRegressor(**p)), {"n_estimators": 160, "min_samples_leaf": 2, "max_features": 0.8, "random_state": 42, "n_jobs": 1}),
        TreeRegressorAlgorithm("EXTRA_TREES_V1", "极端随机树", lambda p: _pipe(ExtraTreesRegressor(**p)), {"n_estimators": 160, "min_samples_leaf": 2, "max_features": 0.9, "random_state": 42, "n_jobs": 1}),
        TreeRegressorAlgorithm("GRADIENT_BOOSTING_V1", "梯度提升树", lambda p: _pipe(GradientBoostingRegressor(**p)), {"n_estimators": 100, "learning_rate": 0.03, "max_depth": 2, "loss": "huber", "random_state": 42}),
        TreeRegressorAlgorithm("ADABOOST_V1", "AdaBoost", lambda p: _pipe(AdaBoostRegressor(**p)), {"n_estimators": 120, "learning_rate": 0.04, "loss": "square", "random_state": 42}),
        TreeRegressorAlgorithm("BAGGING_TREE_V1", "Bagging决策树", lambda p: _pipe(BaggingRegressor(estimator=DecisionTreeRegressor(max_depth=5, min_samples_leaf=3, random_state=42), **p)), {"n_estimators": 80, "random_state": 42, "n_jobs": 1}),
        TreeRegressorAlgorithm("LIGHTGBM_V1", "LightGBM", _lightgbm, {"n_estimators": 280, "learning_rate": 0.03, "num_leaves": 15, "min_child_samples": 15, "random_state": 42, "n_jobs": 1, "verbosity": -1}),
        TreeRegressorAlgorithm("XGBOOST_V1", "XGBoost", _xgboost, {"n_estimators": 300, "learning_rate": 0.03, "max_depth": 4, "min_child_weight": 3, "objective": "reg:squarederror", "random_state": 42, "n_jobs": 1, "tree_method": "hist"}),
        TreeRegressorAlgorithm("CATBOOST_V1", "CatBoost", _catboost, {"iterations": 300, "learning_rate": 0.03, "depth": 6, "l2_leaf_reg": 5.0, "random_seed": 42, "verbose": False, "allow_writing_files": False, "thread_count": 1}),
    ]
