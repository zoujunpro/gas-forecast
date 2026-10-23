"""
可视化工具 — 预测结果图表

matplotlib 不可用时所有函数返回 success=False。
中文字体尝试自动适配。
"""

from __future__ import annotations

import os
from typing import Optional

import numpy as np
import pandas as pd

from ..base import ToolResult


# ============================================
# 字体设置
# ============================================

def _setup_chinese_font() -> bool:
    """
    尝试设置中文字体。成功返回 True，失败返回 False（使用默认字体）。
    """
    try:
        import matplotlib
        import platform

        candidates = [
            "SimHei",
            "Arial Unicode MS",
            "Microsoft YaHei",
            "PingFang SC",
            "Noto Sans CJK SC",
            "WenQuanYi Micro Hei",
        ]
        if platform.system() == "Windows":
            candidates.insert(0, "Microsoft YaHei")
        elif platform.system() == "Darwin":
            candidates.insert(0, "PingFang SC")

        from matplotlib import font_manager
        available = {f.name for f in font_manager.fontManager.ttflist}
        for font in candidates:
            if font in available:
                matplotlib.rcParams["font.sans-serif"] = [font] + matplotlib.rcParams.get("font.sans-serif", [])
                matplotlib.rcParams["axes.unicode_minus"] = False
                return True
        return False
    except Exception:
        return False


def _matplotlib_available() -> bool:
    try:
        import matplotlib  # noqa: F401
        return True
    except ImportError:
        return False


# ============================================
# 工具函数
# ============================================

def plot_forecast(
    train_df: pd.DataFrame,
    forecast_df: pd.DataFrame,
    date_col: str,
    value_col: str,
    title: str = "预测结果",
    save_path: Optional[str] = None,
    figsize: tuple = (12, 5),
) -> ToolResult:
    """
    绘制历史数据 + 预测曲线

    如果 forecast_df 有上下界列，填充置信区间。

    Args:
        train_df: 历史数据
        forecast_df: 预测结果
        date_col: 日期列名
        value_col: 数值列名
        title: 图表标题
        save_path: 保存路径，None 时只显示不保存
        figsize: 图片大小

    Returns:
        ToolResult.data = {
            "saved": bool,
            "path": str or None,
            "title": str,
        }
    """
    if not _matplotlib_available():
        return ToolResult(
            data={"saved": False, "path": None, "title": title},
            success=False,
            error="matplotlib not available",
        )
    try:
        import matplotlib.pyplot as plt

        _setup_chinese_font()

        fig, ax = plt.subplots(figsize=figsize)

        # 历史数据
        ax.plot(
            train_df[date_col],
            train_df[value_col],
            label="历史数据",
            color="#1f77b4",
            linewidth=1.2,
        )

        # 预测数据
        ax.plot(
            forecast_df[date_col],
            forecast_df[value_col],
            label="预测",
            color="#ff7f0e",
            linewidth=1.5,
            linestyle="--",
        )

        # 置信区间
        lower_col = None
        upper_col = None
        for col in forecast_df.columns:
            cl = col.lower()
            if "lower" in cl or "low" in cl or "min" in cl or "下" in col:
                lower_col = col
            if "upper" in cl or "high" in cl or "max" in cl or "上" in col:
                upper_col = col

        if lower_col and upper_col:
            ax.fill_between(
                forecast_df[date_col],
                forecast_df[lower_col],
                forecast_df[upper_col],
                color="#ff7f0e",
                alpha=0.2,
                label="置信区间",
            )

        ax.set_title(title)
        ax.set_xlabel(date_col)
        ax.set_ylabel(value_col)
        ax.legend()
        ax.grid(True, alpha=0.3)
        fig.autofmt_xdate()
        plt.tight_layout()

        saved = False
        if save_path:
            os.makedirs(os.path.dirname(save_path) or ".", exist_ok=True)
            fig.savefig(save_path, dpi=150, bbox_inches="tight")
            saved = True

        plt.close(fig)

        return ToolResult(
            data={
                "saved": saved,
                "path": save_path if saved else None,
                "title": title,
            },
        )
    except Exception as e:
        return ToolResult(
            data={"saved": False, "path": None, "title": title},
            success=False,
            error=str(e),
        )


