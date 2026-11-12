from __future__ import annotations

from ..scenario import ForecastScenario


class WinterSupplyScenario(ForecastScenario):
    """冬季保供场景钩子，复用统一生产预测流程。"""

    code = "WINTER_SUPPLY_V1"
