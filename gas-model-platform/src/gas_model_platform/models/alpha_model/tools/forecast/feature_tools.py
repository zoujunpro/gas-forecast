"""
特征工程工具 — 时间序列特征生成与选择

支持：
- 时间特征（年、月、日、星期、季度、周次、周末/月末/月初标记）
- 滞后特征（lag_1, lag_2, lag_3, lag_7, lag_14, lag_30）
- 滚动窗口特征（均值、标准差、最大、最小）
- 指数加权移动平均（EWM）
- 简单特征选择（相关系数 / 方差）
"""

from __future__ import annotations

from typing import Optional

import numpy as np
import pandas as pd

from ..base import ToolResult


# ============================================
# 1. 综合特征生成
# ============================================

def create_features(
    df: pd.DataFrame,
    date_col: str,
    value_col: str,
    feature_set: str = "basic",
) -> ToolResult:
    """
    生成时间序列特征（综合入口）

    根据 feature_set 生成不同组合的特征：
    - "basic":    时间特征 + 滞后特征
    - "full":     所有特征（时间 + 滞后 + 滚动 + 指数加权）
    - "lag_only": 仅滞后特征

    参数：
        df: 输入 DataFrame，需包含日期列和值列
        date_col: 日期列名
        value_col: 值列名
        feature_set: 特征集合类型，"basic" / "full" / "lag_only"

    返回：
        包含特征的 DataFrame、特征名列表、生成的特征数量等信息
    """
    try:
        if date_col not in df.columns:
            return ToolResult(
                data=None,
                success=False,
                error=f"日期列 '{date_col}' 不存在",
            )
        if value_col not in df.columns:
            return ToolResult(
                data=None,
                success=False,
                error=f"值列 '{value_col}' 不存在",
            )

        result_df = df.copy()
        result_df[date_col] = pd.to_datetime(result_df[date_col])
        result_df = result_df.sort_values(date_col).reset_index(drop=True)

        generated_features: list[str] = []

        # ---- 滞后特征（所有模式都包含） ----
        default_lags = [1, 2, 3, 7, 14, 30]
        n = len(result_df)
        # 自动过滤掉数据不足的滞后阶数
        available_lags = [lag for lag in default_lags if lag < n]

        for lag in available_lags:
            col_name = f"lag_{lag}"
            result_df[col_name] = result_df[value_col].shift(lag)
            generated_features.append(col_name)

        # ---- 时间特征 ----
        if feature_set in ("basic", "full"):
            dt = result_df[date_col].dt
            time_features = {
                "year": dt.year,
                "month": dt.month,
                "day": dt.day,
                "dayofweek": dt.dayofweek,
                "quarter": dt.quarter,
                "weekofyear": dt.isocalendar().week.astype(int),
                "is_weekend": (dt.dayofweek >= 5).astype(int),
                "is_month_start": dt.is_month_start.astype(int),
                "is_month_end": dt.is_month_end.astype(int),
            }
            for feat_name, feat_series in time_features.items():
                result_df[feat_name] = feat_series
                generated_features.append(feat_name)

        # ---- 滚动窗口特征 ----
        if feature_set == "full":
            rolling_configs = [
                (7, "mean"),
                (7, "std"),
                (14, "mean"),
                (30, "std"),
            ]
            for window, stat in rolling_configs:
                if window >= n:
                    continue
                col_name = f"rolling_{stat}_{window}"
                if stat == "mean":
                    result_df[col_name] = result_df[value_col].rolling(
                        window=window, min_periods=1
                    ).mean()
                elif stat == "std":
                    result_df[col_name] = result_df[value_col].rolling(
                        window=window, min_periods=1
                    ).std()
                generated_features.append(col_name)

            # ---- 指数加权特征 ----
            ewm_alphas = [7, 30]
            for span in ewm_alphas:
                if span >= n:
                    continue
                col_name = f"ewm_mean_{span}"
                result_df[col_name] = result_df[value_col].ewm(
                    span=span, adjust=False
                ).mean()
                generated_features.append(col_name)

        # 统计缺失值情况（特征列）
        feature_df = result_df[generated_features]
        nan_counts = feature_df.isna().sum().to_dict()
        total_nan = int(feature_df.isna().sum().sum())

        result = {
            "df": result_df,
            "feature_names": generated_features,
            "n_features": len(generated_features),
            "nan_counts": nan_counts,
            "total_nan": total_nan,
            "feature_set": feature_set,
            "date_col": date_col,
            "value_col": value_col,
        }

        summary = (
            f"生成 {len(generated_features)} 个特征, "
            f"总缺失值 {total_nan}, "
            f"类型: {feature_set}"
        )

        return ToolResult(
            data=result,
            metadata={
                "n_features": len(generated_features),
                "feature_set": feature_set,
                "total_nan": total_nan,
                "n_rows": len(result_df),
                "summary": summary,
            },
        )

    except Exception as e:
        return ToolResult(data=None, success=False, error=str(e))