def plot_decomposition(
    dates,
    trend,
    seasonal,
    residual,
    title: str = "时间序列分解",
    save_path: Optional[str] = None,
    figsize: tuple = (12, 8),
) -> ToolResult:
    """
    时间序列分解四张子图：原始、趋势、季节、残差

    Args:
        dates: 日期序列
        trend: 趋势分量
        seasonal: 季节分量
        residual: 残差分量
        title: 图表标题
        save_path: 保存路径
        figsize: 图片大小

    Returns:
        ToolResult.data = {
            "saved": bool,
            "path": str or None,
            "title": str,
        }
    """
    if not _matplotlib_available():
        return ToolResult(
            data={"saved": False, "path": None, "title": title},
            success=False,
            error="matplotlib not available",
        )
    try:
        import matplotlib.pyplot as plt

        _setup_chinese_font()

        trend_arr = np.asarray(trend, dtype=float)
        seasonal_arr = np.asarray(seasonal, dtype=float)
        residual_arr = np.asarray(residual, dtype=float)
        observed = trend_arr + seasonal_arr + residual_arr

        fig, axes = plt.subplots(4, 1, figsize=figsize, sharex=True)

        axes[0].plot(dates, observed, color="#1f77b4", linewidth=1.2)
        axes[0].set_title(title)
        axes[0].set_ylabel("原始")
        axes[0].grid(True, alpha=0.3)

        axes[1].plot(dates, trend_arr, color="#2ca02c", linewidth=1.2)
        axes[1].set_ylabel("趋势")
        axes[1].grid(True, alpha=0.3)

        axes[2].plot(dates, seasonal_arr, color="#ff7f0e", linewidth=1.2)
        axes[2].set_ylabel("季节")
        axes[2].grid(True, alpha=0.3)

        axes[3].plot(dates, residual_arr, color="#d62728", linewidth=1.0)
        axes[3].set_ylabel("残差")
        axes[3].set_xlabel("日期")
        axes[3].grid(True, alpha=0.3)
        axes[3].axhline(y=0, color="black", linestyle="-", linewidth=0.5, alpha=0.5)

        fig.autofmt_xdate()
        plt.tight_layout()

        saved = False
        if save_path:
            os.makedirs(os.path.dirname(save_path) or ".", exist_ok=True)
            fig.savefig(save_path, dpi=150, bbox_inches="tight")
            saved = True

        plt.close(fig)

        return ToolResult(
            data={
                "saved": saved,
                "path": save_path if saved else None,
                "title": title,
            },
        )
    except Exception as e:
        return ToolResult(
            data={"saved": False, "path": None, "title": title},
            success=False,
            error=str(e),
        )


def plot_comparison(
    y_true,
    predictions_dict: dict,
    title: str = "多模型对比",
    save_path: Optional[str] = None,
    figsize: tuple = (12, 5),
) -> ToolResult:
    """
    多模型预测对比折线图

    Args:
        y_true: 真值数组
        predictions_dict: {模型名: y_pred_array}
        title: 图表标题
        save_path: 保存路径
        figsize: 图片大小

    Returns:
        ToolResult.data = {
            "saved": bool,
            "path": str or None,
            "title": str,
        }
    """
    if not _matplotlib_available():
        return ToolResult(
            data={"saved": False, "path": None, "title": title},
            success=False,
            error="matplotlib not available",
        )
    try:
        import matplotlib.pyplot as plt

        _setup_chinese_font()

        y_true_arr = np.asarray(y_true, dtype=float)
        x = np.arange(len(y_true_arr))

        fig, ax = plt.subplots(figsize=figsize)
        ax.plot(x, y_true_arr, label="真值", color="black", linewidth=1.5, linestyle="-")

        colors = ["#1f77b4", "#ff7f0e", "#2ca02c", "#d62728", "#9467bd", "#8c564b", "#e377c2"]
        for i, (name, pred) in enumerate(predictions_dict.items()):
            pred_arr = np.asarray(pred, dtype=float).ravel()
            color = colors[i % len(colors)]
            ax.plot(x, pred_arr, label=name, color=color, linewidth=1.0, linestyle="--", alpha=0.85)

        ax.set_title(title)
        ax.set_xlabel("时间步")
        ax.set_ylabel("数值")
        ax.legend(loc="best")
        ax.grid(True, alpha=0.3)
        plt.tight_layout()

        saved = False
        if save_path:
            os.makedirs(os.path.dirname(save_path) or ".", exist_ok=True)
            fig.savefig(save_path, dpi=150, bbox_inches="tight")
            saved = True

        plt.close(fig)

        return ToolResult(
            data={
                "saved": saved,
                "path": save_path if saved else None,
                "title": title,
            },
        )
    except Exception as e:
        return ToolResult(
            data={"saved": False, "path": None, "title": title},
            success=False,
            error=str(e),
        )


