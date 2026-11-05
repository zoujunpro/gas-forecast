from sklearn.linear_model import HuberRegressor

from ._base import SklearnRegressorAlgorithm, regression_pipeline


class HuberAlgorithm(SklearnRegressorAlgorithm):
    """Huber 稳健回归：降低异常样本对线性模型参数估计的影响。"""

    code, name = "HUBER_V1", "Huber稳健回归"
    defaults = {"epsilon": 1.35, "max_iter": 1000}
    factory = staticmethod(lambda p: regression_pipeline(HuberRegressor(**p), robust=True))