# ============================================
# 2. 滞后特征
# ============================================

def create_lag_features(
    df: pd.DataFrame,
    value_col: str,
    lags: Optional[list[int]] = None,
) -> ToolResult:
    """
    生成滞后特征

    参数：
        df: 输入 DataFrame
        value_col: 值列名
        lags: 滞后阶数列表，默认 [1, 7, 14, 30]

    返回：
        包含滞后特征的 DataFrame、特征名列表等信息
    """
    try:
        if lags is None:
            lags = [1, 7, 14, 30]

        if value_col not in df.columns:
            return ToolResult(
                data=None,
                success=False,
                error=f"值列 '{value_col}' 不存在",
            )

        result_df = df.copy()
        n = len(result_df)
        generated_features: list[str] = []

        for lag in lags:
            if lag >= n:
                # 数据不足，跳过
                continue
            col_name = f"lag_{lag}"
            result_df[col_name] = result_df[value_col].shift(lag)
            generated_features.append(col_name)

        if not generated_features:
            return ToolResult(
                data=None,
                success=False,
                error=f"数据长度不足，无法生成任何滞后特征（n={n}, lags={lags}）",
            )

        feature_df = result_df[generated_features]
        nan_counts = feature_df.isna().sum().to_dict()
        total_nan = int(feature_df.isna().sum().sum())

        result = {
            "df": result_df,
            "feature_names": generated_features,
            "n_features": len(generated_features),
            "nan_counts": nan_counts,
            "total_nan": total_nan,
            "value_col": value_col,
            "lags": generated_features,
        }

        summary = (
            f"生成 {len(generated_features)} 个滞后特征, "
            f"总缺失值 {total_nan}"
        )

        return ToolResult(
            data=result,
            metadata={
                "n_features": len(generated_features),
                "total_nan": total_nan,
                "n_rows": len(result_df),
                "summary": summary,
            },
        )

    except Exception as e:
        return ToolResult(data=None, success=False, error=str(e))


# ============================================
# 3. 滚动窗口特征
# ============================================

