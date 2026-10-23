"""
报告生成工具 — Markdown 格式预测报告

根据预测结果、评估指标、图表自动生成结构化的分析报告。
"""

from __future__ import annotations

import os
import time
from datetime import datetime
from typing import Optional

from ..base import ToolResult


# ============================================
# 报告生成
# ============================================

def generate_forecast_report(
    dataset_info: dict,
    model_results: dict,
    best_model: str,
    metrics: Optional[dict] = None,
    chart_paths: Optional[dict] = None,
    insights: Optional[list[str]] = None,
    title: str = "需求预测分析报告",
    save_path: Optional[str] = None,
) -> ToolResult:
    """
    生成完整的需求预测分析报告（Markdown 格式）

    参数：
        dataset_info: 数据集信息 {name, n_rows, date_range, freq, value_col}
        model_results: {模型名: {metrics, horizon, ...}}
        best_model: 最佳模型名
        metrics: 整体评估指标
        chart_paths: {图表类型: 图片路径}
        insights: 洞察列表
        title: 报告标题
        save_path: 保存路径（.md 文件）

    返回：
        report_content: 报告文本
        saved: 是否保存
        path: 保存路径
    """
    try:
        lines = []

        # 标题
        lines.append(f"# {title}")
        lines.append("")
        lines.append(f"**生成时间**: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        lines.append("")

        # 目录
        lines.append("## 目录")
        lines.append("")
        lines.append("1. [数据概览](#数据概览)")
        lines.append("2. [模型对比](#模型对比)")
        lines.append("3. [采用模型分析](#采用模型分析)")
        lines.append("4. [可视化](#可视化)")
        lines.append("5. [关键洞察](#关键洞察)")
        lines.append("6. [建议](#建议)")
        lines.append("")

        # 1. 数据概览
        lines.append("## 数据概览")
        lines.append("")
        if dataset_info:
            lines.append("| 指标 | 值 |")
            lines.append("|------|-----|")
            for k, v in dataset_info.items():
                lines.append(f"| {k} | {v} |")
            lines.append("")

        # 2. 模型对比
        lines.append("## 模型对比")
        lines.append("")
        lines.append("以下模型指标均来自验证集。")
        lines.append("")
        if model_results:
            # 收集所有指标名
            all_metric_names = set()
            for result in model_results.values():
                if isinstance(result, dict):
                    all_metric_names.update(result.keys())
            # 优先展示的指标
            preferred_metrics = ["rmse", "mape", "mae", "mase", "smape", "direction_acc"]
            display_metrics = [m for m in preferred_metrics if m in all_metric_names]
            display_metrics += [m for m in sorted(all_metric_names) if m not in display_metrics]

            lines.append("| 模型 | " + " | ".join(display_metrics) + " |")
            lines.append("|" + "------|" * (len(display_metrics) + 1))
            for model_name, result in model_results.items():
                row = [model_name]
                for m in display_metrics:
                    val = result.get(m, "-") if isinstance(result, dict) else "-"
                    if isinstance(val, float):
                        row.append(f"{val:.4f}")
                    else:
                        row.append(str(val))
                lines.append("| " + " | ".join(row) + " |")
            lines.append("")
            lines.append(f"**最终采用模型**: {best_model}")
            lines.append("")

        # 3. 最终采用模型分析（最终预测失败降级时，它不一定是验证集第一名）
        lines.append("## 采用模型分析")
        lines.append("")
        if best_model and best_model in model_results:
            best = model_results[best_model]
            lines.append(f"模型 **{best_model}** 为最终采用模型，其验证集指标如下。")
            lines.append("")
            lines.append("详细指标：")
            lines.append("")
            if isinstance(best, dict):
                for k, v in best.items():
                    if isinstance(v, float):
                        lines.append(f"- **{k}**: {v:.4f}")
                    else:
                        lines.append(f"- **{k}**: {v}")
            lines.append("")

        if metrics:
            lines.append("### 独立测试集指标")
            lines.append("")
            for k, v in metrics.items():
                if isinstance(v, float):
                    lines.append(f"- **{k}**: {v:.4f}")
                else:
                    lines.append(f"- **{k}**: {v}")
            lines.append("")

        # 4. 可视化
        lines.append("## 可视化")
        lines.append("")
        if chart_paths:
            for chart_name, chart_path in chart_paths.items():
                abs_path = os.path.abspath(chart_path) if chart_path else ""
                lines.append(f"### {chart_name}")
                lines.append("")
                if abs_path and os.path.exists(abs_path):
                    lines.append(f"![{chart_name}]({abs_path})")
                else:
                    lines.append(f"*图片路径: {chart_path}*")
                lines.append("")

        # 5. 关键洞察
        lines.append("## 关键洞察")
        lines.append("")
        if insights:
            for i, insight in enumerate(insights, 1):
                lines.append(f"{i}. {insight}")
            lines.append("")
        else:
            lines.append("*暂无自动生成的洞察*")
            lines.append("")

        # 6. 建议
        lines.append("## 建议")
        lines.append("")
        recommendations = _generate_recommendations(
            dataset_info, model_results, best_model, metrics
        )
        for i, rec in enumerate(recommendations, 1):
            lines.append(f"{i}. {rec}")
        lines.append("")

        # 页脚
        lines.append("---")
        lines.append("")
        lines.append(f"*本报告由 QuantaAlpha 需求预测 Agent 框架自动生成*")
        lines.append("")

        report_content = "\n".join(lines)

        # 保存
        saved = False
        if save_path:
            os.makedirs(os.path.dirname(os.path.abspath(save_path)), exist_ok=True)
            with open(save_path, "w", encoding="utf-8") as f:
                f.write(report_content)
            saved = True

        return ToolResult(
            data={
                "report_content": report_content,
                "saved": saved,
                "path": save_path,
                "title": title,
                "n_sections": 6,
            },
            metadata={
                "saved": saved,
                "n_models": len(model_results) if model_results else 0,
                "best_model": best_model,
            },
        )

    except Exception as e:
        return ToolResult(data=None, success=False, error=str(e))


# ============================================
# 简版报告
# ============================================

def generate_summary_report(
    metric_summary: dict,
    dataset_info: Optional[dict] = None,
    title: str = "预测结果摘要",
) -> ToolResult:
    """
    生成简短的一行/几行预测摘要（适合 Agent 上下文中携带）
    """
    try:
        lines = [f"### {title}", ""]

        if dataset_info:
            data_desc = ", ".join(
                f"{k}={v}" for k, v in list(dataset_info.items())[:5]
            )
            lines.append(f"**数据**: {data_desc}")
            lines.append("")

        if metric_summary:
            lines.append("**指标**:")
            lines.append("")
            for k, v in metric_summary.items():
                if isinstance(v, float):
                    lines.append(f"- {k}: {v:.4f}")
                else:
                    lines.append(f"- {k}: {v}")
            lines.append("")

        content = "\n".join(lines)
        return ToolResult(
            data={"summary": content, "metrics": metric_summary},
            metadata={"n_metrics": len(metric_summary)},
        )
    except Exception as e:
        return ToolResult(data=None, success=False, error=str(e))


# ============================================
# 内部：生成建议
# ============================================

def _generate_recommendations(dataset_info, model_results, best_model, metrics):
    """基于分析结果生成建议"""
    recs = []

    if best_model and model_results and best_model in model_results:
        best_res = model_results[best_model]
        mape = best_res.get("mape") if isinstance(best_res, dict) else None

        if mape is not None:
            if mape < 5:
                recs.append(
                    f"{best_model} 预测精度很高（MAPE={mape:.2f}%），可直接用于决策支持。"
                )
            elif mape < 10:
                recs.append(
                    f"{best_model} 预测精度良好（MAPE={mape:.2f}%），建议结合人工判断使用。"
                )
            elif mape < 20:
                recs.append(
                    f"{best_model} 预测精度中等（MAPE={mape:.2f}%），建议作为参考，"
                    "考虑增加特征或尝试更复杂的模型。"
                )
            else:
                recs.append(
                    f"{best_model} 预测精度偏低（MAPE={mape:.2f}%），"
                    "建议检查数据质量、增加特征工程、或调整预测周期。"
                )

    # 数据相关建议
    if dataset_info:
        n_rows = dataset_info.get("n_rows", 0)
        if isinstance(n_rows, int) and n_rows < 100:
            recs.append("数据量较小（<100条），建议积累更多历史数据以提高预测可靠性。")

    # 模型多样性建议
    if model_results and len(model_results) >= 3:
        recs.append("建议使用集成方法（如加权平均）组合多个模型，以提高稳定性。")

    recs.append("建议定期回测并更新模型，以适应需求模式的变化。")
    recs.append("建议结合业务知识对预测结果进行校准，特别是在促销、节假日等特殊时期。")

    return recs
