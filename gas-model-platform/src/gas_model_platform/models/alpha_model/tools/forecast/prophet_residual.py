"""
Prophet + 残差模型 集成预测工具

基于 Prophet 捕捉趋势/季节性，再用 XGBoost/LightGBM 拟合残差。
经过 walk-forward 验证的优化配置（CSSA 燃气数据上 MAPE 7.37%）。

核心模型：
- prophet_xgboost: Prophet + XGBoost 残差（推荐，WF MAPE 7.37%）
- prophet_lightgbm: Prophet + LightGBM 残差（WF MAPE 7.62%）
- prophet_ensemble: Prophet + XGB + LGB 多模型集成

特征工程：
- 滞后特征: lag1, lag7, lag14
- 滚动统计: roll_mean_7, roll_std_7
- 差分特征: diff1, diff7
- 时间特征: dow, doy, month
- Prophet 组件: p_trend, p_yearly, p_weekly

macOS 注意：同时加载 LightGBM 和 XGBoost 可能因 OpenMP 冲突死锁，
本模块会自动设置 OMP_NUM_THREADS=1 环境变量。
"""

from __future__ import annotations

import os
import warnings
from typing import Optional

import numpy as np
import pandas as pd

from ..base import ToolResult

# 修复 macOS OpenMP 死锁（放在导入库之前）
os.environ.setdefault("OMP_NUM_THREADS", "1")
warnings.filterwarnings("ignore")


# ============================================
# 特征工程
# ============================================

_RESID_FEATS = [
    "lag1", "lag7", "lag14",
    "dow", "doy", "month",
    "roll_mean_7", "roll_std_7",
    "diff1", "diff7",
    "p_yhat", "p_trend", "p_yearly", "p_weekly",
]


def _use_yearly_seasonality(ds: pd.Series) -> bool:
    """
    是否打开 Prophet 年季节。

    不足约两年的序列上强制 yearly=True，年傅里叶项会外推爆炸（需求被预测成负数）。
    """
    if ds is None or len(ds) < 2:
        return False
    span_days = (pd.to_datetime(ds.max()) - pd.to_datetime(ds.min())).days
    return span_days >= 365 * 2 - 14


def _fit_prophet(df: pd.DataFrame, changepoint_prior_scale: float = 0.05):
    """按序列长度选择季节性后拟合 Prophet。"""
    from prophet import Prophet

    yearly = _use_yearly_seasonality(df["ds"])
    cps = float(changepoint_prior_scale)
    if not yearly:
        cps = min(cps, 0.05)
    model = Prophet(
        yearly_seasonality=yearly,
        weekly_seasonality=True,
        daily_seasonality=False,
        changepoint_prior_scale=cps,
    )
    model.fit(df[["ds", "y"]])
    return model


def _safe_component(fc: pd.DataFrame, name: str) -> np.ndarray:
    """Prophet 组件缺失时填 0，避免短序列没有 yearly 列直接报错。"""
    if name in fc.columns:
        return fc[name].to_numpy(dtype=float)
    return np.zeros(len(fc), dtype=float)


def _clip_residual(value: float, resid_std: float, max_scale: float = 3.0) -> float:
    """把残差修正限制在训练残差标准差的若干倍内，避免递归发散。"""
    if resid_std is None or not np.isfinite(resid_std) or resid_std <= 0:
        return float(value)
    cap = max_scale * float(resid_std)
    return float(np.clip(value, -cap, cap))


