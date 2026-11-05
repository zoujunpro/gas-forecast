from .inverse_error_weight import InverseErrorWeightAlgorithm
from .median_ensemble import MedianEnsembleAlgorithm
from .nnls_weight import NnlsWeightAlgorithm
from .prophet_ar_residual import ProphetArResidualAlgorithm
from .prophet_lightgbm_residual import ProphetLightgbmResidualAlgorithm
from .prophet_xgboost_ar_residual import ProphetXgboostArResidualAlgorithm
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
        ProphetArResidualAlgorithm(registry),
        ProphetXgboostArResidualAlgorithm(registry),
        ProphetLightgbmResidualAlgorithm(registry),
    ]


__all__ = [
    "InverseErrorWeightAlgorithm",
    "MedianEnsembleAlgorithm",
    "NnlsWeightAlgorithm",
    "ProphetArResidualAlgorithm",
    "ProphetLightgbmResidualAlgorithm",
    "ProphetXgboostArResidualAlgorithm",
    "ResidualCombinationAlgorithm",
    "RidgeStackingAlgorithm",
    "WeightedAverageAlgorithm",
    "WinsorizedAverageAlgorithm",
    "composite_algorithms",
]
