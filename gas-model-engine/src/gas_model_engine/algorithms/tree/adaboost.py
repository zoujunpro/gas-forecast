from sklearn.ensemble import AdaBoostRegressor

from ._base import TreeRegressorAlgorithm, tree_pipeline


class AdaBoostAlgorithm(TreeRegressorAlgorithm):
    """AdaBoost 回归：逐轮提高难拟合样本权重并组合多个弱回归器。"""

    code, name = "ADABOOST_V1", "AdaBoost"
    defaults = {"n_estimators": 120, "learning_rate": 0.04, "loss": "square", "random_state": 42}
    factory = staticmethod(lambda p: tree_pipeline(AdaBoostRegressor(**p)))
