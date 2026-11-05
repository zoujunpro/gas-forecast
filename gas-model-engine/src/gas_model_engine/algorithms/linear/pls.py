from sklearn.cross_decomposition import PLSRegression

from ._base import SklearnRegressorAlgorithm, regression_pipeline


class PlsAlgorithm(SklearnRegressorAlgorithm):
    """偏最小二乘回归：提取兼顾特征方差和目标相关性的低维潜变量。"""

    code, name = "PLS_V1", "偏最小二乘回归"
    defaults = {"n_components": 2, "max_iter": 1000}
    factory = staticmethod(lambda p: regression_pipeline(PLSRegression(**p)))
