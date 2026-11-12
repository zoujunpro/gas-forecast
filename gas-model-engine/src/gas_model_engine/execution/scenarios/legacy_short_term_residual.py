from __future__ import annotations

from ..scenario import ForecastScenario


class LegacyShortTermResidualScenario(ForecastScenario):
    """旧短期残差场景钩子，复用统一生产预测流程。"""

    code = "LEGACY_SHORT_TERM_RESIDUAL_V1"
