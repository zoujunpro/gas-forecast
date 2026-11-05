from sklearn.linear_model import BayesianRidge

from ._base import SklearnRegressorAlgorithm, regression_pipeline


class BayesianRidgeAlgorithm(SklearnRegressorAlgorithm):
    """贝叶斯岭回归：用概率分布估计带正则的线性模型参数。"""

    code, name = "BAYESIAN_RIDGE_V1", "贝叶斯岭回归"
    factory = staticmethod(lambda p: regression_pipeline(BayesianRidge(**p)))
