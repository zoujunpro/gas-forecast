from sklearn.ensemble import BaggingRegressor
from sklearn.tree import DecisionTreeRegressor

from ._base import TreeRegressorAlgorithm, tree_pipeline


class BaggingTreeAlgorithm(TreeRegressorAlgorithm):
    """Bagging 决策树：对多份自助采样数据训练决策树并平均预测结果。"""

    code, name = "BAGGING_TREE_V1", "Bagging决策树"
    defaults = {"n_estimators": 80, "random_state": 42, "n_jobs": 1}
    factory = staticmethod(
        lambda p: tree_pipeline(
            BaggingRegressor(
                estimator=DecisionTreeRegressor(max_depth=5, min_samples_leaf=3, random_state=42),
                **p,
            )
        )
    )
