from sklearn.linear_model import Ridge

from ._base import SklearnRegressorAlgorithm, regression_pipeline


class RidgeAlgorithm(SklearnRegressorAlgorithm):
    """岭回归：在线性回归中加入 L2 正则，降低多重共线性和过拟合风险。"""

    code, name = "RIDGE_V1", "岭回归"
    defaults = {"alpha": 10.0}
    factory = staticmethod(lambda p: regression_pipeline(Ridge(**p)))
