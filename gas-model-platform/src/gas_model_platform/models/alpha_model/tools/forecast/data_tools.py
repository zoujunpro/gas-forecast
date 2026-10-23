"""
数据层工具 — 加载、清洗、分解、聚合、划分

所有函数返回 ToolResult 格式，可直接注册为 Agent 工具，
也可以作为普通函数调用。
"""

from __future__ import annotations

from typing import Optional, Union

import numpy as np
import pandas as pd

from ..base import ToolResult, ToolCategory


# ============================================
# 1. 数据加载
# ============================================

def load_data(
    filepath: str,
    date_col: Optional[str] = None,
    value_col: Optional[str] = None,
    sheet_name: Union[str, int] = 0,
) -> ToolResult:
    """
    加载时间序列数据（CSV / Excel）

    自动识别日期列和值列，自动解析日期、排序。
    """
    try:
        # 根据后缀选择读取方式
        if filepath.endswith(('.xls', '.xlsx')):
            df = pd.read_excel(filepath, sheet_name=sheet_name)
        else:
            df = pd.read_csv(filepath)

        # 自动识别日期列
        if date_col is None:
            date_col = _auto_detect_date_col(df)

        # 自动识别值列
        if value_col is None:
            value_col = _auto_detect_value_col(df, date_col)

        # 解析日期
        if date_col and date_col in df.columns:
            df[date_col] = pd.to_datetime(df[date_col])
            df = df.sort_values(date_col).reset_index(drop=True)

        date_range = None
        if date_col and date_col in df.columns:
            date_range = (str(df[date_col].min()), str(df[date_col].max()))

        result = {
            "df": df,
            "shape": list(df.shape),
            "columns": list(df.columns),
            "date_col": date_col,
            "value_col": value_col,
            "date_range": date_range,
            "sample": df.head(5).to_dict(orient="records"),
        }

        return ToolResult(
            data=result,
            metadata={
                "n_rows": len(df),
                "n_cols": len(df.columns),
                "date_col": date_col,
                "value_col": value_col,
            },
        )

    except Exception as e:
        return ToolResult(data=None, success=False, error=str(e))


def _auto_detect_date_col(df: pd.DataFrame) -> Optional[str]:
    """自动识别日期列"""
    date_keywords = ['date', '时间', '日期', 'ds', 'timestamp', 'time', 'day']
    for col in df.columns:
        col_lower = str(col).lower()
        if any(kw in col_lower for kw in date_keywords):
            # 验证一下能否解析
            try:
                pd.to_datetime(df[col].iloc[:5])
                return col
            except Exception:
                pass
    # 如果第一列能解析成日期，也认为是日期列
    if len(df.columns) > 0:
        first_col = df.columns[0]
        try:
            pd.to_datetime(df[first_col].iloc[:5])
            return first_col
        except Exception:
            pass
    return None


def _auto_detect_value_col(df: pd.DataFrame, date_col: Optional[str]) -> Optional[str]:
    """自动识别值列（数值列）"""
    numeric_cols = df.select_dtypes(include=[np.number]).columns.tolist()
    if not numeric_cols:
        return None
    # 排除日期列
    if date_col and date_col in numeric_cols:
        numeric_cols.remove(date_col)
    if not numeric_cols:
        return None
    # 优先选择名字里有 value / y / 销量 / 需求 等关键字的列
    value_keywords = ['value', 'y', '销量', '需求', 'sales', 'demand', '量', '数值']
    for col in numeric_cols:
        col_lower = str(col).lower()
        if any(kw in col_lower for kw in value_keywords):
            return col
    # 否则选第一个数值列
    return numeric_cols[0]


# ============================================
# 2. 数据清洗
# ============================================

