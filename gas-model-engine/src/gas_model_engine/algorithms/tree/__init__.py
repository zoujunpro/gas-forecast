from .adaboost import AdaBoostAlgorithm
from .bagging_tree import BaggingTreeAlgorithm
from .catboost import CatBoostAlgorithm
from .decision_tree import DecisionTreeAlgorithm
from .extra_trees import ExtraTreesAlgorithm
from .gradient_boosting import GradientBoostingAlgorithm
from .lightgbm import LightGbmAlgorithm
from .random_forest import RandomForestAlgorithm
from .xgboost import XGBoostAlgorithm


def tree_algorithms():
    return [
        DecisionTreeAlgorithm(),
        RandomForestAlgorithm(),
        ExtraTreesAlgorithm(),
        GradientBoostingAlgorithm(),
        AdaBoostAlgorithm(),
        BaggingTreeAlgorithm(),
        LightGbmAlgorithm(),
        XGBoostAlgorithm(),
        CatBoostAlgorithm(),
    ]


__all__ = [
    "AdaBoostAlgorithm",
    "BaggingTreeAlgorithm",
    "CatBoostAlgorithm",
    "DecisionTreeAlgorithm",
    "ExtraTreesAlgorithm",
    "GradientBoostingAlgorithm",
    "LightGbmAlgorithm",
    "RandomForestAlgorithm",
    "XGBoostAlgorithm",
    "tree_algorithms",
]