def _build_residual_features(df_full, prophet_model, value_col="y"):
    """
    构建残差模型特征（Prophet 组件 + 滞后/滚动/时间特征）

    Parameters
    ----------
    df_full : pd.DataFrame，含 ds 和 value_col 列
    prophet_model : 已拟合的 Prophet 模型
    value_col : str

    Returns
    -------
    df_feat : DataFrame，包含所有特征列
    feature_cols : list[str]
    """
    x = df_full.copy()
    y = x[value_col]

    # 滞后特征
    for lag in [1, 7, 14]:
        x[f"lag{lag}"] = y.shift(lag)

    # 滚动统计
    x["roll_mean_7"] = y.shift(1).rolling(7, min_periods=3).mean()
    x["roll_std_7"] = y.shift(1).rolling(7, min_periods=3).std()

    # 差分特征
    x["diff1"] = y.shift(1) - y.shift(2)
    x["diff7"] = y.shift(1) - y.shift(7)

    # 时间特征
    x["dow"] = x["ds"].dt.dayofweek
    x["doy"] = x["ds"].dt.dayofyear
    x["month"] = x["ds"].dt.month

    # Prophet 组件（短序列可能没有 yearly）
    fc = prophet_model.predict(x[["ds"]])
    x["p_yhat"] = _safe_component(fc, "yhat")
    x["p_trend"] = _safe_component(fc, "trend")
    x["p_yearly"] = _safe_component(fc, "yearly")
    x["p_weekly"] = _safe_component(fc, "weekly")

    return x, _RESID_FEATS


def _recursive_resid_forecast(
    prophet_model,
    resid_model,
    last_known_df,
    value_col,
    horizon,
    feature_cols,
    resid_std: float = 0.0,
    y_floor: Optional[float] = None,
):
    """
    递归多步预测：Prophet 先出基础预测，残差模型逐步修正。

    残差会被裁剪，避免滞后特征把修正量放大后发散。
    """
    last_date = last_known_df["ds"].iloc[-1]
    freq = pd.infer_freq(last_known_df["ds"]) or "D"
    future_dates = pd.date_range(
        start=last_date + pd.tseries.frequencies.to_offset(freq),
        periods=horizon,
        freq=freq,
    )

    # Prophet 一次性预测未来 horizon 天
    future_df = pd.DataFrame({"ds": future_dates})
    p_future = prophet_model.predict(future_df)
    prophet_pred = _safe_component(p_future, "yhat")
    p_trend = _safe_component(p_future, "trend")
    p_yearly = _safe_component(p_future, "yearly")
    p_weekly = _safe_component(p_future, "weekly")

    # 维护历史值用于滞后/滚动特征
    history = list(last_known_df[value_col].astype(float).values)
    forecast_values = []
    resid_values = []

    for i, dt in enumerate(future_dates):
        row = {}

        # 滞后特征
        for lag in [1, 7, 14]:
            pos = len(history) - lag
            row[f"lag{lag}"] = history[pos] if pos >= 0 else history[-1]

        # 滚动特征
        recent7 = history[-7:] if len(history) >= 7 else history
        row["roll_mean_7"] = float(np.mean(recent7))
        row["roll_std_7"] = float(np.std(recent7)) if len(recent7) > 1 else 0.0

        # 差分特征
        row["diff1"] = history[-1] - history[-2] if len(history) >= 2 else 0.0
        row["diff7"] = history[-1] - history[-7] if len(history) >= 7 else 0.0

        # 时间特征
        row["dow"] = dt.dayofweek
        row["doy"] = dt.dayofyear
        row["month"] = dt.month

        # Prophet 组件
        row["p_yhat"] = float(prophet_pred[i])
        row["p_trend"] = float(p_trend[i])
        row["p_yearly"] = float(p_yearly[i])
        row["p_weekly"] = float(p_weekly[i])

        feat_row = np.array([[row.get(c, 0.0) for c in feature_cols]], dtype=np.float32)
        resid_pred = _clip_residual(float(resid_model.predict(feat_row)[0]), resid_std)

        final_pred = float(prophet_pred[i] + resid_pred)
        if y_floor is not None:
            final_pred = max(final_pred, y_floor)
        forecast_values.append(final_pred)
        resid_values.append(resid_pred)
        history.append(final_pred)

    return (
        np.array(forecast_values),
        prophet_pred,
        np.array(resid_values),
    )


