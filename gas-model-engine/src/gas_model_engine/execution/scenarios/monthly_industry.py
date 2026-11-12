from __future__ import annotations

from ..scenario import ForecastScenario


class MonthlyIndustryScenario(ForecastScenario):
    """月度行业场景钩子，复用统一生产预测流程。"""

    code = "MONTHLY_INDUSTRY_V1"
