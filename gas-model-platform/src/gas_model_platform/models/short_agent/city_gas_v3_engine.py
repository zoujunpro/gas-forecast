"""
=============================================================================
predict_jiangsu_城燃_v3.py — 精简改进版城市燃气预测模型
=============================================================================

基于 v1 的简洁架构，针对性改进 v2 的过拟合问题：

改进策略（只保留真正有效的）:
  1. 保留 Prophet + AR(4) 基础架构（v1 已证明有效）
  2. 用轻量 LightGBM 替代 XGBoost 做残差修正 — 更快的 leaf-wise 生长，更好的正则化
  3. 严格避免数据泄漏：AR 特征在训练时基于 y_true 构造，预测时逐步滚动
  4. 特征分层：稳定基础特征 + 通过滚动回测的自动特征库
  5. L1/L2 正则化增强的 LightGBM，防止残差过拟合
  6. 回测时关闭 CPS 自动搜索（耗时且无帮助）
  7. 保留 v1→v2→v3 三模型对比

用法:
  python predict_jiangsu_城燃_v3.py
=============================================================================
"""

from __future__ import annotations

import logging
import sys
import time
import warnings
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import numpy as np
import pandas as pd

# ── 可选依赖 ──────────────────────────────────────────────────────────
try:
    from prophet import Prophet
except Exception:
    try:
        from fbprophet import Prophet
    except Exception:
        Prophet = None

try:
    import lightgbm as lgb

    HAS_LGB = True
except Exception:
    HAS_LGB = False

try:
    from xgboost import XGBRegressor

    HAS_XGB = True
except Exception:
    HAS_XGB = False

# ── 日志 & 警告 ────────────────────────────────────────────────────────
logging.getLogger("prophet").setLevel(logging.ERROR)
logging.getLogger("cmdstanpy").setLevel(logging.ERROR)
warnings.filterwarnings("ignore", category=FutureWarning)
warnings.filterwarnings("ignore", category=UserWarning)

# ── 全局常量 ───────────────────────────────────────────────────────────
DS_SHIFT_DAYS = 1
RANDOM_SEED = 42


# ═══════════════════════════════════════════════════════════════════════
# 工具函数
# ═══════════════════════════════════════════════════════════════════════

def compute_rmse_mape(df_eval: pd.DataFrame) -> Tuple[Optional[float], Optional[float]]:
    if df_eval is None or len(df_eval) == 0:
        return None, None
    y_true = pd.to_numeric(df_eval["y"], errors="coerce").astype("float64").values
    y_pred = pd.to_numeric(df_eval["yhat"], errors="coerce").astype("float64").values
    mask = np.isfinite(y_true) & np.isfinite(y_pred)
    if not np.any(mask):
        return None, None
    rmse = float(np.sqrt(np.mean((y_pred[mask] - y_true[mask]) ** 2)))
    mape_mask = mask & (y_true != 0)
    mape = (
        float(np.mean(np.abs((y_pred[mape_mask] - y_true[mape_mask]) / y_true[mape_mask])) * 100)
        if np.any(mape_mask) else None
    )
    return rmse, mape


# ═══════════════════════════════════════════════════════════════════════
# 特征工程（与 v1 一致 + 精简新增）
# ═══════════════════════════════════════════════════════════════════════

def add_engineered_features(df: pd.DataFrame, regs: List[str]) -> Tuple[pd.DataFrame, List[str]]:
    """添加 HDD/CDD、温度滞后、滚动均值等派生特征。"""
    out = df.copy()
    new_regs = []
    if "temp" in out.columns:
        t = out["temp"].astype("float64")
        out["hdd"] = (18.0 - t).clip(lower=0).astype("float32")
        out["cdd"] = (t - 26.0).clip(lower=0).astype("float32")
        out["hdd_lag1"] = out["hdd"].shift(1).astype("float32")
        out["hdd_roll3"] = out["hdd"].rolling(3, min_periods=1).mean().astype("float32")
        out["cdd_roll3"] = out["cdd"].rolling(3, min_periods=1).mean().astype("float32")
        new_regs += ["hdd", "cdd", "hdd_lag1", "hdd_roll3", "cdd_roll3"]
    if "tempmax" in out.columns and "tempmin" in out.columns:
        out["temp_range"] = (
            out["tempmax"].astype("float64") - out["tempmin"].astype("float64")
        ).astype("float32")
        out["temp_roll7"] = out["temp"].rolling(7, min_periods=1).mean().astype("float32")
        new_regs += ["temp_range", "temp_roll7"]
    if "humidity" in out.columns:
        out["humidity_roll3"] = out["humidity"].rolling(3, min_periods=1).mean().astype("float32")
        new_regs += ["humidity_roll3"]
    keep = []
    for c in new_regs:
        if c in out.columns and not out[c].isna().all():
            out[c] = out[c].interpolate(limit_direction="both").ffill().bfill().astype("float32")
            keep.append(c)
    return out, keep


def add_calendar_features(df: pd.DataFrame, min_ds: pd.Timestamp) -> pd.DataFrame:
    """添加精简日历特征（仅 4 个）。"""
    out = df.copy()
    out["dow"] = out["ds"].dt.dayofweek.astype(int)
    out["month"] = out["ds"].dt.month.astype(int)
    out["t"] = (out["ds"] - min_ds).dt.days.astype(int)
    out["is_weekend"] = (out["dow"] >= 5).astype(int)
    return out


# ═══════════════════════════════════════════════════════════════════════
# 节假日
# ═══════════════════════════════════════════════════════════════════════

