from sklearn.linear_model import Lasso

from ._base import SklearnRegressorAlgorithm, regression_pipeline


class LassoAlgorithm(SklearnRegressorAlgorithm):
    """Lasso 回归：使用 L1 正则压缩系数，并具有一定的特征选择能力。"""

    code, name = "LASSO_V1", "Lasso回归"
    defaults = {"alpha": 1.0, "max_iter": 20000}
    factory = staticmethod(lambda p: regression_pipeline(Lasso(**p)))
