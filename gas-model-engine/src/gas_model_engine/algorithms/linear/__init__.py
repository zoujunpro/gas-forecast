from .bayesian_ridge import BayesianRidgeAlgorithm
from .elastic_net import ElasticNetAlgorithm
from .huber import HuberAlgorithm
from .lasso import LassoAlgorithm
from .linear_regression import LinearRegressionAlgorithm
from .linear_svr import LinearSvrAlgorithm
from .pls import PlsAlgorithm
from .ridge import RidgeAlgorithm


def linear_algorithms():
    return [
        LinearRegressionAlgorithm(),
        RidgeAlgorithm(),
        LassoAlgorithm(),
        ElasticNetAlgorithm(),
        HuberAlgorithm(),
        BayesianRidgeAlgorithm(),
        PlsAlgorithm(),
        LinearSvrAlgorithm(),
    ]


__all__ = [
    "BayesianRidgeAlgorithm",
    "ElasticNetAlgorithm",
    "HuberAlgorithm",
    "LassoAlgorithm",
    "LinearRegressionAlgorithm",
    "LinearSvrAlgorithm",
    "PlsAlgorithm",
    "RidgeAlgorithm",
    "linear_algorithms",
]