def build_cn_holidays(start_ds: pd.Timestamp, end_ds: pd.Timestamp) -> Optional[pd.DataFrame]:
    if pd.isna(start_ds) or pd.isna(end_ds):
        return None
    start_ds = pd.to_datetime(start_ds).normalize()
    end_ds = pd.to_datetime(end_ds).normalize()
    if start_ds > end_ds:
        start_ds, end_ds = end_ds, start_ds
    years = list(range(int(start_ds.year), int(end_ds.year) + 1))

    cny = {
        2016: "2016-02-08", 2017: "2017-01-28", 2018: "2018-02-16",
        2019: "2019-02-05", 2020: "2020-01-25", 2021: "2021-02-12",
        2022: "2022-02-01", 2023: "2023-01-22", 2024: "2024-02-10",
        2025: "2025-01-29", 2026: "2026-02-17", 2027: "2027-02-06",
        2028: "2028-01-26",
    }
    recs = []
    for y in years:
        if y in cny:
            recs.append({"holiday": "春节", "ds": pd.Timestamp(cny[y]),
                         "lower_window": -10, "upper_window": 15})
        recs.append({"holiday": "元旦", "ds": pd.Timestamp(f"{y}-01-01"),
                     "lower_window": 0, "upper_window": 2})
        recs.append({"holiday": "劳动节", "ds": pd.Timestamp(f"{y}-05-01"),
                     "lower_window": -2, "upper_window": 4})
        recs.append({"holiday": "国庆节", "ds": pd.Timestamp(f"{y}-10-01"),
                     "lower_window": -2, "upper_window": 8})

    try:
        import holidays as _hol
        minor = {"清明": "清明节", "Tomb": "清明节", "端午": "端午节",
                 "Dragon": "端午节", "中秋": "中秋节", "Mid-Autumn": "中秋节"}
        cn = _hol.country_holidays("CN", years=years)
        for d, name in cn.items():
            key = next((minor[k] for k in minor if k in str(name)), None)
            if key:
                recs.append({"holiday": key, "ds": pd.Timestamp(d),
                             "lower_window": 0, "upper_window": 2})
    except Exception:
        pass

    if not recs:
        return None
    hol = pd.DataFrame(recs).drop_duplicates(subset=["holiday", "ds"]).sort_values("ds")
    hol["ds"] = hol["ds"] - pd.Timedelta(days=DS_SHIFT_DAYS)
    hol = hol[(hol["ds"] >= start_ds - pd.Timedelta(days=15))
              & (hol["ds"] <= end_ds + pd.Timedelta(days=15))]
    return hol if len(hol) else None


def build_holiday_proximity_features(
    ds_series: pd.Series, start_ds: pd.Timestamp, end_ds: pd.Timestamp
) -> pd.DataFrame:
    """构造节假日邻近度特征（仅 2 个：春节、国庆）。"""
    holidays_df = build_cn_holidays(start_ds, end_ds)
    out = pd.DataFrame({"ds": ds_series})
    out["days_to_cny"] = 365.0
    out["days_to_national_day"] = 365.0
    if holidays_df is None or holidays_df.empty:
        return out.drop(columns=["ds"])
    for _, row in holidays_df.iterrows():
        holiday_name = str(row["holiday"])
        holiday_ds = pd.Timestamp(row["ds"])
        dist = (out["ds"] - holiday_ds).dt.days.abs().astype(float)
        if "春节" in holiday_name:
            out["days_to_cny"] = np.minimum(out["days_to_cny"], dist)
        elif "国庆" in holiday_name:
            out["days_to_national_day"] = np.minimum(out["days_to_national_day"], dist)
    return out.drop(columns=["ds"])


# ═══════════════════════════════════════════════════════════════════════
# Prophet 拟合
# ═══════════════════════════════════════════════════════════════════════

def _doy_climatology(df_hist: pd.DataFrame, regs: List[str]) -> Dict[str, pd.Series]:
    if not regs:
        return {}
    tmp = df_hist[["ds"] + regs].copy()
    tmp["_md"] = tmp["ds"].dt.strftime("%m-%d")
    return {c: tmp.groupby("_md")[c].mean() for c in regs}


def build_future(
    df_hist: pd.DataFrame, regs: List[str], periods: int, freq: str,
    future_regs_df: Optional[pd.DataFrame] = None,
) -> pd.DataFrame:
    last_ds = df_hist["ds"].max()
    hist_regs = df_hist[["ds"] + regs].copy() if regs else df_hist[["ds"]].copy()
    if future_regs_df is not None:
        fut = future_regs_df[["ds"] + regs].copy() if regs else future_regs_df[["ds"]].copy()
        return pd.concat([hist_regs, fut], ignore_index=True)
    if periods <= 0:
        return hist_regs
    fut_dates = pd.date_range(last_ds + pd.tseries.frequencies.to_offset(freq),
                              periods=periods, freq=freq)
    fut = pd.DataFrame({"ds": fut_dates})
    clim = _doy_climatology(df_hist, regs)
    for c in regs:
        md_key = fut["ds"].dt.strftime("%m-%d")
        fallback = float(df_hist[c].iloc[-1])
        fut[c] = md_key.map(clim.get(c, {})).astype("float64").fillna(fallback)
    return pd.concat([hist_regs, fut], ignore_index=True)


def build_prophet(
    df: pd.DataFrame, regs: List[str], freq: str, periods: int,
    prophet_params: Optional[dict] = None,
) -> Prophet:
    """构建 Prophet 模型（与 v1 完全一致，不自动调 CPS）。"""
    if Prophet is None:
        raise RuntimeError("Prophet/fbprophet 未安装")

    train_start = df["ds"].min()
    train_end = df["ds"].max()
    end_ds = train_end + (pd.tseries.frequencies.to_offset(freq) * max(periods, 0))
    holidays_df = build_cn_holidays(train_start, end_ds)

    # special dates
    special_dates = [
        *pd.date_range("2024-08-21", "2024-08-23"),
        pd.Timestamp("2026-02-01"),
    ]
    if holidays_df is not None:
        special_holidays = pd.DataFrame({
            "holiday": "special", "ds": special_dates,
            "lower_window": 0, "upper_window": 0,
        })
        special_holidays["ds"] = special_holidays["ds"] - pd.Timedelta(days=DS_SHIFT_DAYS)
        holidays_df = pd.concat([holidays_df, special_holidays], ignore_index=True)
    else:
        holidays_df = pd.DataFrame({
            "holiday": "special", "ds": special_dates,
            "lower_window": 0, "upper_window": 0,
        })
        holidays_df["ds"] = holidays_df["ds"] - pd.Timedelta(days=DS_SHIFT_DAYS)

    p = prophet_params or {}
    cps = float(p.get("changepoint_prior_scale", 0.05))

    m = Prophet(
        holidays=holidays_df,
        holidays_prior_scale=float(p.get("holidays_prior_scale", 20.0)),
        yearly_seasonality=bool(p.get("yearly_seasonality", True)),
        weekly_seasonality=bool(p.get("weekly_seasonality", True)),
        daily_seasonality=bool(p.get("daily_seasonality", False)),
        seasonality_mode=p.get("seasonality_mode", "additive"),
        changepoint_prior_scale=cps,
        seasonality_prior_scale=float(p.get("seasonality_prior_scale", 10.0)),
        changepoint_range=p.get("changepoint_range", 0.8),
    )
    for r in regs:
        m.add_regressor(r, standardize="auto",
                        prior_scale=p.get("regressor_prior_scale", 10.0))
    return m


