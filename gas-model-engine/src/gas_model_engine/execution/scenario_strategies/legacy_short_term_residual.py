from __future__ import annotations


class LegacyShortTermResidualStrategy:
    """旧短期残差流程占位，保留独立编码用于后续逐项一致性迁移。"""

    code = "LEGACY_SHORT_TERM_RESIDUAL_V1"

    def execute(self, request, profile, executor):
        raise NotImplementedError(
            "预测策略 LEGACY_SHORT_TERM_RESIDUAL_V1 尚未完成等价迁移：需要恢复 Prophet 基线、"
            "残差模型、Walk-forward 调参和残差修正细节"
        )
