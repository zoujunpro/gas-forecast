from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Protocol

from ..methods import MethodMetadata, MethodRegistry


@dataclass(frozen=True)
class BacktestFold:
    number: int
    train_start: int
    train_end: int
    validation_start: int
    validation_end: int


@dataclass(frozen=True)
class BacktestPlan:
    method_code: str
    minimum_train_size: int
    initial_train_size: int
    validation_size: int
    step_size: int
    available_fold_count: int
    folds: tuple[BacktestFold, ...]


class BacktestPlanner(Protocol):
    metadata: MethodMetadata

    def plan(
        self, row_count: int, forecast_horizon: int, params: dict[str, Any]
    ) -> BacktestPlan: ...


class BacktestPlannerRegistry(MethodRegistry[BacktestPlanner]):
    pass


class ExpandingWalkForwardPlanner:
    """短期扩展窗口回测。

    自动模式使用与真实任务相同的预测长度作为验证窗口和步长，优先选取
    靠近预测时点的连续、互不重叠窗口。折数由历史长度、最低训练长度和
    计算预算共同决定，不再把少量折人为均匀铺满整段历史。
    """

    metadata = MethodMetadata("EXPANDING_WALK_FORWARD_V1", "扩展窗口滚动回测")

    def plan(self, row_count, forecast_horizon, params):
        mode = str(params.get("mode", "auto")).lower()
        max_folds = int(params.get("max_folds", 5))
        min_folds = int(params.get("min_folds", 1))
        if mode == "fixed":
            validation_size = int(params.get("validation_size", forecast_horizon))
            step_size = int(params.get("step_size", validation_size))
            minimum_train = int(params.get("minimum_train_size", 1))
        elif mode == "auto":
            if forecast_horizon < 1:
                raise ValueError("预测长度必须为正整数")
            validation_size = int(forecast_horizon)
            minimum_train_floor = int(params.get("minimum_train_floor", 30))
            minimum_train = max(
                minimum_train_floor,
                int(params.get("minimum_train_size", minimum_train_floor)),
            )
            # 每折完整模拟一次真实预测任务；步长等于预测长度，避免相邻折
            # 共享验证日期而夸大有效样本量。
            step_size = validation_size
            available_range = row_count - minimum_train
            possible = 0 if available_range < validation_size else available_range // step_size
            fold_count = min(max_folds, possible)
            if fold_count < min_folds:
                raise ValueError(
                    f"历史数据不足以生成{min_folds}折回测: rows={row_count}, "
                    f"minimum_train={minimum_train}, validation={validation_size}"
                )
        else:
            raise ValueError(f"不支持的回测规划模式: {mode}")
        if min(validation_size, step_size, max_folds, min_folds, minimum_train) < 1:
            raise ValueError("回测窗口、步长、折数和最小训练长度必须为正整数")
        available = row_count - minimum_train - validation_size
        if mode == "fixed":
            possible = 0 if available < 0 else available // step_size + 1
            fold_count = min(max_folds, possible)
        if fold_count < min_folds:
            raise ValueError(
                f"历史数据不足以生成{min_folds}折回测: rows={row_count}, "
                f"minimum_train={minimum_train}, validation={validation_size}, step={step_size}"
            )
        # 最后一折始终紧邻历史末端，其余折按预测长度连续向前排列。
        first_validation = row_count - validation_size - step_size * (fold_count - 1)
        folds = tuple(
            BacktestFold(
                number=index + 1,
                train_start=0,
                train_end=first_validation + index * step_size,
                validation_start=first_validation + index * step_size,
                validation_end=first_validation + index * step_size + validation_size,
            )
            for index in range(fold_count)
        )
        return BacktestPlan(
            self.metadata.code,
            minimum_train,
            folds[0].train_end,
            validation_size,
            step_size,
            possible,
            folds,
        )


def create_default_backtest_registry() -> BacktestPlannerRegistry:
    return BacktestPlannerRegistry([ExpandingWalkForwardPlanner()])