def prophet_predict(df_any: pd.DataFrame, m: Prophet) -> pd.DataFrame:
    """Prophet 预测并返回 yhat 及分解组件。"""
    fcst = m.predict(df_any[["ds"] + [c for c in m.extra_regressors if c in df_any.columns]])
    cols = ["ds", "yhat"]
    for c in ["trend", "weekly", "yearly", "additive_terms", "multiplicative_terms"]:
        if c in fcst.columns:
            cols.append(c)
    out = fcst[cols].copy()
    for c in ["trend", "weekly", "yearly", "additive_terms", "multiplicative_terms"]:
        if c not in out.columns:
            out[c] = 0.0
    return out


# ═══════════════════════════════════════════════════════════════════════
# AR 残差修正（与 v1 一致）
# ═══════════════════════════════════════════════════════════════════════

def fit_residual_ar(resid_values: np.ndarray, p: int = 4) -> Optional[np.ndarray]:
    r = np.asarray(resid_values, dtype="float64")
    r = r[np.isfinite(r)]
    if len(r) < p + 20:
        return None
    rows = len(r) - p
    X = np.empty((rows, p), dtype="float64")
    for i in range(p):
        X[:, i] = r[p - 1 - i: len(r) - 1 - i]
    yv = r[p:]
    Xb = np.column_stack([np.ones(rows), X])
    coef, *_ = np.linalg.lstsq(Xb, yv, rcond=None)
    return coef


def predict_residual_ar(fcst_join: pd.DataFrame, coef: np.ndarray, p: int = 4) -> np.ndarray:
    d = fcst_join.sort_values("ds")
    base = d["yhat_base"].astype("float64").values
    yv = d["y"].astype("float64").values
    resid_known = yv - base
    out = np.zeros(len(d), dtype="float64")
    hist = []
    for t in range(len(d)):
        if len(hist) >= p:
            x = np.empty(p + 1, dtype="float64")
            x[0] = 1.0
            for i in range(p):
                x[i + 1] = hist[-1 - i]
            pred = float(x @ coef)
        else:
            pred = 0.0
        out[t] = pred
        a = resid_known[t]
        hist.append(a if np.isfinite(a) else pred)
    return pd.Series(out, index=d.index).reindex(fcst_join.index).values


# ═══════════════════════════════════════════════════════════════════════
# v3 核心：LightGBM 轻量残差修正（精简特征 + 强正则化）
# ═══════════════════════════════════════════════════════════════════════

def build_lgb_features(
    df_any: pd.DataFrame,
    fcst_prophet: pd.DataFrame,
    holiday_start: Optional[pd.Timestamp] = None,
    holiday_end: Optional[pd.Timestamp] = None,
) -> pd.DataFrame:
    """构造 LightGBM 精简特征矩阵。

    特征选择原则：保留稳定的基础特征，再加载通过时序滚动回测的自动特征。
      - Prophet 基线 + trend（yhat 本身含 weekly/yearly，trend 单独提供长期趋势）
      - HDD / CDD（温度偏离度，城市燃气核心驱动）
      - 日历特征（dow / is_weekend / month — 燃气用量有周内模式）
      - 节假日邻近度（days_to_cny — 春节前用量骤变）
      - 自动特征库（仅注入通过泄漏检查和滚动回测的未来可用特征）
    """
    has_hdd = "hdd" in df_any.columns
    has_cdd = "cdd" in df_any.columns

    data: Dict[str, np.ndarray] = {
        "prophet_yhat": fcst_prophet["yhat"].astype(float).values,
        "prophet_trend": fcst_prophet["trend"].astype(float).values,
    }

    if has_hdd:
        data["hdd"] = df_any["hdd"].astype(float).values
    if has_cdd:
        data["cdd"] = df_any["cdd"].astype(float).values

    for c in ["dow", "month", "is_weekend"]:
        if c in df_any.columns:
            data[c] = df_any[c].astype(float).values
        else:
            # 动态生成日历特征
            dow_vals = df_any["ds"].dt.dayofweek.astype(float).values
            if c == "dow":
                data["dow"] = dow_vals
            elif c == "month":
                data["month"] = df_any["ds"].dt.month.astype(float).values
            elif c == "is_weekend":
                data["is_weekend"] = (dow_vals >= 5).astype(float)

    X = pd.DataFrame(data)

    # 节假日邻近度（仅春节）
    if holiday_start is not None and holiday_end is not None:
        hp_df = build_holiday_proximity_features(df_any["ds"], holiday_start, holiday_end)
        for c in ["days_to_cny", "days_to_national_day"]:
            X[c] = hp_df[c].values if c in hp_df.columns else 365.0
    else:
        X["days_to_cny"] = 365.0
        X["days_to_national_day"] = 365.0

    # 自动特征库是可选增强：没有已录用特征或独立模块异常时，
    # 原 v3 特征和预测流程保持不变。
    try:
        from quantaalpha.gas_features.runtime import apply_accepted_features

        auto_frame, auto_names, auto_diagnostics = apply_accepted_features(
            df_any,
            sector="city_gas",
        )
        for feature_name in auto_names:
            if feature_name not in X.columns:
                X[feature_name] = pd.to_numeric(
                    auto_frame[feature_name], errors="coerce"
                ).values
        if auto_names:
            logging.getLogger(__name__).info(
                "Loaded %d accepted automatic gas features: %s",
                len(auto_names),
                ", ".join(auto_names),
            )
        if auto_diagnostics.get("skipped"):
            logging.getLogger(__name__).warning(
                "Skipped invalid automatic gas features: %s",
                auto_diagnostics["skipped"],
            )
    except Exception as exc:
        logging.getLogger(__name__).warning(
            "Automatic gas features unavailable; using the v3 baseline: %s",
            exc,
        )

    # NaN/inf 兜底
    for c in X.columns:
        if X[c].dtype in ("float64", "float32"):
            med = float(np.nanmedian(X[c].values)) if np.isfinite(np.nanmedian(X[c].values)) else 0.0
            X[c] = X[c].replace([np.inf, -np.inf], np.nan).fillna(med)

    return X


