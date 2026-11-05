"""场景预测策略。

每个场景使用独立文件，但统一接收 ForecastRequest 并返回 ForecastResult。
公共训练、回测和预测步骤由 execution.strategies 中的模板复用。
"""

from .legacy_short_term_residual import LegacyShortTermResidualStrategy
from .monthly_industry import MonthlyIndustryStrategy
from .short_term_customer import ShortTermCustomerStrategy
from .winter_supply import WinterSupplyStrategy

__all__ = [
    "LegacyShortTermResidualStrategy",
    "MonthlyIndustryStrategy",
    "ShortTermCustomerStrategy",
    "WinterSupplyStrategy",
]
