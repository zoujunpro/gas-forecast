from sklearn.ensemble import RandomForestRegressor

from ._base import TreeRegressorAlgorithm, tree_pipeline


class RandomForestAlgorithm(TreeRegressorAlgorithm):
    """随机森林：对多棵随机采样决策树取平均，提高泛化能力和稳定性。"""

    code, name = "RANDOM_FOREST_V1", "随机森林"
    defaults = {
        "n_estimators": 160,
        "min_samples_leaf": 2,
        "max_features": 0.8,
        "random_state": 42,
        "n_jobs": 1,
    }
    factory = staticmethod(lambda p: tree_pipeline(RandomForestRegressor(**p)))