def train_lgb_residual(
    X_tr: pd.DataFrame, y_tr: np.ndarray,
    X_va: pd.DataFrame, y_va: np.ndarray,
) -> Optional[lgb.Booster]:
    """训练轻量 LightGBM 残差修正模型，强正则化防止过拟合。

    关键设计：
      - max_depth=3（浅树，避免过拟合残差噪声）
      - min_child_samples=20（叶子最少样本数，防止小样本分裂）
      - reg_alpha=0.5, reg_lambda=1.0（L1+L2 正则化）
      - num_leaves=15（远小于默认 31，限制模型复杂度）
      - early_stopping_rounds=30（早停，验证集不提升则停止）
    """
    if not HAS_LGB:
        return None

    params = {
        "objective": "regression",
        "metric": "rmse",
        "boosting_type": "gbdt",
        "num_leaves": 15,
        "max_depth": 3,
        "learning_rate": 0.03,
        "min_child_samples": 20,
        "subsample": 0.8,
        "colsample_bytree": 0.8,
        "reg_alpha": 0.5,
        "reg_lambda": 1.0,
        "min_split_gain": 0.1,
        "verbosity": -1,
        "random_state": RANDOM_SEED,
        "n_jobs": 4,
    }

    # 如果验证集太小（< 20），不验证直接用全部训练
    if len(y_va) < 20:
        dtrain = lgb.Dataset(X_tr, label=y_tr)
        model = lgb.train(params, dtrain, num_boost_round=300)
        return model

    dtrain = lgb.Dataset(X_tr, label=y_tr)
    dval = lgb.Dataset(X_va, label=y_va, reference=dtrain)

    model = lgb.train(
        params,
        dtrain,
        num_boost_round=500,
        valid_sets=[dval],
        callbacks=[lgb.early_stopping(30), lgb.log_evaluation(0)],
    )
    return model


# ═══════════════════════════════════════════════════════════════════════
# 核心预测函数
# ═══════════════════════════════════════════════════════════════════════

def run_single_forecast_v1(
    df: pd.DataFrame, regs: List[str], periods: int, freq: str,
    prophet_params: Optional[dict] = None,
    future_regs_df: Optional[pd.DataFrame] = None,
    ar_order: int = 4,
) -> Tuple[pd.DataFrame, Optional[float], Optional[float]]:
    """v1 模型：Prophet + AR 残差修正（与原版一致）。"""
    m = build_prophet(df, regs=regs, freq=freq, periods=periods,
                      prophet_params=prophet_params)
    m.fit(df[["ds", "y"] + regs])
    future = build_future(df, regs, periods=periods, freq=freq, future_regs_df=future_regs_df)
    fcst = m.predict(future)

    fcst_join = fcst.merge(df[["ds", "y"]], on="ds", how="left")
    fcst_join.rename(columns={"yhat": "yhat_base", "yhat_lower": "yhat_lower_base",
                               "yhat_upper": "yhat_upper_base"}, inplace=True)

    feature_cols = ["yhat_base"] + regs
    overlap = fcst_join[~fcst_join["y"].isna()].copy()
    overlap["resid"] = (overlap["y"].astype("float64") - overlap["yhat_base"].astype("float64")).astype("float32")
    overlap = overlap.dropna(subset=feature_cols + ["resid"])

    resid_hat = None
    if len(overlap) >= max(30, ar_order + 20):
        coef = fit_residual_ar(overlap.sort_values("ds")["resid"].values, p=ar_order)
        if coef is not None:
            resid_hat = predict_residual_ar(fcst_join, coef, p=ar_order)

    if resid_hat is not None:
        fcst_join["resid_hat"] = resid_hat
        resid_adj = pd.to_numeric(fcst_join["resid_hat"], errors="coerce").fillna(0).astype("float64")
        fcst_join["yhat"] = (fcst_join["yhat_base"].astype("float64") + resid_adj).astype("float64")
        try:
            ov = fcst_join.merge(overlap[["ds", "resid"]], on="ds", how="inner")
            r2 = ov["resid"].astype("float64") - ov["resid_hat"].astype("float64")
            sigma_r = float(np.nanstd(r2.values))
        except Exception:
            sigma_r = 0.0
        widen = 1.2816 * sigma_r
        fcst_join["yhat_lower"] = (fcst_join["yhat_lower_base"].astype("float64") + resid_adj - widen).astype("float64")
        fcst_join["yhat_upper"] = (fcst_join["yhat_upper_base"].astype("float64") + resid_adj + widen).astype("float64")
    else:
        fcst_join["resid_hat"] = np.nan
        fcst_join["yhat"] = fcst_join["yhat_base"].astype("float64")
        fcst_join["yhat_lower"] = fcst_join["yhat_lower_base"].astype("float64")
        fcst_join["yhat_upper"] = fcst_join["yhat_upper_base"].astype("float64")

    eval_overlap = fcst_join[~fcst_join["y"].isna()].dropna(subset=["yhat"])
    rmse, mape = compute_rmse_mape(eval_overlap)
    return fcst_join, rmse, mape


