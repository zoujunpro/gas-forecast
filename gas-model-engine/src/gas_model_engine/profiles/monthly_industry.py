from .contracts import AggregationMode, AlgorithmProfile, CandidateSpec

MONTHLY_INDUSTRY_PROFILE = AlgorithmProfile(
    code="monthly-industry",
    version="1.0.0",
    candidates=(
        CandidateSpec("seasonal_naive_12", "SEASONAL_NAIVE_V1", {"period": 12}),
        CandidateSpec("seasonal_mean_3y", "SEASONAL_MEAN_V1", {"period": 12, "seasons": 3}),
        CandidateSpec("arima", "ARIMA_V1"),
        CandidateSpec("prophet", "PROPHET_V1"),
        CandidateSpec("ridge", "RIDGE_V1"),
        CandidateSpec("lightgbm", "LIGHTGBM_V1"),
        CandidateSpec("xgboost", "XGBOOST_V1"),
    ),
    aggregation=AggregationMode.MEDIAN,
    scenario_code="MONTHLY_INDUSTRY_V1",
    feature_policy_code="MONTHLY_INDUSTRY_FEATURES_V1",
)
