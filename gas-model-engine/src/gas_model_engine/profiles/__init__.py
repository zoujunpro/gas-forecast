from .contracts import (
    AggregationMode,
    AlgorithmProfile,
    BacktestSpec,
    CandidateSpec,
    CombinationSpec,
    MethodSpec,
    SelectionSpec,
)
from .monthly_industry import MONTHLY_INDUSTRY_PROFILE
from .registry import AlgorithmProfileRegistry
from .short_term import SHORT_TERM_PROFILE
from .winter_supply import WINTER_SUPPLY_PROFILE


def create_default_profile_registry() -> AlgorithmProfileRegistry:
    return AlgorithmProfileRegistry(
        [SHORT_TERM_PROFILE, WINTER_SUPPLY_PROFILE, MONTHLY_INDUSTRY_PROFILE]
    )


__all__ = [
    "MONTHLY_INDUSTRY_PROFILE",
    "SHORT_TERM_PROFILE",
    "WINTER_SUPPLY_PROFILE",
    "AggregationMode",
    "AlgorithmProfile",
    "AlgorithmProfileRegistry",
    "BacktestSpec",
    "CandidateSpec",
    "CombinationSpec",
    "MethodSpec",
    "SelectionSpec",
    "create_default_profile_registry",
]