def clean_data(
    df: pd.DataFrame,
    date_col: Optional[str] = None,
    value_col: Optional[str] = None,
    freq: Optional[str] = None,
    fill_method: str = "linear",
    outlier_method: str = "iqr",
    outlier_action: str = "replace",
    outlier_bounds: Optional[dict] = None,
) -> ToolResult:
    """
    清洗时间序列数据

    处理：缺失值、异常值、频率对齐、重复值
    """
    try:
        if date_col is None or value_col is None:
            return ToolResult(
                data=None,
                success=False,
                error="请指定 date_col 和 value_col",
            )

        df = df.copy()
        df[date_col] = pd.to_datetime(df[date_col])
        df = df.sort_values(date_col).reset_index(drop=True)

        # 设为索引
        df_indexed = df.set_index(date_col)
        y = pd.to_numeric(df_indexed[value_col], errors="coerce")

        original_count = len(y)
        missing_before = y.isna().sum()

        # 1. 频率对齐（可选）
        if freq:
            y = y.asfreq(freq)

        # 2. 异常值检测与处理。验证/测试集可复用训练集拟合的边界，避免未来信息泄漏。
        resolved_bounds = _resolve_outlier_bounds(y, outlier_method, outlier_bounds)
        outlier_mask = _outlier_mask(y, resolved_bounds)
        outliers_detected = int(outlier_mask.sum())
        y_clean = _handle_outliers(
            y,
            method=outlier_method,
            action=outlier_action,
            bounds=resolved_bounds,
        )

        # 3. 缺失值填充
        if fill_method == "linear":
            y_clean = y_clean.interpolate(method="linear", limit_direction="both")
        elif fill_method == "ffill":
            y_clean = y_clean.ffill()
        elif fill_method == "bfill":
            y_clean = y_clean.bfill()
        elif fill_method == "mean":
            y_clean = y_clean.fillna(y_clean.mean())
        elif fill_method == "median":
            y_clean = y_clean.fillna(y_clean.median())
        # else: 不填充

        missing_after = y_clean.isna().sum()

        # 还原 DataFrame
        result_df = y_clean.reset_index()
        result_df.columns = [date_col, value_col]

        stats = {
            "original_rows": original_count,
            "missing_before": int(missing_before),
            "missing_after": int(missing_after),
            "outliers_detected": outliers_detected,
            "outlier_bounds": resolved_bounds,
            "freq": str(y_clean.index.freq) if y_clean.index.freq else "none",
            "date_range": [str(y_clean.index.min()), str(y_clean.index.max())],
        }

        return ToolResult(
            data={"df": result_df, "stats": stats, "date_col": date_col, "value_col": value_col},
            metadata=stats,
        )

    except Exception as e:
        return ToolResult(data=None, success=False, error=str(e))


def _handle_outliers(
    y: pd.Series,
    method: str = "iqr",
    action: str = "replace",
    bounds: Optional[dict] = None,
) -> pd.Series:
    """处理异常值"""
    y = y.copy()
    resolved_bounds = _resolve_outlier_bounds(y, method, bounds)
    if resolved_bounds is None:
        return y

    lower = resolved_bounds["lower"]
    upper = resolved_bounds["upper"]
    mask = _outlier_mask(y, resolved_bounds)

    if action == "replace":
        # 替换为 NaN，后续用插值填充
        y[mask] = np.nan
    elif action == "clip":
        y[y < lower] = lower
        y[y > upper] = upper
    # "keep": 不处理

    return y


def _resolve_outlier_bounds(
    y: pd.Series,
    method: str,
    bounds: Optional[dict] = None,
) -> Optional[dict]:
    """返回异常值边界；显式传入时不再从当前数据重新拟合。"""
    if bounds is not None:
        try:
            lower = float(bounds["lower"])
            upper = float(bounds["upper"])
        except (KeyError, TypeError, ValueError):
            raise ValueError("outlier_bounds 必须包含数值 lower 和 upper")
        if not np.isfinite(lower) or not np.isfinite(upper) or lower > upper:
            raise ValueError("outlier_bounds 非法")
        return {"lower": lower, "upper": upper, "method": bounds.get("method", method)}

    valid = y.dropna()
    if len(valid) < 4:
        return None

    if method == "iqr":
        q1 = valid.quantile(0.25)
        q3 = valid.quantile(0.75)
        iqr = q3 - q1
        lower = q1 - 1.5 * iqr
        upper = q3 + 1.5 * iqr
    elif method == "zscore":
        mean = valid.mean()
        std = valid.std()
        lower = mean - 3 * std
        upper = mean + 3 * std
    else:
        return None

    return {"lower": float(lower), "upper": float(upper), "method": method}


