from .contracts import AggregationMode, AlgorithmProfile, CandidateSpec

WINTER_SUPPLY_PROFILE = AlgorithmProfile(
    code="winter-supply",
    version="1.0.0",
    candidates=(
        CandidateSpec("seasonal_naive_36", "SEASONAL_NAIVE_V1", {"period": 36}),
        CandidateSpec("seasonal_mean_3y", "SEASONAL_MEAN_V1", {"period": 36, "seasons": 3}),
        CandidateSpec("ridge", "RIDGE_V1", {"alpha": 10.0}),
        CandidateSpec("extra_trees", "EXTRA_TREES_V1", {}),
        CandidateSpec("lightgbm", "LIGHTGBM_V1", {}),
        CandidateSpec("xgboost", "XGBOOST_V1", {}),
    ),
    aggregation=AggregationMode.MEDIAN,
    strategy_code="WINTER_SUPPLY_V1",
    feature_policy_code="WINTER_SUPPLY_FEATURES_V1",
)
