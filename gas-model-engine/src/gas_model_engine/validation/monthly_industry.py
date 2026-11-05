from __future__ import annotations

from typing import TYPE_CHECKING

from .base import BaseForecastRequestValidator

if TYPE_CHECKING:
    from ..execution.contracts import ForecastRequest


class MonthlyIndustryValidator(BaseForecastRequestValidator):
    """月度行业校验器；月频连续性和行业专属规则在迁移旧逻辑时在此补充。"""

    code = "MONTHLY_INDUSTRY_V1"

    def validate(self, request: ForecastRequest) -> None:
        """执行月度行业当前已明确的基础数据校验。"""
        self.validate_history(request.train_data, "月度行业")
        self.validate_future(request.future_data, "月度行业", features_optional=True)