def plot_metrics_bar(
    metrics_dict: dict,
    title: str = "评估指标对比",
    save_path: Optional[str] = None,
    figsize: tuple = (10, 5),
) -> ToolResult:
    """
    分组柱状图对比各模型的 RMSE/MAPE/MAE

    Args:
        metrics_dict: {模型名: {metric1: val1, metric2: val2, ...}}
        title: 图表标题
        save_path: 保存路径
        figsize: 图片大小

    Returns:
        ToolResult.data = {
            "saved": bool,
            "path": str or None,
            "title": str,
        }
    """
    if not _matplotlib_available():
        return ToolResult(
            data={"saved": False, "path": None, "title": title},
            success=False,
            error="matplotlib not available",
        )
    try:
        import matplotlib.pyplot as plt

        _setup_chinese_font()

        models = list(metrics_dict.keys())
        if not models:
            return ToolResult(
                data={"saved": False, "path": None, "title": title},
                success=False,
                error="metrics_dict is empty",
            )

        # 收集所有指标名
        metric_names = []
        seen = set()
        for m in models:
            for k in metrics_dict[m].keys():
                if k not in seen:
                    seen.add(k)
                    metric_names.append(k)

        n_models = len(models)
        n_metrics = len(metric_names)

        x = np.arange(n_models)
        width = 0.8 / max(n_metrics, 1)

        fig, ax = plt.subplots(figsize=figsize)
        colors = ["#1f77b4", "#ff7f0e", "#2ca02c", "#d62728", "#9467bd"]

        for i, metric in enumerate(metric_names):
            values = [metrics_dict[m].get(metric, 0) for m in models]
            offset = (i - n_metrics / 2 + 0.5) * width
            ax.bar(
                x + offset,
                values,
                width=width,
                label=metric.upper(),
                color=colors[i % len(colors)],
                alpha=0.85,
            )

        ax.set_title(title)
        ax.set_xticks(x)
        ax.set_xticklabels(models, rotation=15, ha="right")
        ax.set_ylabel("指标值")
        ax.legend(loc="best")
        ax.grid(True, alpha=0.3, axis="y")
        plt.tight_layout()

        saved = False
        if save_path:
            os.makedirs(os.path.dirname(save_path) or ".", exist_ok=True)
            fig.savefig(save_path, dpi=150, bbox_inches="tight")
            saved = True

        plt.close(fig)

        return ToolResult(
            data={
                "saved": saved,
                "path": save_path if saved else None,
                "title": title,
            },
        )
    except Exception as e:
        return ToolResult(
            data={"saved": False, "path": None, "title": title},
            success=False,
            error=str(e),
        )


def plot_backtest_segments(
    segments_data: dict,
    title: str = "分段回测表现",
    save_path: Optional[str] = None,
    figsize: tuple = (10, 5),
) -> ToolResult:
    """
    分段回测 MAPE/RMSE 趋势柱状图

    Args:
        segments_data: {分段名: {"mape": val, "rmse": val, ...}}
        title: 图表标题
        save_path: 保存路径
        figsize: 图片大小

    Returns:
        ToolResult.data = {
            "saved": bool,
            "path": str or None,
            "title": str,
        }
    """
    if not _matplotlib_available():
        return ToolResult(
            data={"saved": False, "path": None, "title": title},
            success=False,
            error="matplotlib not available",
        )
    try:
        import matplotlib.pyplot as plt

        _setup_chinese_font()

        segments = list(segments_data.keys())
        if not segments:
            return ToolResult(
                data={"saved": False, "path": None, "title": title},
                success=False,
                error="segments_data is empty",
            )

        # 收集指标名
        metric_names = []
        seen = set()
        for s in segments:
            for k in segments_data[s].keys():
                if k not in seen:
                    seen.add(k)
                    metric_names.append(k)

        n_segments = len(segments)
        n_metrics = len(metric_names)

        if n_metrics == 0:
            return ToolResult(
                data={"saved": False, "path": None, "title": title},
                success=False,
                error="no metrics found in segments_data",
            )

        x = np.arange(n_segments)
        width = 0.8 / max(n_metrics, 1)

        fig, ax = plt.subplots(figsize=figsize)
        colors = ["#1f77b4", "#ff7f0e", "#2ca02c", "#d62728", "#9467bd"]

        for i, metric in enumerate(metric_names):
            values = [segments_data[s].get(metric, 0) for s in segments]
            offset = (i - n_metrics / 2 + 0.5) * width
            ax.bar(
                x + offset,
                values,
                width=width,
                label=metric.upper(),
                color=colors[i % len(colors)],
                alpha=0.85,
            )

        ax.set_title(title)
        ax.set_xticks(x)
        ax.set_xticklabels(segments, rotation=20, ha="right")
        ax.set_ylabel("指标值")
        ax.legend(loc="best")
        ax.grid(True, alpha=0.3, axis="y")
        plt.tight_layout()

        saved = False
        if save_path:
            os.makedirs(os.path.dirname(save_path) or ".", exist_ok=True)
            fig.savefig(save_path, dpi=150, bbox_inches="tight")
            saved = True

        plt.close(fig)

        return ToolResult(
            data={
                "saved": saved,
                "path": save_path if saved else None,
                "title": title,
            },
        )
    except Exception as e:
        return ToolResult(
            data={"saved": False, "path": None, "title": title},
            success=False,
            error=str(e),
        )