def _make_result_df(dates, forecast, value_col, conf_int=None):
    """构造结果 DataFrame"""
    result = pd.DataFrame({"ds": dates, value_col: forecast})
    if conf_int is not None:
        result[f"{value_col}_lower"] = conf_int[:, 0]
        result[f"{value_col}_upper"] = conf_int[:, 1]
    return result


# ============================================
# 1. Prophet + XGBoost 残差（推荐）
# ============================================

def forecast_prophet_xgboost(
    train_df: pd.DataFrame,
    date_col: str,
    value_col: str,
    horizon: int = 30,
    changepoint_prior_scale: float = 0.2,
    max_depth: int = 4,
    learning_rate: float = 0.05,
    n_estimators: int = 500,
    reg_lambda: float = 5.0,
    reg_alpha: float = 0.5,
    min_child_weight: int = 15,
    subsample: float = 0.8,
    colsample_bytree: float = 0.8,
) -> ToolResult:
    """
    Prophet + XGBoost 残差集成预测

    经过 15 折 walk-forward 验证的优化配置：
    - Prophet: yearly + weekly 季节性, changepoint_prior_scale=0.2
    - XGBoost: max_depth=4, lr=0.05, 500 trees, 强正则化
    - 13 维特征：滞后(1/7/14) + 滚动 + 差分 + 时间 + Prophet组件

    CSSA 燃气数据表现: WF 平均 MAPE = 7.37% (优于 Prophet 基线 39.5%)

    XGBoost 不可用时自动退化为 Prophet+LightGBM，再退化为 Prophet。
    """
    try:
        df = train_df.rename(columns={date_col: "ds", value_col: "y"})
        df["ds"] = pd.to_datetime(df["ds"])
        df = df.sort_values("ds").reset_index(drop=True)

        # ---- Prophet 部分：短序列禁止强制年季节 ----
        m = _fit_prophet(df, changepoint_prior_scale)

        # ---- 构建特征 + 残差 ----
        df_feat, feat_cols = _build_residual_features(df, m, "y")
        mask = df_feat[feat_cols].notna().all(axis=1).values

        train_fc = m.predict(df[["ds"]])
        residuals = df["y"].values - train_fc["yhat"].values

        X_train = df_feat.loc[mask, feat_cols].values.astype(np.float32)
        y_resid = residuals[mask]

        # ---- XGBoost 残差模型 ----
        try:
            import xgboost as xgb
            model_used = "prophet_xgboost"
        except ImportError:
            # 退化为 LightGBM
            try:
                import lightgbm as lgb
                xgb = None
                model_used = "prophet_lightgbm_fallback"
            except ImportError:
                # 退化为纯 Prophet
                future = m.make_future_dataframe(periods=horizon)
                fc = m.predict(future)
                fc = fc.tail(horizon).reset_index(drop=True)
                return ToolResult(
                    data={
                        "forecast_df": _make_result_df(fc["ds"].values, fc["yhat"].values, value_col),
                        "forecast_values": fc["yhat"].tolist(),
                        "model": "prophet_fallback",
                        "note": "XGBoost 和 LightGBM 均不可用，退回纯 Prophet",
                    },
                    metadata={"model": "prophet_fallback", "horizon": horizon},
                )

        if xgb is not None:
            resid_model = xgb.XGBRegressor(
                n_estimators=n_estimators,
                max_depth=max_depth,
                learning_rate=learning_rate,
                subsample=subsample,
                colsample_bytree=colsample_bytree,
                reg_lambda=reg_lambda,
                reg_alpha=reg_alpha,
                min_child_weight=min_child_weight,
                verbosity=0,
                random_state=42,
                tree_method="hist",
                n_jobs=1,
            )
        else:
            # LightGBM fallback
            resid_model = lgb.LGBMRegressor(
                n_estimators=n_estimators,
                num_leaves=2 ** max_depth if max_depth > 0 else 31,
                max_depth=max_depth,
                learning_rate=learning_rate,
                subsample=subsample,
                colsample_bytree=colsample_bytree,
                reg_lambda=reg_lambda,
                reg_alpha=reg_alpha,
                min_child_samples=min_child_weight,
                verbosity=-1,
                random_state=42,
                n_jobs=1,
            )

        resid_model.fit(X_train, y_resid)

        resid_std = float(np.std(y_resid)) if len(y_resid) > 1 else 0.0
        y_floor = 0.0 if float(df["y"].min()) >= 0 else None
        last_known = df.tail(30).copy()
        forecast, prophet_pred, resid_pred = _recursive_resid_forecast(
            m, resid_model, last_known, "y", horizon, feat_cols,
            resid_std=resid_std, y_floor=y_floor,
        )

        # ---- 置信区间：用训练集残差 + XGB 残差的标准差 ----
        fitted_resid = resid_model.predict(X_train)
        total_resid = y_resid - fitted_resid
        resid_std = float(np.std(total_resid)) if len(total_resid) > 1 else 0.0
        step_scales = np.sqrt(np.arange(1, horizon + 1))
        conf_int = np.column_stack([
            forecast - 1.96 * resid_std * step_scales,
            forecast + 1.96 * resid_std * step_scales,
        ])

        # 特征重要性
        feat_imp = {}
        if hasattr(resid_model, "feature_importances_"):
            imp = resid_model.feature_importances_
            feat_imp = dict(zip(feat_cols, [float(v) for v in imp]))

        last_date = df["ds"].iloc[-1]
        freq = pd.infer_freq(df["ds"]) or "D"
        future_dates = pd.date_range(
            start=last_date + pd.tseries.frequencies.to_offset(freq),
            periods=horizon, freq=freq,
        )
        result_df = _make_result_df(future_dates, forecast, value_col, conf_int)

        return ToolResult(
            data={
                "forecast_df": result_df,
                "forecast_values": forecast.tolist(),
                "prophet_forecast": prophet_pred.tolist(),
                "residual_forecast": resid_pred.tolist(),
                "conf_int": conf_int.tolist(),
                "model": model_used,
                "feature_importance": feat_imp,
                "n_features": len(feat_cols),
                "prophet_config": {
                    "changepoint_prior_scale": changepoint_prior_scale,
                },
                "residual_config": {
                    "max_depth": max_depth,
                    "learning_rate": learning_rate,
                    "n_estimators": n_estimators,
                    "reg_lambda": reg_lambda,
                    "reg_alpha": reg_alpha,
                    "min_child_weight": min_child_weight,
                    "subsample": subsample,
                    "colsample_bytree": colsample_bytree,
                },
                "resid_std": resid_std,
            },
            metadata={
                "model": model_used,
                "horizon": horizon,
                "n_features": len(feat_cols),
                "resid_std": resid_std,
            },
        )
    except Exception as e:
        return ToolResult(data=None, success=False, error=str(e))


