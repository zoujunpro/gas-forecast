"""
统计预测模型工具

支持模型：
- naive: 朴素预测（最后一个值）
- seasonal_naive: 季节性朴素预测
- sma: 简单移动平均
- ewma: 指数加权移动平均
- holt_winters: Holt-Winters 指数平滑
- arima: ARIMA (statsmodels，可选依赖)
- prophet: Prophet (可选依赖)

所有模型统一接口：fit(train_df, date_col, value_col, ...) -> forecast(horizon, ...)
返回 ToolResult，含预测值、置信区间、模型信息。
"""

from __future__ import annotations

from typing import Optional, Union

import numpy as np
import pandas as pd

from ..base import ToolResult


# ============================================
# 工具函数
# ============================================

def _prepare_series(df, date_col, value_col):
    """准备时间序列"""
    df = df.copy()
    df[date_col] = pd.to_datetime(df[date_col])
    df = df.sort_values(date_col).set_index(date_col)
    y = pd.to_numeric(df[value_col], errors="coerce")
    y = y.interpolate().ffill().bfill()
    return y


def _make_forecast_df(y_train, forecast, date_col, value_col, conf_int=None):
    """构造预测结果 DataFrame"""
    last_date = y_train.index[-1]
    freq = pd.infer_freq(y_train.index) or "D"
    future_dates = pd.date_range(
        start=last_date + pd.tseries.frequencies.to_offset(freq),
        periods=len(forecast),
        freq=freq,
    )
    result = pd.DataFrame({
        date_col: future_dates,
        value_col: forecast,
    })
    if conf_int is not None:
        result[f"{value_col}_lower"] = conf_int[:, 0]
        result[f"{value_col}_upper"] = conf_int[:, 1]
    return result


# ============================================
# 1. Naive 朴素预测
# ============================================

def forecast_naive(
    train_df: pd.DataFrame,
    date_col: str,
    value_col: str,
    horizon: int = 7,
) -> ToolResult:
    """
    朴素预测 — 用最后一个观测值作为所有未来预测
    """
    try:
        y = _prepare_series(train_df, date_col, value_col)
        last_val = float(y.iloc[-1])
        forecast = np.full(horizon, last_val)
        # 简单置信区间：基于历史标准差
        std = float(y.std()) if len(y) > 1 else 0.0
        conf_int = np.column_stack([
            forecast - 1.96 * std,
            forecast + 1.96 * std,
        ])
        result_df = _make_forecast_df(y, forecast, date_col, value_col, conf_int)

        return ToolResult(
            data={
                "forecast_df": result_df,
                "forecast_values": forecast.tolist(),
                "conf_int": conf_int.tolist(),
                "model": "naive",
                "last_value": last_val,
            },
            metadata={"model": "naive", "horizon": horizon, "std": std},
        )
    except Exception as e:
        return ToolResult(data=None, success=False, error=str(e))


# ============================================
# 2. 季节性朴素预测
# ============================================

def forecast_seasonal_naive(
    train_df: pd.DataFrame,
    date_col: str,
    value_col: str,
    horizon: int = 7,
    period: Optional[int] = None,
) -> ToolResult:
    """
    季节性朴素预测 — 用上一周期的对应值作为预测
    """
    try:
        y = _prepare_series(train_df, date_col, value_col)
        if period is None:
            # 自动推断
            period = _auto_period(y)

        y_vals = y.values
        n = len(y_vals)
        forecast = np.zeros(horizon)
        for i in range(horizon):
            # 取上一周期同位置的值
            forecast[i] = y_vals[n - period + (i % period)]

        # 置信区间：基于周期内残差的标准差
        seasonal_errors = []
        for i in range(period, n):
            seasonal_errors.append(y_vals[i] - y_vals[i - period])
        std = float(np.std(seasonal_errors)) if seasonal_errors else 0.0
        conf_int = np.column_stack([
            forecast - 1.96 * std,
            forecast + 1.96 * std,
        ])
        result_df = _make_forecast_df(y, forecast, date_col, value_col, conf_int)

        return ToolResult(
            data={
                "forecast_df": result_df,
                "forecast_values": forecast.tolist(),
                "conf_int": conf_int.tolist(),
                "model": "seasonal_naive",
                "period": period,
            },
            metadata={"model": "seasonal_naive", "horizon": horizon, "period": period},
        )
    except Exception as e:
        return ToolResult(data=None, success=False, error=str(e))


