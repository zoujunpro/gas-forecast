from sklearn.linear_model import LinearRegression

from ._base import SklearnRegressorAlgorithm, regression_pipeline


class LinearRegressionAlgorithm(SklearnRegressorAlgorithm):
    """线性回归：通过最小二乘学习特征与目标之间的线性关系。"""

    code, name = "LINEAR_REGRESSION_V1", "线性回归"
    factory = staticmethod(lambda p: regression_pipeline(LinearRegression(**p)))
