from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Dict, List
import warnings

import numpy as np
import pandas as pd

from .feature_agent import build_supervised, make_feature_row
from .model_zoo import build_estimator


warnings.filterwarnings("ignore")


@dataclass
class FittedForecastModel:
    """不依赖数据库、可随训练产物一起序列化的单模型。"""

    name: str
    kind: str
    estimator: Any = None
    feature_cols: List[str] = field(default_factory=list)
    history_y: List[float] = field(default_factory=list)
    exog_history: List[Dict[str, Any]] = field(default_factory=list)


def _clip(prediction, history):
    values = np.asarray(history, dtype=float)
    lower = max(0.0, float(np.nanpercentile(values[-108:], 1)) * 0.35)
    upper = float(np.nanpercentile(values[-108:], 99)) * 2.5
    return float(np.clip(prediction, lower, upper))


def forecast_baseline(name, history_y: List[float], horizon: int):
    history = list(map(float, history_y))
    predictions = []
    for _ in range(horizon):
        if name == "SeasonalNaive36":
            prediction = history[-36]
        elif name == "SeasonalMean2Y":
            prediction = np.nanmean(
                [history[-36], history[-72] if len(history) >= 72 else np.nan]
            )
        elif name == "SeasonalMean3Y":
            prediction = np.nanmean(
                [
                    history[-36],
                    history[-72] if len(history) >= 72 else np.nan,
                    history[-108] if len(history) >= 108 else np.nan,
                ]
            )
        elif name == "SeasonalTrend":
            recent = history[-36]
            previous = history[-72] if len(history) >= 72 else recent
            prediction = recent + 0.5 * (recent - previous)
        elif name == "RecentMean3":
            prediction = np.mean(history[-3:])
        elif name == "RecentMean6":
            prediction = np.mean(history[-6:])
        else:
            raise KeyError(name)
        prediction = _clip(prediction, history)
        predictions.append(prediction)
        history.append(prediction)
    return np.asarray(predictions)


def _fit_ml(name, train_df, feature_cols, params=None, seed=42):
    features, target = build_supervised(train_df)
    columns = [column for column in feature_cols if column in features.columns]
    if not columns:
        raise ValueError("没有可用特征")
    estimator = build_estimator(name, params=params, seed=seed)
    estimator.fit(features[columns], target)
    history = train_df.sort_values("date")
    return FittedForecastModel(
        name=name,
        kind="ml",
        estimator=estimator,
        feature_cols=columns,
        history_y=history.gas_sales.astype(float).tolist(),
        exog_history=history.to_dict("records"),
    )


def _fit_stat(name, train_df):
    target = train_df.sort_values("date").gas_sales.astype(float).values
    if name == "SARIMAX":
        from statsmodels.tsa.statespace.sarimax import SARIMAX

        estimator = SARIMAX(
            target,
            order=(1, 1, 1),
            seasonal_order=(1, 0, 0, 36),
            trend="t",
            enforce_stationarity=False,
            enforce_invertibility=False,
        ).fit(disp=False, maxiter=80)
    elif name == "ETS":
        from statsmodels.tsa.holtwinters import ExponentialSmoothing

        estimator = ExponentialSmoothing(
            target,
            trend="add",
            damped_trend=True,
            seasonal="add",
            seasonal_periods=36,
            initialization_method="estimated",
        ).fit(optimized=True, use_brute=False)
    elif name == "Theta":
        from statsmodels.tsa.forecasting.theta import ThetaModel

        estimator = ThetaModel(target, period=36, deseasonalize=True).fit()
    elif name == "UnobservedComponents":
        from statsmodels.tsa.statespace.structural import UnobservedComponents

        estimator = UnobservedComponents(
            target,
            level="local linear trend",
            seasonal=36,
        ).fit(disp=False, maxiter=100)
    else:
        raise KeyError(name)
    return FittedForecastModel(name=name, kind="stat", estimator=estimator)


def fit_candidate(spec, train_df, feature_cols, params=None, seed=42):
    """训练模型。返回值可以保存，预测阶段不需要再次 fit。"""

    try:
        history = train_df.sort_values("date")
        if spec.kind == "baseline":
            return FittedForecastModel(
                name=spec.name,
                kind="baseline",
                history_y=history.gas_sales.astype(float).tolist(),
            )
        if spec.kind == "stat":
            return _fit_stat(spec.name, history)
        return _fit_ml(
            spec.name,
            history,
            feature_cols,
            params=params,
            seed=seed,
        )
    except Exception as exc:
        raise RuntimeError(f"{spec.name} 训练失败: {exc}") from exc


def predict_candidate(model: FittedForecastModel, future_exog):
    """使用训练产物预测；此方法不会训练或修改传入的模型。"""

    try:
        future = future_exog.sort_values("date")
        if model.kind == "baseline":
            return forecast_baseline(model.name, model.history_y, len(future))
        if model.kind == "stat":
            return np.maximum(np.asarray(model.estimator.forecast(len(future))), 0)

        history_y = list(model.history_y)
        exog_history = list(model.exog_history)
        predictions = []
        for _, row in future.iterrows():
            feature = make_feature_row(
                row["date"], row.to_dict(), history_y, exog_history
            )
            model_row = pd.DataFrame(
                [
                    {
                        column: feature.get(column, np.nan)
                        for column in model.feature_cols
                    }
                ]
            )
            prediction = _clip(
                float(model.estimator.predict(model_row)[0]), history_y
            )
            predictions.append(prediction)
            history_y.append(prediction)
            exog_history.append(row.to_dict())
        return np.asarray(predictions)
    except Exception as exc:
        raise RuntimeError(f"{model.name} 预测失败: {exc}") from exc


def forecast_candidate(spec, train_df, future_exog, feature_cols, params=None, seed=42):
    """回测兼容入口：对一个历史切分执行训练后立即预测。"""

    model = fit_candidate(
        spec, train_df, feature_cols, params=params, seed=seed
    )
    return predict_candidate(model, future_exog)
