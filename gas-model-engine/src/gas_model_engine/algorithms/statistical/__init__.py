from .arima import ArimaAlgorithm
from .auto_regression import AutoRegressionAlgorithm
from .exponential_smoothing import ExponentialSmoothingAlgorithm
from .prophet import ProphetAlgorithm
from .sarimax import SarimaxAlgorithm
from .theta import ThetaAlgorithm
from .unobserved_components import UnobservedComponentsAlgorithm


def statistical_algorithms():
    return [
        AutoRegressionAlgorithm(),
        ArimaAlgorithm(),
        SarimaxAlgorithm(),
        ExponentialSmoothingAlgorithm(),
        ThetaAlgorithm(),
        UnobservedComponentsAlgorithm(),
        ProphetAlgorithm(),
    ]


__all__ = [
    "ArimaAlgorithm",
    "AutoRegressionAlgorithm",
    "ExponentialSmoothingAlgorithm",
    "ProphetAlgorithm",
    "SarimaxAlgorithm",
    "ThetaAlgorithm",
    "UnobservedComponentsAlgorithm",
    "statistical_algorithms",
]
