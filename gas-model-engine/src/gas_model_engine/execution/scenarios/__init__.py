"""预测场景钩子。

每个场景可以准备数据或覆盖参数；训练、回测、选优和预测始终由
``ForecastPipeline`` 中的唯一生产流程执行。
"""

from .legacy_short_term_residual import LegacyShortTermResidualScenario
from .monthly_industry import MonthlyIndustryScenario
from .short_term_customer import ShortTermCustomerScenario
from .winter_supply import WinterSupplyScenario

__all__ = [
    "LegacyShortTermResidualScenario",
    "MonthlyIndustryScenario",
    "ShortTermCustomerScenario",
    "WinterSupplyScenario",
]