def create_rolling_features(
    df: pd.DataFrame,
    value_col: str,
    windows: Optional[list[int]] = None,
    stats: Optional[list[str]] = None,
) -> ToolResult:
    """
    生成滚动窗口特征

    参数：
        df: 输入 DataFrame
        value_col: 值列名
        windows: 窗口大小列表，默认 [7, 14, 30]
        stats: 统计量列表，支持 mean / std / min / max，默认 ["mean", "std", "min", "max"]

    返回：
        包含滚动特征的 DataFrame、特征名列表等信息
    """
    try:
        if windows is None:
            windows = [7, 14, 30]
        if stats is None:
            stats = ["mean", "std", "min", "max"]

        if value_col not in df.columns:
            return ToolResult(
                data=None,
                success=False,
                error=f"值列 '{value_col}' 不存在",
            )

        valid_stats = {"mean", "std", "min", "max"}
        invalid_stats = set(stats) - valid_stats
        if invalid_stats:
            return ToolResult(
                data=None,
                success=False,
                error=f"不支持的统计量: {invalid_stats}，支持: {valid_stats}",
            )

        result_df = df.copy()
        n = len(result_df)
        generated_features: list[str] = []

        for window in windows:
            if window >= n:
                continue
            for stat in stats:
                col_name = f"rolling_{stat}_{window}"
                rolling = result_df[value_col].rolling(window=window, min_periods=1)
                if stat == "mean":
                    result_df[col_name] = rolling.mean()
                elif stat == "std":
                    result_df[col_name] = rolling.std()
                elif stat == "min":
                    result_df[col_name] = rolling.min()
                elif stat == "max":
                    result_df[col_name] = rolling.max()
                generated_features.append(col_name)

        if not generated_features:
            return ToolResult(
                data=None,
                success=False,
                error=f"数据长度不足，无法生成任何滚动特征（n={n}, windows={windows}）",
            )

        feature_df = result_df[generated_features]
        nan_counts = feature_df.isna().sum().to_dict()
        total_nan = int(feature_df.isna().sum().sum())

        result = {
            "df": result_df,
            "feature_names": generated_features,
            "n_features": len(generated_features),
            "nan_counts": nan_counts,
            "total_nan": total_nan,
            "value_col": value_col,
            "windows": windows,
            "stats": stats,
        }

        summary = (
            f"生成 {len(generated_features)} 个滚动特征, "
            f"窗口: {windows}, 统计量: {stats}"
        )

        return ToolResult(
            data=result,
            metadata={
                "n_features": len(generated_features),
                "total_nan": total_nan,
                "n_rows": len(result_df),
                "windows": windows,
                "stats": stats,
                "summary": summary,
            },
        )

    except Exception as e:
        return ToolResult(data=None, success=False, error=str(e))


# ============================================
# 4. 时间特征
# ============================================

def create_time_features(
    df: pd.DataFrame,
    date_col: str,
) -> ToolResult:
    """
    生成时间特征

    生成的特征包括：
    year, month, day, dayofweek, quarter, weekofyear,
    is_weekend, is_month_start, is_month_end

    参数：
        df: 输入 DataFrame
        date_col: 日期列名

    返回：
        包含时间特征的 DataFrame、特征名列表等信息
    """
    try:
        if date_col not in df.columns:
            return ToolResult(
                data=None,
                success=False,
                error=f"日期列 '{date_col}' 不存在",
            )

        result_df = df.copy()
        result_df[date_col] = pd.to_datetime(result_df[date_col])

        dt = result_df[date_col].dt
        time_features = {
            "year": dt.year,
            "month": dt.month,
            "day": dt.day,
            "dayofweek": dt.dayofweek,
            "quarter": dt.quarter,
            "weekofyear": dt.isocalendar().week.astype(int),
            "is_weekend": (dt.dayofweek >= 5).astype(int),
            "is_month_start": dt.is_month_start.astype(int),
            "is_month_end": dt.is_month_end.astype(int),
        }

        generated_features: list[str] = []
        for feat_name, feat_series in time_features.items():
            result_df[feat_name] = feat_series
            generated_features.append(feat_name)

        result = {
            "df": result_df,
            "feature_names": generated_features,
            "n_features": len(generated_features),
            "date_col": date_col,
        }

        summary = (
            f"生成 {len(generated_features)} 个时间特征: {generated_features}"
        )

        return ToolResult(
            data=result,
            metadata={
                "n_features": len(generated_features),
                "n_rows": len(result_df),
                "date_col": date_col,
                "summary": summary,
            },
        )

    except Exception as e:
        return ToolResult(data=None, success=False, error=str(e))