def run_single_forecast_v3(
    df: pd.DataFrame, regs: List[str], periods: int, freq: str,
    prophet_params: Optional[dict] = None,
    future_regs_df: Optional[pd.DataFrame] = None,
    ar_order: int = 4,
) -> Tuple[pd.DataFrame, Optional[float], Optional[float]]:
    """v3 模型：Prophet + LightGBM 轻量残差修正（增强正则化）。

    与 v2 的关键区别：
      1. LightGBM 替代 XGBoost（leaf-wise，天然更适合时间序列残差）
      2. 特征从 30+ 精简到 10 个（去掉了 AR 特征、交互特征、temp_range 等）
      3. 强 L1/L2 正则化 + 浅树 + 大 min_child_samples
      4. 不使用 AR 特征避免数据泄漏
      5. 不跑 CPS 自动网格搜索（回测中每步都跑太慢且无益）
    """
    # ── 1. Prophet 拟合 ──
    m = build_prophet(df, regs=regs, freq=freq, periods=periods,
                      prophet_params=prophet_params)
    m.fit(df[["ds", "y"] + regs])
    future = build_future(df, regs, periods=periods, freq=freq, future_regs_df=future_regs_df)
    fcst = m.predict(future)

    fcst_join = fcst.merge(df[["ds", "y"]], on="ds", how="left")
    fcst_join.rename(columns={"yhat": "yhat_base", "yhat_lower": "yhat_lower_base",
                               "yhat_upper": "yhat_upper_base"}, inplace=True)

    # ── 2. 找出 overlap 区域（有真实 y 的部分）──
    overlap = fcst_join[~fcst_join["y"].isna()].copy()
    overlap["resid"] = (overlap["y"].astype("float64") - overlap["yhat_base"].astype("float64")).astype("float32")
    overlap = overlap.dropna(subset=["resid"])

    min_ds = df["ds"].min()
    end_ds = fcst_join["ds"].max()

    # ── 3. LightGBM 残差修正 ──
    resid_hat_lgb = None
    lgb_model = None
    if HAS_LGB and len(overlap) >= 40:
        overlap_sorted = overlap.sort_values("ds")
        n = len(overlap_sorted)

        # 使用后 30% 作为验证集（时序切分，不是随机）
        split_idx = max(int(n * 0.7), 20)
        ov_tr = overlap_sorted.iloc[:split_idx]
        ov_va = overlap_sorted.iloc[split_idx:]

        # 添加日历特征用于构造 LGB 输入
        ov_tr = add_calendar_features(ov_tr, min_ds=min_ds)
        ov_va = add_calendar_features(ov_va, min_ds=min_ds)
        fcst_join_cal = add_calendar_features(fcst_join.copy(), min_ds=min_ds)

        fcst_prophet_tr = prophet_predict(ov_tr, m)
        fcst_prophet_va = prophet_predict(ov_va, m)
        fcst_prophet_all = prophet_predict(fcst_join_cal, m)

        X_tr = build_lgb_features(ov_tr, fcst_prophet_tr, min_ds, end_ds)
        y_tr = ov_tr["resid"].astype("float32").values

        X_va = build_lgb_features(ov_va, fcst_prophet_va, min_ds, end_ds)
        y_va = ov_va["resid"].astype("float32").values

        lgb_model = train_lgb_residual(X_tr, y_tr, X_va, y_va)

        if lgb_model is not None:
            X_all = build_lgb_features(fcst_join_cal, fcst_prophet_all, min_ds, end_ds)
            resid_hat_lgb = lgb_model.predict(X_all).astype(np.float32)

    # ── 4. AR 残差修正（作为备用，如果 LGB 不可用则回退到纯 AR）──
    resid_hat_ar = None
    if len(overlap) >= max(30, ar_order + 20):
        coef = fit_residual_ar(overlap.sort_values("ds")["resid"].values, p=ar_order)
        if coef is not None:
            resid_hat_ar = predict_residual_ar(fcst_join, coef, p=ar_order)

    # ── 5. 选择残差修正策略 ──
    # 优先使用 LightGBM（如果可用），否则回退到 AR
    if resid_hat_lgb is not None:
        resid_hat = resid_hat_lgb
        method = "lgb"
    elif resid_hat_ar is not None:
        resid_hat = resid_hat_ar
        method = "ar"
    else:
        resid_hat = None
        method = "none"

    # ── 6. 应用残差修正 ──
    if resid_hat is not None:
        fcst_join["resid_hat"] = resid_hat
        resid_adj = pd.to_numeric(fcst_join["resid_hat"], errors="coerce").fillna(0).astype("float64")
        fcst_join["yhat"] = (fcst_join["yhat_base"].astype("float64") + resid_adj).astype("float64")
        try:
            ov = fcst_join.merge(overlap[["ds", "resid"]], on="ds", how="inner")
            r2 = ov["resid"].astype("float64") - ov["resid_hat"].astype("float64")
            sigma_r = float(np.nanstd(r2.values))
        except Exception:
            sigma_r = 0.0
        widen = 1.2816 * sigma_r
        fcst_join["yhat_lower"] = (fcst_join["yhat_lower_base"].astype("float64") + resid_adj - widen).astype("float64")
        fcst_join["yhat_upper"] = (fcst_join["yhat_upper_base"].astype("float64") + resid_adj + widen).astype("float64")
    else:
        fcst_join["resid_hat"] = np.nan
        fcst_join["yhat"] = fcst_join["yhat_base"].astype("float64")
        fcst_join["yhat_lower"] = fcst_join["yhat_lower_base"].astype("float64")
        fcst_join["yhat_upper"] = fcst_join["yhat_upper_base"].astype("float64")

    eval_overlap = fcst_join[~fcst_join["y"].isna()].dropna(subset=["yhat"])
    rmse, mape = compute_rmse_mape(eval_overlap)
    return fcst_join, rmse, mape


# ═══════════════════════════════════════════════════════════════════════
# 滚动回测
# ═══════════════════════════════════════════════════════════════════════

