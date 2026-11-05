from __future__ import annotations


class WinterSupplyStrategy:
    """冬季保供预测策略占位。

    冬季保供应按历史供暖季划分 Tune/Eval，并恢复旧流程的 Equal、Inverse、
    NNLS、Median 组合与统一排名。在完成等价迁移前明确拒绝执行，避免错误复用
    短期客户的均匀 Walk-forward 计划。
    """

    code = "WINTER_SUPPLY_V1"

    def execute(self, request, profile, executor):
        raise NotImplementedError(
            "预测策略 WINTER_SUPPLY_V1 尚未完成等价迁移：需要按历史供暖季生成回测，"
            "并接入 Equal/Inverse Top2/3/5、NNLS Top5、Median Top5 和 Eval 统一选优；"
            "在完成迁移前不会回退到通用组合流程"
        )
