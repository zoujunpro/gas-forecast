from __future__ import annotations

from collections.abc import Callable
from time import perf_counter

import numpy as np

from ..calendar import LocalHolidayProvider
from ..evaluation import calculate_metrics
from .contracts import BenchmarkDataset, BenchmarkReport, ModelBenchmark

Predictor = Callable[[object], object]


class ForecastBenchmark:
    """在完全相同的样本上运行旧实现和新 Pipeline，并生成迁移验收报告。

    该工具只用于离线验收，不参与线上 ``ForecastPipeline.run``。调用方使用
    两个小适配器把各自输出转换成一维预测数组，因此旧工程无需依赖新引擎。
    """

    def __init__(self, holiday_provider: LocalHolidayProvider | None = None) -> None:
        self.holiday_provider = holiday_provider or LocalHolidayProvider()

    def compare(
        self,
        dataset: BenchmarkDataset,
        old_predictor: Predictor,
        new_predictor: Predictor,
        *,
        old_name: str = "旧代码",
        new_name: str = "新Pipeline",
    ) -> BenchmarkReport:
        """依次运行两侧适配器；正数 delta 表示新方案的该项指标更大。"""
        old_values, old_elapsed = self._timed_predict(old_predictor, dataset)
        new_values, new_elapsed = self._timed_predict(new_predictor, dataset)
        spring_mask = self._spring_festival_mask(dataset)
        old = self._evaluate(old_name, old_values, old_elapsed, dataset, spring_mask)
        new = self._evaluate(new_name, new_values, new_elapsed, dataset, spring_mask)
        return BenchmarkReport(
            old=old,
            new=new,
            metric_delta={key: new.metrics[key] - old.metrics[key] for key in old.metrics},
            sample_count=len(dataset.actual),
            fold_count=len(np.unique(dataset.fold_ids)),
            spring_festival_sample_count=int(spring_mask.sum()),
        )

    @staticmethod
    def _timed_predict(predictor: Predictor, dataset: BenchmarkDataset) -> tuple[np.ndarray, float]:
        started = perf_counter()
        values = np.asarray(predictor(dataset.payload), dtype=float).reshape(-1)
        elapsed = perf_counter() - started
        if len(values) != len(dataset.actual):
            raise ValueError(
                f"预测结果长度{len(values)}与验收真实值长度{len(dataset.actual)}不一致"
            )
        if not np.isfinite(values).all():
            raise ValueError("预测结果必须全部为有限数值")
        return values, elapsed

    def _spring_festival_mask(self, dataset: BenchmarkDataset) -> np.ndarray:
        calendar = self.holiday_provider.build_calendar(dataset.dates)
        return calendar["holiday_name_en"].eq("Spring Festival").to_numpy(dtype=bool)

    @staticmethod
    def _evaluate(
        name: str,
        predictions: np.ndarray,
        elapsed: float,
        dataset: BenchmarkDataset,
        spring_mask: np.ndarray,
    ) -> ModelBenchmark:
        fold_metrics = {}
        for fold_id in dict.fromkeys(dataset.fold_ids.tolist()):
            mask = dataset.fold_ids == fold_id
            fold_metrics[str(fold_id)] = calculate_metrics(dataset.actual[mask], predictions[mask])
        fold_mapes = [item["mape"] for item in fold_metrics.values()]
        spring_metrics = (
            calculate_metrics(dataset.actual[spring_mask], predictions[spring_mask])
            if spring_mask.any()
            else None
        )
        return ModelBenchmark(
            name=name,
            metrics=calculate_metrics(dataset.actual, predictions),
            fold_metrics=fold_metrics,
            error_std=float(np.std(fold_mapes)),
            spring_festival_metrics=spring_metrics,
            elapsed_seconds=elapsed,
            predictions=predictions,
        )


def render_markdown(report: BenchmarkReport) -> str:
    """把结构化报告渲染成便于评审和归档的 Markdown。"""

    def value(metrics, key):
        return "无样本" if metrics is None else f"{metrics[key]:.4f}"

    lines = [
        "# 新旧预测对比报告",
        "",
        f"样本数：{report.sample_count}；回测折数：{report.fold_count}；"
        f"春节样本数：{report.spring_festival_sample_count}。",
        "",
        "| 指标 | 旧代码 | 新Pipeline | 新-旧 |",
        "|---|---:|---:|---:|",
    ]
    for key in ("mape", "rmse", "mae", "wmape"):
        lines.append(
            f"| {key.upper()} | {report.old.metrics[key]:.4f} | "
            f"{report.new.metrics[key]:.4f} | {report.metric_delta[key]:+.4f} |"
        )
    lines.extend(
        [
            f"| 每折MAPE标准差 | {report.old.error_std:.4f} | "
            f"{report.new.error_std:.4f} | {report.new.error_std - report.old.error_std:+.4f} |",
            f"| 春节MAPE | {value(report.old.spring_festival_metrics, 'mape')} | "
            f"{value(report.new.spring_festival_metrics, 'mape')} | - |",
            f"| 执行时间（秒） | {report.old.elapsed_seconds:.4f} | "
            f"{report.new.elapsed_seconds:.4f} | "
            f"{report.new.elapsed_seconds - report.old.elapsed_seconds:+.4f} |",
            "",
            "## 每折误差",
            "",
            "| 折 | 旧代码MAPE | 新Pipeline MAPE |",
            "|---|---:|---:|",
        ]
    )
    for fold_id, old_metrics in report.old.fold_metrics.items():
        lines.append(
            f"| {fold_id} | {old_metrics['mape']:.4f} | "
            f"{report.new.fold_metrics[fold_id]['mape']:.4f} |"
        )
    return "\n".join(lines) + "\n"
