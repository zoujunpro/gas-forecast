
from __future__ import annotations
import json
from pathlib import Path
from typing import Any, Dict

DEFAULT_CONFIG: Dict[str, Any] = {
    "provinces": ["江苏", "河北"],
    "forecast_start": "auto",
    "forecast_horizon": 15,
    "profile": "full",
    "backtest_seasons": 6,
    "tuning_seasons": 2,
    "minimum_train_rows": 108,
    "feature_top_n_candidates": [12, 20, 30, 999],
    "optuna_enabled": True,
    "optuna_trials": 8,
    "random_seed": 42,
    "future_weather_recent_years": 5,
    "drop_suspicious_tail": True,
    "prediction_interval": 0.90,
    "tune_models": [
        "Ridge", "ElasticNet", "RandomForest", "ExtraTrees",
        "GradientBoosting",
        "LightGBM", "XGBoost", "CatBoost"
    ],
}

PROFILE_OVERRIDES = {
    "smoke": {
        "backtest_seasons": 3,
        "tuning_seasons": 1,
        "optuna_enabled": False,
        "optuna_trials": 1,
        "feature_top_n_candidates": [12],
    },
    "quick": {
        "backtest_seasons": 4,
        "tuning_seasons": 1,
        "optuna_trials": 4,
        "feature_top_n_candidates": [12, 20],
    },
    "full": {},
    "exhaustive": {
        "backtest_seasons": 7,
        "tuning_seasons": 3,
        "optuna_trials": 30,
        "feature_top_n_candidates": [10, 15, 20, 30, 40, 999],
    },
}

def load_config(path: Path | None = None, profile: str | None = None) -> Dict[str, Any]:
    cfg = dict(DEFAULT_CONFIG)
    if path and path.exists():
        with path.open('r', encoding='utf-8') as f:
            cfg.update(json.load(f))
    selected = profile or cfg.get('profile', 'full')
    if selected not in PROFILE_OVERRIDES:
        raise ValueError(f"未知运行模式: {selected}")
    cfg.update(PROFILE_OVERRIDES[selected])
    cfg['profile'] = selected
    return cfg