# ============================================
# 2. Prophet + LightGBM 残差
# ============================================

def forecast_prophet_lightgbm(
    train_df: pd.DataFrame,
    date_col: str,
    value_col: str,
    horizon: int = 30,
    changepoint_prior_scale: float = 0.2,
    num_leaves: int = 7,
    max_depth: int = 4,
    learning_rate: float = 0.01,
    n_estimators: int = 1000,
    reg_lambda: float = 20.0,
    reg_alpha: float = 1.0,
    min_child_samples: int = 30,
    subsample: float = 0.8,
    colsample_bytree: float = 0.8,
) -> ToolResult:
    """
    Prophet + LightGBM 残差集成预测

    经过 walk-forward 验证的优化配置：
    - LightGBM: num_leaves=7, max_depth=4, lr=0.01, 1000 trees, 强正则
    - 13 维特征同上

    CSSA 燃气数据表现: WF 平均 MAPE = 7.62%

    LightGBM 不可用时自动退化为 Prophet+XGBoost。
    """
    try:
        df = train_df.rename(columns={date_col: "ds", value_col: "y"})
        df["ds"] = pd.to_datetime(df["ds"])
        df = df.sort_values("ds").reset_index(drop=True)

        m = _fit_prophet(df, changepoint_prior_scale)

        df_feat, feat_cols = _build_residual_features(df, m, "y")
        mask = df_feat[feat_cols].notna().all(axis=1).values

        train_fc = m.predict(df[["ds"]])
        residuals = df["y"].values - train_fc["yhat"].values

        X_train = df_feat.loc[mask, feat_cols].values.astype(np.float32)
        y_resid = residuals[mask]

        # 尝试 LightGBM，不可用退回 XGBoost
        try:
            import lightgbm as lgb
            model_used = "prophet_lightgbm"
        except ImportError:
            lgb = None
            model_used = "prophet_xgboost_fallback"

        if lgb is not None:
            resid_model = lgb.LGBMRegressor(
                n_estimators=n_estimators,
                num_leaves=num_leaves,
                max_depth=max_depth,
                learning_rate=learning_rate,
                subsample=subsample,
                colsample_bytree=colsample_bytree,
                reg_lambda=reg_lambda,
                reg_alpha=reg_alpha,
                min_child_samples=min_child_samples,
                verbosity=-1,
                random_state=42,
                n_jobs=1,
            )
        else:
            import xgboost as xgb
            resid_model = xgb.XGBRegressor(
                n_estimators=n_estimators,
                max_depth=max_depth,
                learning_rate=learning_rate,
                subsample=subsample,
                colsample_bytree=colsample_bytree,
                reg_lambda=reg_lambda,
                reg_alpha=reg_alpha,
                min_child_weight=min_child_samples,
                verbosity=0,
                random_state=42,
                tree_method="hist",
                n_jobs=1,
            )

        resid_model.fit(X_train, y_resid)

        resid_std = float(np.std(y_resid)) if len(y_resid) > 1 else 0.0
        y_floor = 0.0 if float(df["y"].min()) >= 0 else None
        last_known = df.tail(30).copy()
        forecast, prophet_pred, resid_pred = _recursive_resid_forecast(
            m, resid_model, last_known, "y", horizon, feat_cols,
            resid_std=resid_std, y_floor=y_floor,
        )

        fitted_resid = resid_model.predict(X_train)
        total_resid = y_resid - fitted_resid
        resid_std = float(np.std(total_resid)) if len(total_resid) > 1 else 0.0
        step_scales = np.sqrt(np.arange(1, horizon + 1))
        conf_int = np.column_stack([
            forecast - 1.96 * resid_std * step_scales,
            forecast + 1.96 * resid_std * step_scales,
        ])

        feat_imp = {}
        if hasattr(resid_model, "feature_importances_"):
            imp = resid_model.feature_importances_
            feat_imp = dict(zip(feat_cols, [float(v) for v in imp]))

        last_date = df["ds"].iloc[-1]
        freq = pd.infer_freq(df["ds"]) or "D"
        future_dates = pd.date_range(
            start=last_date + pd.tseries.frequencies.to_offset(freq),
            periods=horizon, freq=freq,
        )
        result_df = _make_result_df(future_dates, forecast, value_col, conf_int)

        return ToolResult(
            data={
                "forecast_df": result_df,
                "forecast_values": forecast.tolist(),
                "prophet_forecast": prophet_pred.tolist(),
                "residual_forecast": resid_pred.tolist(),
                "conf_int": conf_int.tolist(),
                "model": model_used,
                "feature_importance": feat_imp,
                "n_features": len(feat_cols),
                "prophet_config": {
                    "changepoint_prior_scale": changepoint_prior_scale,
                },
                "residual_config": {
                    "num_leaves": num_leaves,
                    "max_depth": max_depth,
                    "learning_rate": learning_rate,
                    "n_estimators": n_estimators,
                    "reg_lambda": reg_lambda,
                    "reg_alpha": reg_alpha,
                    "min_child_samples": min_child_samples,
                    "subsample": subsample,
                    "colsample_bytree": colsample_bytree,
                },
                "resid_std": resid_std,
            },
            metadata={
                "model": model_used,
                "horizon": horizon,
                "n_features": len(feat_cols),
                "resid_std": resid_std,
            },
        )
    except Exception as e:
        return ToolResult(data=None, success=False, error=str(e))


