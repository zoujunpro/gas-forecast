from .inverse_error_weight import InverseErrorWeightAlgorithm
from .median_ensemble import MedianEnsembleAlgorithm
from .nnls_weight import NnlsWeightAlgorithm
from .prophet_ar import ProphetArAlgorithm
from .prophet_lightgbm import ProphetLightgbmAlgorithm
from .prophet_xgboost import ProphetXgboostAlgorithm
from .residual_combination import ResidualCombinationAlgorithm
from .ridge_stacking import RidgeStackingAlgorithm
from .weighted_average import WeightedAverageAlgorithm
from .winsorized_average import WinsorizedAverageAlgorithm


def composite_algorithms(registry):
    return [
        WeightedAverageAlgorithm(registry),
        MedianEnsembleAlgorithm(registry),
        WinsorizedAverageAlgorithm(registry),
        InverseErrorWeightAlgorithm(registry),
        NnlsWeightAlgorithm(registry),
        RidgeStackingAlgorithm(registry),
        ResidualCombinationAlgorithm(registry),
        ProphetArAlgorithm(registry),
        ProphetXgboostAlgorithm(registry),
        ProphetLightgbmAlgorithm(registry),
    ]


__all__ = [
    "InverseErrorWeightAlgorithm",
    "MedianEnsembleAlgorithm",
    "NnlsWeightAlgorithm",
    "ProphetArAlgorithm",
    "ProphetLightgbmAlgorithm",
    "ProphetXgboostAlgorithm",
    "ResidualCombinationAlgorithm",
    "RidgeStackingAlgorithm",
    "WeightedAverageAlgorithm",
    "WinsorizedAverageAlgorithm",
    "composite_algorithms",
]
