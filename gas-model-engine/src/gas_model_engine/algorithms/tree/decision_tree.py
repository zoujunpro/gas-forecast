from sklearn.tree import DecisionTreeRegressor

from ._base import TreeRegressorAlgorithm, tree_pipeline


class DecisionTreeAlgorithm(TreeRegressorAlgorithm):
    """决策树回归：通过递归划分特征空间学习非线性分段预测规则。"""

    code, name = "DECISION_TREE_V1", "决策树"
    defaults = {"max_depth": 6, "min_samples_leaf": 3, "random_state": 42}
    factory = staticmethod(lambda p: tree_pipeline(DecisionTreeRegressor(**p)))