def _outlier_mask(y: pd.Series, bounds: Optional[dict]) -> pd.Series:
    """根据已拟合边界返回异常值掩码。"""
    if bounds is None:
        return pd.Series(False, index=y.index)
    return (y < bounds["lower"]) | (y > bounds["upper"])


# ============================================
# 3. 时间序列分解
# ============================================

def decompose(
    df: pd.DataFrame,
    date_col: str,
    value_col: str,
    method: str = "stl",
    period: Optional[int] = None,
) -> ToolResult:
    """
    时间序列分解（趋势 + 季节 + 残差）

    支持：STL / 移动平均 / 简单差分
    """
    try:
        df = df.copy()
        df[date_col] = pd.to_datetime(df[date_col])
        df = df.sort_values(date_col).set_index(date_col)
        y = pd.to_numeric(df[value_col], errors="coerce")

        # 自动推断周期
        if period is None:
            period = _infer_period(y)

        trend = seasonal = resid = None

        if method == "stl":
            try:
                from statsmodels.tsa.seasonal import STL
                stl = STL(y.interpolate(), period=period, robust=True)
                result = stl.fit()
                trend = result.trend.values
                seasonal = result.seasonal.values
                resid = result.resid.values
            except ImportError:
                # statsmodels 不可用时退化为移动平均分解
                method = "moving_average"

        if method == "moving_average":
            trend, seasonal, resid = _ma_decompose(y.values, period)

        if method == "differencing":
            # 差分：趋势=累计和的趋势，季节=周期项
            trend = y.rolling(window=period, center=True, min_periods=1).mean().values
            detrended = y.values - trend
            seasonal = np.zeros_like(detrended)
            for i in range(period):
                seasonal[i::period] = np.nanmean(detrended[i::period])
            resid = detrended - seasonal

        if trend is None:
            return ToolResult(data=None, success=False, error=f"未知的分解方法: {method}")

        result = {
            "trend": trend,
            "seasonal": seasonal,
            "residual": resid,
            "period": period,
            "method": method,
            "dates": df.index.strftime("%Y-%m-%d").tolist(),
        }

        # 简单的强度评估
        total_var = np.nanvar(y.values)
        resid_var = np.nanvar(resid) if resid is not None else total_var
        strength = 1 - resid_var / total_var if total_var > 0 else 0

        return ToolResult(
            data=result,
            metadata={
                "period": period,
                "method": method,
                "strength": float(strength),
                "trend_mean": float(np.nanmean(trend)),
                "seasonal_amplitude": float(np.nanmax(seasonal) - np.nanmin(seasonal)),
            },
        )

    except Exception as e:
        return ToolResult(data=None, success=False, error=str(e))


def _infer_period(y: pd.Series) -> int:
    """自动推断周期"""
    # 简单启发：根据数据点数量和时间间隔猜
    if len(y) < 14:
        return 7
    elif len(y) < 60:
        return 7
    elif len(y) < 365:
        return 12
    else:
        return 365


def _ma_decompose(y: np.ndarray, period: int) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """简单移动平均分解"""
    n = len(y)
    y = np.asarray(y, dtype=float)
    # 趋势：中心移动平均
    window = period
    trend = np.full(n, np.nan)
    half = window // 2
    for i in range(half, n - half):
        trend[i] = np.nanmean(y[i - half: i + half + 1])

    # 去趋势
    detrended = y - trend

    # 季节：同相位平均
    seasonal = np.full(n, np.nan)
    for i in range(period):
        indices = [j for j in range(i, n, period) if not np.isnan(detrended[j])]
        if indices:
            avg = np.nanmean(detrended[indices])
            for j in range(i, n, period):
                seasonal[j] = avg

    # 残差
    resid = y - trend - seasonal

    # 两端填充
    trend = pd.Series(trend).interpolate(limit_direction="both").values
    seasonal = pd.Series(seasonal).interpolate(limit_direction="both").values
    resid = pd.Series(resid).interpolate(limit_direction="both").values

    return trend, seasonal, resid


# holdout / 测试集最短天数（与预测期数 horizon 独立）
MIN_HOLDOUT_DAYS = 10
MIN_TRAIN_AFTER_HOLDOUT = 20