# ============================================
# 3. Prophet + XGB + LGB 多模型集成
# ============================================

def forecast_prophet_ensemble(
    train_df: pd.DataFrame,
    date_col: str,
    value_col: str,
    horizon: int = 30,
    weights: Optional[dict] = None,
) -> ToolResult:
    """
    Prophet + XGBoost + LightGBM 多模型集成

    同时训练 Prophet+XGB 和 Prophet+LGB，按权重集成。
    默认权重: xgb=0.6, lgb=0.4（基于 walk-forward 验证 XGB 略优）

    某模型不可用时自动退化为可用模型的组合。
    """
    try:
        # 训练两个模型
        xgb_result = forecast_prophet_xgboost(
            train_df, date_col, value_col, horizon,
        )
        lgb_result = forecast_prophet_lightgbm(
            train_df, date_col, value_col, horizon,
        )

        if not xgb_result.success and not lgb_result.success:
            return ToolResult(
                data=None, success=False,
                error=f"XGBoost: {xgb_result.error}; LightGBM: {lgb_result.error}",
            )

        # 收集可用模型预测
        preds = {}
        if xgb_result.success:
            preds["prophet_xgboost"] = np.array(xgb_result.data["forecast_values"])
        if lgb_result.success:
            preds["prophet_lightgbm"] = np.array(lgb_result.data["forecast_values"])

        # 默认权重
        if weights is None:
            if len(preds) == 2:
                weights = {"prophet_xgboost": 0.6, "prophet_lightgbm": 0.4}
            else:
                weights = {k: 1.0 for k in preds}

        # 只保留可用模型的权重
        active_weights = {k: weights.get(k, 0.0) for k in preds}
        total = sum(active_weights.values())
        if total <= 0:
            active_weights = {k: 1.0 / len(preds) for k in preds}
        else:
            active_weights = {k: v / total for k, v in active_weights.items()}

        # 加权平均
        ensemble = np.zeros_like(list(preds.values())[0])
        for name, pred in preds.items():
            ensemble += active_weights[name] * pred

        # 日期
        df = train_df.rename(columns={date_col: "ds"})
        df["ds"] = pd.to_datetime(df["ds"])
        last_date = df["ds"].max()
        freq = pd.infer_freq(df.sort_values("ds")["ds"]) or "D"
        future_dates = pd.date_range(
            start=last_date + pd.tseries.frequencies.to_offset(freq),
            periods=horizon, freq=freq,
        )

        # 置信区间：用各模型保守估计
        all_lower = []
        all_upper = []
        for res in [xgb_result, lgb_result]:
            if res.success and "conf_int" in res.data:
                ci = np.array(res.data["conf_int"])
                all_lower.append(ci[:, 0])
                all_upper.append(ci[:, 1])
        if all_lower:
            conf_int = np.column_stack([
                np.min(all_lower, axis=0),
                np.max(all_upper, axis=0),
            ])
        else:
            conf_int = None

        result_df = _make_result_df(future_dates, ensemble, value_col, conf_int)

        # 各模型预测
        individual = {k: v.tolist() for k, v in preds.items()}

        return ToolResult(
            data={
                "forecast_df": result_df,
                "forecast_values": ensemble.tolist(),
                "conf_int": conf_int.tolist() if conf_int is not None else None,
                "model": "prophet_ensemble",
                "weights": active_weights,
                "individual_predictions": individual,
                "horizon": horizon,
            },
            metadata={
                "model": "prophet_ensemble",
                "horizon": horizon,
                "weights": active_weights,
                "components": list(preds.keys()),
            },
        )
    except Exception as e:
        return ToolResult(data=None, success=False, error=str(e))