# ============================================
# 3. 简单移动平均 (SMA)
# ============================================

def forecast_sma(
    train_df: pd.DataFrame,
    date_col: str,
    value_col: str,
    horizon: int = 7,
    window: int = 7,
) -> ToolResult:
    """
    简单移动平均预测 — 用最后 window 个值的均值作为所有预测
    """
    try:
        y = _prepare_series(train_df, date_col, value_col)
        if window > len(y):
            window = len(y)

        sma_val = float(y.iloc[-window:].mean())
        forecast = np.full(horizon, sma_val)

        # 置信区间
        resid = y.values - y.rolling(window=window, min_periods=1).mean().values
        std = float(np.nanstd(resid))
        conf_int = np.column_stack([
            forecast - 1.96 * std,
            forecast + 1.96 * std,
        ])
        result_df = _make_forecast_df(y, forecast, date_col, value_col, conf_int)

        return ToolResult(
            data={
                "forecast_df": result_df,
                "forecast_values": forecast.tolist(),
                "conf_int": conf_int.tolist(),
                "model": "sma",
                "window": window,
                "sma_value": sma_val,
            },
            metadata={"model": "sma", "horizon": horizon, "window": window},
        )
    except Exception as e:
        return ToolResult(data=None, success=False, error=str(e))


# ============================================
# 4. 指数加权移动平均 (EWMA)
# ============================================

def forecast_ewma(
    train_df: pd.DataFrame,
    date_col: str,
    value_col: str,
    horizon: int = 7,
    alpha: Optional[float] = None,
    span: Optional[int] = None,
) -> ToolResult:
    """
    指数加权移动平均预测 (EWMA / SES)
    """
    try:
        y = _prepare_series(train_df, date_col, value_col)

        # 计算 EWMA
        if alpha is not None:
            ewm = y.ewm(alpha=alpha, adjust=False)
        elif span is not None:
            ewm = y.ewm(span=span, adjust=False)
        else:
            ewm = y.ewm(alpha=0.3, adjust=False)

        smoothed = ewm.mean()
        last_val = float(smoothed.iloc[-1])
        forecast = np.full(horizon, last_val)

        # 残差标准差
        resid = y.values - smoothed.values
        std = float(np.std(resid)) if len(resid) > 1 else 0.0
        conf_int = np.column_stack([
            forecast - 1.96 * std,
            forecast + 1.96 * std,
        ])
        result_df = _make_forecast_df(y, forecast, date_col, value_col, conf_int)

        used_alpha = float(ewm.alpha) if hasattr(ewm, 'alpha') else alpha
        return ToolResult(
            data={
                "forecast_df": result_df,
                "forecast_values": forecast.tolist(),
                "conf_int": conf_int.tolist(),
                "model": "ewma",
                "alpha": used_alpha,
                "span": span,
            },
            metadata={"model": "ewma", "horizon": horizon, "alpha": used_alpha},
        )
    except Exception as e:
        return ToolResult(data=None, success=False, error=str(e))


# ============================================
# 5. Holt-Winters 指数平滑
# ============================================

