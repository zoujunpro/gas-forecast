from .baseline import (
    EwmaAlgorithm,
    MovingAverageAlgorithm,
    NaiveAlgorithm,
    SeasonalMeanAlgorithm,
    SeasonalNaiveAlgorithm,
    SeasonalTrendAlgorithm,
)
from .composite import composite_algorithms
from .linear import linear_algorithms
from .registry import AlgorithmRegistry
from .statistical import statistical_algorithms
from .tree import tree_algorithms


def create_default_registry() -> AlgorithmRegistry:
    registry = AlgorithmRegistry(
        [
            NaiveAlgorithm(),
            SeasonalNaiveAlgorithm(),
            SeasonalMeanAlgorithm(),
            SeasonalTrendAlgorithm(),
            MovingAverageAlgorithm(),
            EwmaAlgorithm(),
            *statistical_algorithms(),
            *linear_algorithms(),
            *tree_algorithms(),
        ]
    )
    for algorithm in composite_algorithms(registry):
        registry.register(algorithm)
    return registry


__all__ = ["AlgorithmRegistry", "create_default_registry"]