# ============================================
# 5. 特征选择
# ============================================

def select_features(
    df: pd.DataFrame,
    target_col: str,
    method: str = "correlation",
    top_k: int = 20,
) -> ToolResult:
    """
    简单特征选择

    method:
    - "correlation": 按与目标列的相关系数绝对值排序，取 Top K
    - "variance":   按方差排序，取 Top K（方差越大越被选择）

    参数：
        df: 输入 DataFrame（包含特征和目标列）
        target_col: 目标列名
        method: 选择方法，"correlation" 或 "variance"
        top_k: 保留的特征数量

    返回：
        筛选后的特征列表、对应的数据 DataFrame、选择依据等
    """
    try:
        if target_col not in df.columns:
            return ToolResult(
                data=None,
                success=False,
                error=f"目标列 '{target_col}' 不存在",
            )

        # 筛选数值型特征列（排除目标列和非数值列）
        numeric_df = df.select_dtypes(include=[np.number])
        feature_cols = [c for c in numeric_df.columns if c != target_col]

        if not feature_cols:
            return ToolResult(
                data=None,
                success=False,
                error="没有可用的数值型特征列",
            )

        # 处理缺失值（仅用于计算）
        clean_df = numeric_df[feature_cols + [target_col]].dropna()
        if len(clean_df) < 2:
            return ToolResult(
                data=None,
                success=False,
                error="有效数据点不足，无法进行特征选择",
            )

        if method == "correlation":
            # 计算与目标的相关系数绝对值
            correlations = clean_df[feature_cols].corrwith(clean_df[target_col]).abs()
            correlations = correlations.sort_values(ascending=False)

            # 处理 NaN 相关系数（如常数列）
            correlations = correlations.dropna()

            selected = correlations.head(top_k).index.tolist()
            scores = {col: float(correlations[col]) for col in selected}

            stats = {
                "method": "correlation",
                "top_k": top_k,
                "selected": selected,
                "scores": scores,
                "candidates": len(feature_cols),
            }

            summary = (
                f"按相关系数选择了 {len(selected)} 个特征 (top {top_k}), "
                f"最高相关: {selected[0]}={scores[selected[0]]:.4f}"
                if selected else "没有选到有效特征"
            )

        elif method == "variance":
            # 按方差排序
            variances = clean_df[feature_cols].var().sort_values(ascending=False)
            variances = variances.dropna()

            selected = variances.head(top_k).index.tolist()
            scores = {col: float(variances[col]) for col in selected}

            stats = {
                "method": "variance",
                "top_k": top_k,
                "selected": selected,
                "scores": scores,
                "candidates": len(feature_cols),
            }

            summary = (
                f"按方差选择了 {len(selected)} 个特征 (top {top_k}), "
                f"最大方差: {selected[0]}={scores[selected[0]]:.4f}"
                if selected else "没有选到有效特征"
            )

        else:
            return ToolResult(
                data=None,
                success=False,
                error=f"不支持的特征选择方法: {method}，支持: correlation, variance",
            )

        # 构造仅含选中特征 + 目标列的 DataFrame（保留原始索引和全部行）
        keep_cols = selected + [target_col]
        # 如果 date_col 等非数值列原本在 df 中，也一并保留
        non_numeric_cols = [c for c in df.columns if c not in numeric_df.columns]
        result_df = df[non_numeric_cols + keep_cols].copy()

        result = {
            "df": result_df,
            "selected_features": selected,
            "n_selected": len(selected),
            "scores": scores,
            "method": method,
            "target_col": target_col,
            "total_candidates": len(feature_cols),
        }

        return ToolResult(
            data=result,
            metadata={
                "n_selected": len(selected),
                "method": method,
                "top_k": top_k,
                "total_candidates": len(feature_cols),
                "summary": summary,
            },
        )

    except Exception as e:
        return ToolResult(data=None, success=False, error=str(e))
