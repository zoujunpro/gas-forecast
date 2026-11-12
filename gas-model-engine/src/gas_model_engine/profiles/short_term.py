from .contracts import (
    AggregationMode,
    AlgorithmProfile,
    BacktestSpec,
    CandidateSpec,
    MethodSpec,
    SelectionSpec,
)

SHORT_TERM_PROFILE = AlgorithmProfile(
    code="short-term",
    version="2.0.0",
    candidates=(
        CandidateSpec("naive", "NAIVE_V1", required=True),
        CandidateSpec("moving_average", "MOVING_AVERAGE_V1", {"window": 7}),
        CandidateSpec("ewma", "EWMA_V1"),
        CandidateSpec("arima", "ARIMA_V1"),
        CandidateSpec(
            "prophet",
            "PROPHET_V1",
            search_space={"changepoint_prior_scale": [0.01, 0.05, 0.1]},
        ),
        CandidateSpec("ridge", "RIDGE_V1", search_space={"alpha": [0.1, 1.0, 10.0]}),
        CandidateSpec("xgboost", "XGBOOST_V1"),
        CandidateSpec("lightgbm", "LIGHTGBM_V1"),
        CandidateSpec(
            "prophet_ar",
            "PROPHET_AR_RESIDUAL_V1",
            {
                "prophet_params": {
                    "yearly_seasonality": "auto",
                    "weekly_seasonality": "auto",
                    "daily_seasonality": False,
                    "seasonality_mode": "additive",
                    "changepoint_prior_scale": 0.05,
                    "seasonality_prior_scale": 10.0,
                    "holidays_prior_scale": 20.0,
                },
                "ar_params": {"lags": 4},
                # AR只使用残差序列，不向公共组合申请目标Lag/Rolling特征。
                "residual_feature_policy": "PASSTHROUGH_V1",
                # 残差成员只学习Prophet样本外误差，不能使用训练集内拟合残差。
                "oof_params": {"folds": 3, "minimum_train_size": 30},
                "residual_clip": {
                    "enabled": True,
                    "lower_quantile": 0.01,
                    "upper_quantile": 0.99,
                },
            },
            search_space={"prophet_params.changepoint_prior_scale": [0.01, 0.05, 0.1]},
        ),
        CandidateSpec(
            "prophet_xgboost",
            "PROPHET_XGBOOST_RESIDUAL_V1",
            {
                "prophet_params": {
                    "seasonality_mode": "additive",
                    "changepoint_prior_scale": 0.05,
                    "seasonality_prior_scale": 10.0,
                    "holidays_prior_scale": 20.0,
                },
                "xgboost_params": {
                    "n_estimators": 300,
                    "max_depth": 4,
                    "learning_rate": 0.05,
                    "subsample": 0.9,
                    "colsample_bytree": 0.9,
                    "early_stopping": {
                        "enabled": True,
                        "validation_fraction": 0.2,
                        "minimum_validation_rows": 10,
                        "rounds": 30,
                    },
                },
                # XGBoost残差模型需要目标历史特征，并在未来日期逐日递推。
                "residual_feature_policy": "TARGET_HISTORY_V1",
                "oof_params": {"folds": 3, "minimum_train_size": 30},
                "residual_clip": {
                    "enabled": True,
                    "lower_quantile": 0.01,
                    "upper_quantile": 0.99,
                },
            },
            search_space={
                "prophet_params.changepoint_prior_scale": [0.01, 0.05],
                "xgboost_params.max_depth": [3, 4],
            },
        ),
        CandidateSpec(
            "prophet_lightgbm",
            "PROPHET_LIGHTGBM_RESIDUAL_V1",
            {
                "prophet_params": {
                    "seasonality_mode": "additive",
                    "changepoint_prior_scale": 0.05,
                    "seasonality_prior_scale": 10.0,
                    "holidays_prior_scale": 20.0,
                },
                "lightgbm_params": {
                    "num_leaves": 15,
                    "max_depth": 3,
                    "learning_rate": 0.03,
                    "min_child_samples": 20,
                    "subsample": 0.8,
                    "colsample_bytree": 0.8,
                    "reg_alpha": 0.5,
                    "reg_lambda": 1.0,
                    "early_stopping": {
                        "enabled": True,
                        "validation_fraction": 0.2,
                        "minimum_validation_rows": 10,
                        "rounds": 30,
                    },
                },
                "minimum_lightgbm_rows": 40,
                # LightGBM仅消费配置生成的行业特征，不增加销量Lag，也不递归。
                "residual_feature_policy": "PASSTHROUGH_V1",
                "oof_params": {"folds": 3, "minimum_train_size": 30},
                "residual_clip": {
                    "enabled": True,
                    "lower_quantile": 0.01,
                    "upper_quantile": 0.99,
                },
            },
            search_space={
                "prophet_params.changepoint_prior_scale": [0.01, 0.05],
                "lightgbm_params.num_leaves": [7, 15],
            },
        ),
    ),
    aggregation=AggregationMode.MEAN,
    scenario_code="SHORT_TERM_CUSTOMER_V1",
    feature_policy_code="SHORT_TERM_CUSTOMER_FEATURES_V1",
    features=(
        MethodSpec("DYNAMIC_PASSTHROUGH_V1"),
        MethodSpec("CALENDAR_V1", required=True),
    ),
    backtest=BacktestSpec(
        "EXPANDING_WALK_FORWARD_V1",
        {
            "mode": "auto",
            "min_folds": 6,
            "max_folds": 10,
            "minimum_tune_folds": 3,
            "minimum_eval_folds": 3,
            "eval_ratio": 0.4,
            "prediction_interval_coverage": 0.8,
        },
    ),
    combinations=(),
    selection=SelectionSpec("MIN_METRIC_SELECTOR_V1", {"metric": "mape"}),
)
