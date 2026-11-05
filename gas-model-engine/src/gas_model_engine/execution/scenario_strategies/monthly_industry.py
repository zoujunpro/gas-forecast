from __future__ import annotations


class MonthlyIndustryStrategy:
    """月度行业预测策略占位。

    月度预测必须按月频率生成回测起点，并恢复原月度模型的候选、调参、组合及
    评价逻辑；在完成等价迁移前不复用日级短期预测流程。
    """

    code = "MONTHLY_INDUSTRY_V1"

    def execute(self, request, profile, executor):
        raise NotImplementedError(
            "预测策略 MONTHLY_INDUSTRY_V1 尚未完成等价迁移：需要接入月度行业特征、"
            "按月回测和原月度模型选优逻辑；不会回退到日级短期预测流程"
        )
