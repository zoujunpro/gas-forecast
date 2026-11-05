from sklearn.ensemble import GradientBoostingRegressor

from ._base import TreeRegressorAlgorithm, tree_pipeline


class GradientBoostingAlgorithm(TreeRegressorAlgorithm):
    """梯度提升树：逐轮拟合前序模型残差，将弱决策树累加为强预测器。"""

    code, name = "GRADIENT_BOOSTING_V1", "梯度提升树"
    defaults = {
        "n_estimators": 100,
        "learning_rate": 0.03,
        "max_depth": 2,
        "loss": "huber",
        "random_state": 42,
    }
    factory = staticmethod(lambda p: tree_pipeline(GradientBoostingRegressor(**p)))
