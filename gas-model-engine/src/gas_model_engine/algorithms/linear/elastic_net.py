from sklearn.linear_model import ElasticNet

from ._base import SklearnRegressorAlgorithm, regression_pipeline


class ElasticNetAlgorithm(SklearnRegressorAlgorithm):
    """Elastic Net：组合 L1 与 L2 正则，兼顾特征筛选和系数稳定性。"""

    code, name = "ELASTIC_NET_V1", "ElasticNet回归"
    defaults = {"alpha": 1.0, "l1_ratio": 0.5, "max_iter": 20000}
    factory = staticmethod(lambda p: regression_pipeline(ElasticNet(**p)))