def rolling_backtest(
    df_all: pd.DataFrame, regs: List[str], horizon: int, freq: str,
    min_train_days: int, stride: int,
    eval_start: pd.Timestamp, eval_end: Optional[pd.Timestamp] = None,
    prophet_params: Optional[dict] = None, ar_order: int = 4,
    version: str = "v3",
) -> Tuple[pd.DataFrame, Optional[float], Optional[float]]:
    """滚动回测：逐步推进训练截止日期，评估多步预测精度。"""
    df_all = df_all.sort_values("ds").reset_index(drop=True)
    if horizon <= 0:
        raise ValueError("horizon 必须 > 0")

    first_ds = df_all["ds"].min()
    last_ds = df_all["ds"].max()
    # min_train_days 表示包含首尾日期在内的训练样本数。首日已经算 1 天，
    # 因此最早 cutoff 应为 first_ds + (min_train_days - 1)。此前这里多加
    # 了 1 天，导致恰好 140 条数据（90 天训练 + 50 天评估）被误判不足。
    min_train_end = first_ds + pd.Timedelta(days=max(0, int(min_train_days) - 1))

    if eval_end is not None:
        last_cutoff = min(last_ds - (pd.tseries.frequencies.to_offset(freq) * horizon), eval_end)
    else:
        last_cutoff = last_ds - (pd.tseries.frequencies.to_offset(freq) * horizon)

    start_cutoff = max(min_train_end, eval_start - pd.Timedelta(days=horizon))
    if start_cutoff > last_cutoff:
        raise ValueError(f"eval date range {eval_start} to {eval_end} is invalid or insufficient data")

    full_reg_df = df_all[["ds"] + regs].copy() if regs else df_all[["ds"]].copy()

    cutoffs = pd.date_range(start_cutoff, last_cutoff,
                            freq=pd.tseries.frequencies.to_offset(freq) * int(stride))
    n_cut = len(cutoffs)
    if n_cut > 300:
        print(f"  [warn] 滚动回测将进行 {n_cut} 次模型拟合")

    forecast_fn = {
        "v1": run_single_forecast_v1,
        "v2": None,  # v2 通过 v2 文件运行
        "v3": run_single_forecast_v3,
    }[version]

    rows = []
    for cutoff in cutoffs:
        train = df_all[df_all["ds"] <= cutoff].copy()
        if len(train) < 10:
            continue

        pred_dates = pd.date_range(cutoff + pd.tseries.frequencies.to_offset(freq),
                                   periods=horizon, freq=freq)
        pred_df = pd.DataFrame({"ds": pred_dates})
        if regs:
            pred_df = pred_df.merge(full_reg_df, on="ds", how="left")
            clim = _doy_climatology(train, regs)
            for r in regs:
                if pred_df[r].isna().any():
                    md_key = pred_df["ds"].dt.strftime("%m-%d")
                    fallback = float(train[r].iloc[-1])
                    filled = md_key.map(clim.get(r, {})).astype("float64").fillna(fallback)
                    pred_df[r] = pred_df[r].astype("float64").fillna(filled)

        fcst_join, _, _ = forecast_fn(
            df=train, regs=regs, periods=horizon, freq=freq,
            prophet_params=prophet_params, future_regs_df=pred_df,
            ar_order=ar_order,
        )
        pred_part = fcst_join[fcst_join["ds"].isin(pred_dates)][
            ["ds", "yhat", "yhat_lower", "yhat_upper", "yhat_base", "resid_hat"]
        ].copy()
        pred_part["cutoff"] = cutoff
        rows.append(pred_part)

    if not rows:
        raise ValueError("滚动回测没有产生任何预测结果")

    pred_all = pd.concat(rows, ignore_index=True)
    pred_all = pred_all.sort_values(["ds", "cutoff"]).drop_duplicates(subset=["ds"], keep="last")
    pred_all = pred_all.merge(df_all[["ds", "y"]], on="ds", how="left")
    pred_all = pred_all.dropna(subset=["y", "yhat"])
    pred_all = pred_all[pred_all["ds"] >= eval_start].copy()
    if eval_end is not None:
        pred_all = pred_all[pred_all["ds"] <= eval_end].copy()

    rmse, mape = compute_rmse_mape(pred_all[["y", "yhat"]])
    return pred_all.sort_values("ds").reset_index(drop=True), rmse, mape


# ═══════════════════════════════════════════════════════════════════════
# Main — v1 vs v2 vs v3 对比
# ═══════════════════════════════════════════════════════════════════════