def forecast_holt_winters(
    train_df: pd.DataFrame,
    date_col: str,
    value_col: str,
    horizon: int = 7,
    period: Optional[int] = None,
    trend: str = "add",
    seasonal: str = "add",
    damped_trend: bool = False,
) -> ToolResult:
    """
    Holt-Winters 三次指数平滑预测

    支持加性/乘性趋势和季节项。
    需要 statsmodels。
    """
    try:
        from statsmodels.tsa.holtwinters import ExponentialSmoothing
    except ImportError:
        # 退化为 EWMA
        result = forecast_ewma(train_df, date_col, value_col, horizon, span=7)
        if result.success:
            result.data["model"] = "holt_winters(ewma_fallback)"
            result.metadata["note"] = "statsmodels 不可用，使用 EWMA 替代"
        return result

    try:
        y = _prepare_series(train_df, date_col, value_col)
        if period is None:
            period = _auto_period(y)
        if period >= len(y) // 2:
            period = max(2, len(y) // 4)

        model = ExponentialSmoothing(
            y,
            trend=trend,
            seasonal=seasonal,
            seasonal_periods=period,
            damped_trend=damped_trend,
            initialization_method="estimated",
        )
        fitted = model.fit(optimized=True)
        forecast = fitted.forecast(horizon).values

        # 模拟置信区间（用残差标准差）
        resid = fitted.resid.values
        std = float(np.std(resid[~np.isnan(resid)]))
        conf_int = np.column_stack([
            forecast - 1.96 * std,
            forecast + 1.96 * std,
        ])
        result_df = _make_forecast_df(y, forecast, date_col, value_col, conf_int)

        return ToolResult(
            data={
                "forecast_df": result_df,
                "forecast_values": forecast.tolist(),
                "conf_int": conf_int.tolist(),
                "model": "holt_winters",
                "period": period,
                "trend": trend,
                "seasonal": seasonal,
                "aic": float(fitted.aic) if hasattr(fitted, 'aic') else None,
                "params": {} if not hasattr(fitted, 'params') else {
                    k: float(v) for k, v in fitted.params.items()
                    if np.ndim(v) == 0 and v is not None and not (isinstance(v, float) and np.isnan(v))
                },
            },
            metadata={
                "model": "holt_winters",
                "horizon": horizon,
                "period": period,
                "aic": float(fitted.aic) if hasattr(fitted, 'aic') else None,
            },
        )
    except Exception as e:
        return ToolResult(data=None, success=False, error=str(e))


# ============================================
# 6. ARIMA
# ============================================

def forecast_arima(
    train_df: pd.DataFrame,
    date_col: str,
    value_col: str,
    horizon: int = 7,
    order: Optional[tuple] = None,
    seasonal_order: Optional[tuple] = None,
    period: Optional[int] = None,
    auto: bool = True,
) -> ToolResult:
    """
    ARIMA / SARIMA 预测

    需要 statsmodels。auto=True 时尝试自动选择阶数。
    """
    try:
        from statsmodels.tsa.arima.model import ARIMA
    except ImportError:
        result = forecast_holt_winters(train_df, date_col, value_col, horizon)
        if result.success:
            result.data["model"] = "arima(holt_winters_fallback)"
            result.metadata["note"] = "statsmodels ARIMA 不可用，使用 Holt-Winters 替代"
        return result

    try:
        y = _prepare_series(train_df, date_col, value_col)
        if period is None:
            period = _auto_period(y)

        # 自动选择阶数：仅在未显式给出 order 时启用
        if order is None:
            if auto:
                order = _auto_arima_order(y, period)
            else:
                order = (1, 1, 1)

        seasonal = seasonal_order is not None
        if seasonal and seasonal_order is not None:
            try:
                from statsmodels.tsa.statespace.sarimax import SARIMAX
                model = SARIMAX(
                    y, order=order, seasonal_order=seasonal_order,
                    initialization="estimated",
                )
                fitted = model.fit(disp=False)
            except Exception:
                # 退回普通 ARIMA
                model = ARIMA(y, order=order)
                fitted = model.fit()
                seasonal = False
        else:
            model = ARIMA(y, order=order)
            fitted = model.fit()

        forecast_result = fitted.get_forecast(horizon)
        forecast = forecast_result.predicted_mean.values
        conf = forecast_result.conf_int(alpha=0.05)
        conf_int = conf.values

        result_df = _make_forecast_df(y, forecast, date_col, value_col, conf_int)

        return ToolResult(
            data={
                "forecast_df": result_df,
                "forecast_values": forecast.tolist(),
                "conf_int": conf_int.tolist(),
                "model": "sarima" if seasonal else "arima",
                "order": list(order),
                "seasonal_order": list(seasonal_order) if seasonal_order else None,
                "period": period,
                "aic": float(fitted.aic) if hasattr(fitted, 'aic') else None,
                "bic": float(fitted.bic) if hasattr(fitted, 'bic') else None,
            },
            metadata={
                "model": "sarima" if seasonal else "arima",
                "horizon": horizon,
                "order": order,
                "aic": float(fitted.aic) if hasattr(fitted, 'aic') else None,
            },
        )
    except Exception as e:
        return ToolResult(data=None, success=False, error=str(e))


def _auto_arima_order(y, period):
    """简单的 ARIMA 阶数自动选择"""
    n = len(y)
    # 差分阶数：用简单 ADF 思路，不足就用 1
    d = 1
    # 简单尝试几组 (p, d, q) 组合，选 AIC 最小的
    best_aic = float("inf")
    best_order = (1, 1, 1)
    try:
        from statsmodels.tsa.arima.model import ARIMA
        for p in range(0, min(4, n // 10 + 1)):
            for q in range(0, min(4, n // 10 + 1)):
                if p == 0 and q == 0:
                    continue
                try:
                    m = ARIMA(y, order=(p, d, q))
                    res = m.fit()
                    if res.aic < best_aic:
                        best_aic = res.aic
                        best_order = (p, d, q)
                except Exception:
                    continue
    except Exception:
        pass
    return best_order


# ============================================
# 7. Prophet (可选)
# ============================================

def forecast_prophet(
    train_df: pd.DataFrame,
    date_col: str,
    value_col: str,
    horizon: int = 7,
    yearly_seasonality: Union[bool, str] = "auto",
    weekly_seasonality: Union[bool, str] = "auto",
    daily_seasonality: Union[bool, str] = False,
    seasonality_mode: str = "additive",
    changepoint_prior_scale: float = 0.05,
) -> ToolResult:
    """
    Prophet 预测（Facebook/Meta Prophet）

    需要 prophet 包。不可用时自动退化为 Holt-Winters。
    """
    try:
        from prophet import Prophet
    except ImportError:
        result = forecast_holt_winters(train_df, date_col, value_col, horizon)
        if result.success:
            result.data["model"] = "prophet(holt_winters_fallback)"
            result.metadata["note"] = "prophet 未安装，使用 Holt-Winters 替代"
        return result

    try:
        y = _prepare_series(train_df, date_col, value_col)
        df_prophet = y.reset_index()
        df_prophet.columns = ["ds", "y"]

        model = Prophet(
            yearly_seasonality=yearly_seasonality,
            weekly_seasonality=weekly_seasonality,
            daily_seasonality=daily_seasonality,
            seasonality_mode=seasonality_mode,
            changepoint_prior_scale=changepoint_prior_scale,
        )
        model.fit(df_prophet)

        future = model.make_future_dataframe(periods=horizon)
        forecast_all = model.predict(future)

        # 取最后 horizon 个
        forecast_series = forecast_all.tail(horizon)
        forecast_vals = forecast_series["yhat"].values
        conf_int = forecast_series[["yhat_lower", "yhat_upper"]].values

        result_df = pd.DataFrame({
            date_col: forecast_series["ds"].values,
            value_col: forecast_vals,
            f"{value_col}_lower": conf_int[:, 0],
            f"{value_col}_upper": conf_int[:, 1],
        })

        return ToolResult(
            data={
                "forecast_df": result_df,
                "forecast_values": forecast_vals.tolist(),
                "conf_int": conf_int.tolist(),
                "model": "prophet",
                "seasonality_mode": seasonality_mode,
            },
            metadata={"model": "prophet", "horizon": horizon},
        )
    except Exception as e:
        return ToolResult(data=None, success=False, error=str(e))


# ============================================
# 辅助函数
# ============================================

def _auto_period(y: pd.Series) -> int:
    """自动推断季节性周期"""
    n = len(y)
    # 看看数据频率
    freq = pd.infer_freq(y.index)
    if freq:
        if freq.startswith("H"):
            return 24
        elif freq.startswith("D"):
            return 7
        elif freq.startswith("W"):
            return 52
        elif freq.startswith("M") or freq.startswith("ME"):
            return 12
        elif freq.startswith("Q"):
            return 4
    # 根据长度启发
    if n < 14:
        return max(2, n // 2)
    elif n < 60:
        return 7
    elif n < 365:
        return 12
    return 365
