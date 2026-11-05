from sklearn.svm import LinearSVR

from ._base import SklearnRegressorAlgorithm, regression_pipeline


class LinearSvrAlgorithm(SklearnRegressorAlgorithm):
    """线性支持向量回归：在 epsilon 不敏感损失下学习稳健的线性预测函数。"""

    code, name = "LINEAR_SVR_V1", "线性支持向量回归"
    defaults = {"C": 1.0, "epsilon": 0.01, "max_iter": 20000, "random_state": 42}
    factory = staticmethod(lambda p: regression_pipeline(LinearSVR(**p), robust=True))