def main():
    base = Path(__file__).resolve().parent

    data_path = base / "test.xlsx"
    eval_start_date = pd.Timestamp("2025-01-01")
    eval_end_date = pd.Timestamp("2026-01-01")
    horizon = 50
    stride = 50
    min_train_days = 90
    freq = "D"
    ar_order = 4

    print("=" * 80)
    print("城市燃气预测模型对比: v1 (Prophet+AR) vs v3 (Prophet+LightGBM)")
    print("=" * 80)
    print(f"数据文件: {data_path}")
    print(f"评估区间: {eval_start_date.date()} ~ {eval_end_date.date()}")
    print(f"预测步长: {horizon} 天")
    print(f"回测步长: {stride} 天")
    print(f"AR 阶数: {ar_order}")
    print(f"日期位移: {DS_SHIFT_DAYS} 天")
    print(f"LightGBM: {'可用' if HAS_LGB else '不可用 (v3 将回退为 AR only)'}")
    print()

    # ── 加载数据 ──
    df = pd.read_excel(data_path)
    df = df.rename(columns={"日期": "ds"})
    df["ds"] = pd.to_datetime(df["ds"])
    df["y"] = pd.to_numeric(df["y"], errors="coerce")

    regs_initial = [c for c in df.columns if c not in ["ds", "y"]]
    df[regs_initial] = df[regs_initial].apply(pd.to_numeric, errors="coerce")
    df[regs_initial] = df[regs_initial].interpolate(limit_direction="both").ffill().bfill()

    df, eng_regs = add_engineered_features(df, regs_initial)
    final_regs = list(dict.fromkeys([*regs_initial, *eng_regs]))

    df["ds"] = df["ds"] - pd.Timedelta(days=DS_SHIFT_DAYS)
    df = df.dropna(subset=["ds", "y"]).sort_values("ds").reset_index(drop=True)

    print(f"数据范围: {df['ds'].min().date()} ~ {df['ds'].max().date()}")
    print(f"数据点数: {len(df)}")
    print(f"原始特征: {len(regs_initial)}  工程特征: {len(eng_regs)}  总特征: {len(final_regs)}")
    print()

    prophet_params = {
        "seasonality_mode": "additive",
        "changepoint_prior_scale": 0.05,
        "seasonality_prior_scale": 10.0,
        "holidays_prior_scale": 20.0,
    }

    # ══════════════════════════════════════════════════════════════════
    # v1 滚动回测
    # ══════════════════════════════════════════════════════════════════
    print("=" * 80)
    print("[v1] Prophet + AR 滚动回测...")
    print("=" * 80)
    t0 = time.time()
    pred_v1, rmse_v1, mape_v1 = rolling_backtest(
        df_all=df, regs=final_regs, horizon=horizon, freq=freq,
        min_train_days=min_train_days, stride=stride,
        eval_start=eval_start_date - pd.Timedelta(days=DS_SHIFT_DAYS),
        eval_end=eval_end_date - pd.Timedelta(days=DS_SHIFT_DAYS),
        prophet_params=prophet_params, ar_order=ar_order, version="v1",
    )
    t_v1 = time.time() - t0
    print(f"  v1 完成!  RMSE={rmse_v1:.2f}  MAPE={mape_v1:.2f}%  耗时={t_v1:.1f}s")
    print()

    # ══════════════════════════════════════════════════════════════════
    # v3 滚动回测
    # ══════════════════════════════════════════════════════════════════
    print("=" * 80)
    print("[v3] Prophet + LightGBM 滚动回测...")
    print("=" * 80)
    t0 = time.time()
    pred_v3, rmse_v3, mape_v3 = rolling_backtest(
        df_all=df, regs=final_regs, horizon=horizon, freq=freq,
        min_train_days=min_train_days, stride=stride,
        eval_start=eval_start_date - pd.Timedelta(days=DS_SHIFT_DAYS),
        eval_end=eval_end_date - pd.Timedelta(days=DS_SHIFT_DAYS),
        prophet_params=prophet_params, ar_order=ar_order, version="v3",
    )
    t_v3 = time.time() - t0
    print(f"  v3 完成!  RMSE={rmse_v3:.2f}  MAPE={mape_v3:.2f}%  耗时={t_v3:.1f}s")
    print()

    # ══════════════════════════════════════════════════════════════════
    # 读取 v2 之前的运行结果（如果存在）
    # ══════════════════════════════════════════════════════════════════
    summary_v2_path = base / "城燃_模型对比汇总.csv"
    rmse_v2 = None
    mape_v2 = None
    t_v2 = None
    if summary_v2_path.exists():
        try:
            df_summary = pd.read_csv(summary_v2_path)
            for _, row in df_summary.iterrows():
                if "v2" in str(row["模型"]):
                    rmse_v2 = float(row["RMSE"])
                    mape_v2 = float(row["MAPE(%)"])
                    t_v2 = float(row["耗时(s)"])
                    break
        except Exception:
            pass

    # ══════════════════════════════════════════════════════════════════
    # 对比结果
    # ══════════════════════════════════════════════════════════════════
    print("=" * 80)
    print("三模型对比结果")
    print("=" * 80)

    col_widths = {"指标": 12, "v1 (Prophet+AR)": 22, "v3 (LightGBM)": 22}
    if rmse_v2 is not None:
        col_widths["v2 (XGBoost,旧)"] = 22

    header = f"{'指标':<12}"
    header += f" {'v1 (Prophet+AR)':<22}"
    if rmse_v2 is not None:
        header += f" {'v2 (XGBoost,旧)':<22}"
    header += f" {'v3 (LightGBM)':<22}"
    print(header)
    print("-" * len(header))

    # RMSE 行
    rmse_line = f"{'RMSE':<12} {rmse_v1:<22.2f}"
    if rmse_v2 is not None:
        rmse_line += f" {rmse_v2:<22.2f}"
    rmse_line += f" {rmse_v3:<22.2f}"
    print(rmse_line)

    # MAPE 行
    mape_line = f"{'MAPE':<12} {mape_v1:<22.2f}%"
    if mape_v2 is not None:
        mape_line += f" {mape_v2:<22.2f}%"
    mape_line += f" {mape_v3:<22.2f}%"
    print(mape_line)

    # 耗时行
    time_line = f"{'耗时':<12} {t_v1:<22.1f}s"
    if t_v2 is not None:
        time_line += f" {t_v2:<22.1f}s"
    time_line += f" {t_v3:<22.1f}s"
    print(time_line)

    # 改进幅度
    print()
    if rmse_v2 is not None:
        imp_v3_vs_v2 = (mape_v2 - mape_v3) / mape_v2 * 100
        print(f"  v3 vs v2: MAPE 改进 {imp_v3_vs_v2:+.2f}%")

    if mape_v3 is not None and mape_v1 is not None:
        imp_v3_vs_v1 = (mape_v1 - mape_v3) / mape_v1 * 100
        print(f"  v3 vs v1: MAPE 改进 {imp_v3_vs_v1:+.2f}%")

    # 判断优胜
    candidates = {"v1": mape_v1}
    if mape_v2 is not None:
        candidates["v2"] = mape_v2
    candidates["v3"] = mape_v3
    valid = {k: v for k, v in candidates.items() if v is not None}
    winner = min(valid, key=valid.get) if valid else "v1"
    print(f"\n综合优胜: {winner} (MAPE={valid[winner]:.2f}%)")
    print()

    # ══════════════════════════════════════════════════════════════════
    # 保存结果
    # ══════════════════════════════════════════════════════════════════
    # v1 结果
    pred_v1_out = pred_v1.copy()
    pred_v1_out["ds"] = pred_v1_out["ds"] + pd.Timedelta(days=DS_SHIFT_DAYS)
    out_v1_csv = base / "城燃_v1_rolling_h50_ar.csv"
    pred_v1_out.to_csv(out_v1_csv, index=False, encoding="utf-8-sig")
    print(f"v1 回测结果: {out_v1_csv}")

    # v3 结果
    pred_v3_out = pred_v3.copy()
    pred_v3_out["ds"] = pred_v3_out["ds"] + pd.Timedelta(days=DS_SHIFT_DAYS)
    out_v3_csv = base / "城燃_v3_rolling_h50_lgb.csv"
    pred_v3_out.to_csv(out_v3_csv, index=False, encoding="utf-8-sig")
    print(f"v3 回测结果: {out_v3_csv}")

    # ══════════════════════════════════════════════════════════════════
    # 未来预测 (用优胜模型)
    # ══════════════════════════════════════════════════════════════════
    print(f"\n使用全量数据训练最终 {winner} 模型并预测未来...")
    forecast_fn = run_single_forecast_v3 if winner == "v3" else run_single_forecast_v1
    fcst_join, _, _ = forecast_fn(
        df=df, regs=final_regs, periods=horizon, freq=freq,
        prophet_params=prophet_params, ar_order=ar_order,
    )
    fcst_join["ds"] = fcst_join["ds"] + pd.Timedelta(days=DS_SHIFT_DAYS)
    future_df = fcst_join[fcst_join["y"].isna()].copy().sort_values("ds")

    out_future_csv = base / f"城燃_{winner}_future_h50.csv"
    future_df[["ds", "yhat", "yhat_lower", "yhat_upper"]].to_csv(
        out_future_csv, index=False, encoding="utf-8-sig")
    print(f"未来预测: {out_future_csv}")

    # ══════════════════════════════════════════════════════════════════
    # 可视化
    # ══════════════════════════════════════════════════════════════════
    import matplotlib.pyplot as plt
    plt.rcParams["font.sans-serif"] = ["SimHei", "Microsoft YaHei"]
    plt.rcParams["axes.unicode_minus"] = False

    n_plots = 3 if (rmse_v2 is not None) else 2
    fig, axes = plt.subplots(n_plots, 1, figsize=(16, 5 * n_plots))

    plot_v1 = pred_v1_out.sort_values("ds")
    plot_v3 = pred_v3_out.sort_values("ds")

    # ── 图1: v1 vs v3 回测对比 ──
    ax1 = axes[0]
    ax1.plot(plot_v1["ds"].values, plot_v1["y"].values,
             label="实际值", color="#10b981", linewidth=1.5, marker="o", markersize=3)
    ax1.plot(plot_v1["ds"].values, plot_v1["yhat"].values,
             label=f"v1 Prophet+AR (MAPE={mape_v1:.2f}%)", color="#3b82f6",
             linewidth=1.5, linestyle="--", marker="s", markersize=2)
    ax1.plot(plot_v3["ds"].values, plot_v3["yhat"].values,
             label=f"v3 Prophet+LGB (MAPE={mape_v3:.2f}%)", color="#f59e0b",
             linewidth=1.5, marker="^", markersize=3)
    ax1.set_title(f"城市燃气 — v1 vs v3 回测对比 ({eval_start_date.date()} ~ {eval_end_date.date()})",
                  fontsize=14, fontweight="bold")
    ax1.set_ylabel("用气量")
    ax1.legend(loc="best", fontsize=10)
    ax1.grid(True, alpha=0.3)

    # ── 图2: v3 置信区间 ──
    ax2 = axes[1]
    ax2.plot(plot_v3["ds"].values, plot_v3["y"].values,
             label="实际值", color="#10b981", linewidth=1.5, marker="o", markersize=3)
    ax2.plot(plot_v3["ds"].values, plot_v3["yhat"].values,
             label="v3 预测值", color="#f59e0b", linewidth=1.5, marker="^", markersize=3)
    ax2.fill_between(plot_v3["ds"].values,
                     plot_v3["yhat_lower"].values, plot_v3["yhat_upper"].values,
                     color="#f59e0b", alpha=0.15, label="置信区间")
    ax2.set_title(f"v3 模型回测 + 置信区间 (RMSE={rmse_v3:.2f}, MAPE={mape_v3:.2f}%)",
                  fontsize=14, fontweight="bold")
    ax2.set_ylabel("用气量")
    ax2.legend(loc="best", fontsize=10)
    ax2.grid(True, alpha=0.3)

    # ── 图3: 历史 + 未来预测 ──
    ax3 = axes[2] if n_plots == 3 else axes[-1]
    plot_full = df.copy()
    plot_full["ds"] = plot_full["ds"] + pd.Timedelta(days=DS_SHIFT_DAYS)
    ax3.plot(plot_full["ds"].values, plot_full["y"].values,
             label="历史数据", color="#10b981", linewidth=1.2, alpha=0.8)
    if len(future_df) > 0:
        ax3.plot(future_df["ds"].values, future_df["yhat"].values,
                 label=f"未来预测 ({winner})", color="#f59e0b", linewidth=2)
        ax3.fill_between(future_df["ds"].values,
                         future_df["yhat_lower"].values, future_df["yhat_upper"].values,
                         color="#f59e0b", alpha=0.2, label="预测区间")
        ax3.axvline(plot_full["ds"].max(), color="#f59e0b", linestyle="--",
                    linewidth=1.5, label="预测起点")
    ax3.set_title(f"城市燃气 — 历史 + 未来 {horizon} 天预测 ({winner} 模型)",
                  fontsize=14, fontweight="bold")
    ax3.set_xlabel("日期")
    ax3.set_ylabel("用气量")
    ax3.legend(loc="best", fontsize=10)
    ax3.grid(True, alpha=0.3)

    plt.tight_layout()
    out_png = base / "城燃_v1_vs_v3_对比.png"
    plt.savefig(out_png, dpi=150, bbox_inches="tight")
    print(f"对比可视化: {out_png}")

    # ══════════════════════════════════════════════════════════════════
    # 对比汇总表
    # ══════════════════════════════════════════════════════════════════
    summary_rows = [
        {"模型": "v1 (Prophet+AR)", "RMSE": round(rmse_v1, 2), "MAPE(%)": round(mape_v1, 2),
         "耗时(s)": round(t_v1, 1)},
    ]
    if rmse_v2 is not None:
        summary_rows.append(
            {"模型": "v2 (Prophet+XGB+Ensemble)", "RMSE": round(rmse_v2, 2), "MAPE(%)": round(mape_v2, 2),
             "耗时(s)": round(t_v2, 1)})
    summary_rows.append(
        {"模型": "v3 (Prophet+LightGBM)", "RMSE": round(rmse_v3, 2), "MAPE(%)": round(mape_v3, 2),
         "耗时(s)": round(t_v3, 1)})

    summary = pd.DataFrame(summary_rows)
    out_summary = base / "城燃_模型对比汇总_v3.csv"
    summary.to_csv(out_summary, index=False, encoding="utf-8-sig")
    print(f"对比汇总: {out_summary}")

    print("\n" + "=" * 80)
    print("预测完成!")
    print("=" * 80)
    print(f"\n未来 {len(future_df)} 天预测 ({winner} 模型):")
    print(future_df[["ds", "yhat", "yhat_lower", "yhat_upper"]].head(15).to_string(index=False))
    if len(future_df) > 15:
        print(f"... 还有 {len(future_df) - 15} 天 (见 CSV)")


if __name__ == "__main__":
    main()
