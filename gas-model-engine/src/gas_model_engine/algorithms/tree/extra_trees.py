from sklearn.ensemble import ExtraTreesRegressor

from ._base import TreeRegressorAlgorithm, tree_pipeline


class ExtraTreesAlgorithm(TreeRegressorAlgorithm):
    """极端随机树：使用更随机的切分构建树集成，以降低方差并提升训练效率。"""

    code, name = "EXTRA_TREES_V1", "极端随机树"
    defaults = {
        "n_estimators": 160,
        "min_samples_leaf": 2,
        "max_features": 0.9,
        "random_state": 42,
        "n_jobs": 1,
    }
    factory = staticmethod(lambda p: tree_pipeline(ExtraTreesRegressor(**p)))