def resolve_holdout_size(n_rows: int, requested: Optional[int] = None) -> int:
    """
    计算测试集天数：至少 10 天，且尽量给训练集留出空间。

    参数：
        n_rows: 可用序列长度
        requested: 调用方希望的测试天数（含 horizon 或 n_test）；小于 10 会被抬到 10
    """
    if n_rows < 2:
        return 1
    want = MIN_HOLDOUT_DAYS if requested is None else max(int(requested), MIN_HOLDOUT_DAYS)
    max_test = n_rows - MIN_TRAIN_AFTER_HOLDOUT
    if max_test >= MIN_HOLDOUT_DAYS:
        return min(want, max_test)
    return max(1, min(want, n_rows - 1))


# ============================================
# 4. 数据划分
# ============================================

def split_data(
    df: pd.DataFrame,
    date_col: str,
    value_col: str,
    train_ratio: float = 0.8,
    val_ratio: float = 0.0,
    n_test: Optional[int] = None,
    n_val: Optional[int] = None,
) -> ToolResult:
    """
    划分时间序列（按时间顺序，不打乱）

    支持按比例或按数量划分。测试集至少 10 天，不会因为 horizon=3 而被切成 3 天。
    """
    try:
        df = df.copy()
        df[date_col] = pd.to_datetime(df[date_col])
        df = df.sort_values(date_col).reset_index(drop=True)
        y = df[value_col].values
        n = len(y)

        if n_test is not None:
            test_size = n_test
        else:
            test_size = int(n * (1 - train_ratio - val_ratio))
        test_size = resolve_holdout_size(n, test_size)

        if n_val is not None:
            val_size = n_val
        else:
            val_size = int(n * val_ratio) if val_ratio > 0 else 0

        train_size = n - val_size - test_size

        if train_size <= 0 or test_size <= 0:
            return ToolResult(
                data=None,
                success=False,
                error=f"数据不足: 总{n}点, 训练{train_size}, 验证{val_size}, 测试{test_size}",
            )

        train = df.iloc[:train_size]
        val = df.iloc[train_size:train_size + val_size] if val_size > 0 else None
        test = df.iloc[train_size + val_size:]

        result = {
            "train": train,
            "val": val,
            "test": test,
            "train_size": len(train),
            "val_size": len(val) if val is not None else 0,
            "test_size": len(test),
            "date_col": date_col,
            "value_col": value_col,
        }

        return ToolResult(
            data=result,
            metadata={
                "train": len(train),
                "val": len(val) if val is not None else 0,
                "test": len(test),
                "total": n,
            },
        )

    except Exception as e:
        return ToolResult(data=None, success=False, error=str(e))


# ============================================
# 5. 数据聚合
# ============================================

def aggregate_data(
    df: pd.DataFrame,
    date_col: str,
    value_col: str,
    freq: str = "W",
    agg_func: str = "sum",
) -> ToolResult:
    """
    按频率聚合数据

    freq: D(日) / W(周) / M(月) / Q(季) / Y(年) / H(小时)
    agg_func: sum / mean / max / min / median
    """
    try:
        df = df.copy()
        df[date_col] = pd.to_datetime(df[date_col])
        df = df.set_index(date_col).sort_index()

        # 聚合
        if agg_func == "sum":
            agg = df[value_col].resample(freq).sum()
        elif agg_func == "mean":
            agg = df[value_col].resample(freq).mean()
        elif agg_func == "max":
            agg = df[value_col].resample(freq).max()
        elif agg_func == "min":
            agg = df[value_col].resample(freq).min()
        elif agg_func == "median":
            agg = df[value_col].resample(freq).median()
        else:
            return ToolResult(
                data=None,
                success=False,
                error=f"不支持的聚合函数: {agg_func}",
            )

        result_df = agg.reset_index()
        result_df.columns = [date_col, value_col]

        return ToolResult(
            data={
                "df": result_df,
                "freq": freq,
                "agg_func": agg_func,
                "original_rows": len(df),
                "aggregated_rows": len(result_df),
            },
            metadata={
                "freq": freq,
                "n_original": len(df),
                "n_aggregated": len(result_df),
            },
        )

    except Exception as e:
        return ToolResult(data=None, success=False, error=str(e))
