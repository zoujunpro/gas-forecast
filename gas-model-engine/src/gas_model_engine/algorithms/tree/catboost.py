from ._base import TreeRegressorAlgorithm, tree_pipeline


def _factory(params):
    """按参数创建 CatBoost 回归流水线，并延迟导入可选依赖。"""
    from catboost import CatBoostRegressor

    return tree_pipeline(CatBoostRegressor(**params))


class CatBoostAlgorithm(TreeRegressorAlgorithm):
    """CatBoost：采用有序提升机制、对类别特征友好的梯度提升树模型。"""

    code, name = "CATBOOST_V1", "CatBoost"
    defaults = {
        "iterations": 300,
        "learning_rate": 0.03,
        "depth": 6,
        "l2_leaf_reg": 5.0,
        "random_seed": 42,
        "verbose": False,
        "allow_writing_files": False,
        "thread_count": 1,
    }
    factory = staticmethod(_factory)
