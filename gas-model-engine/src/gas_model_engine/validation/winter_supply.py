from __future__ import annotations

from typing import TYPE_CHECKING

from .base import BaseForecastRequestValidator

if TYPE_CHECKING:
    from ..execution.contracts import ForecastRequest


class WinterSupplyValidator(BaseForecastRequestValidator):
    """冬季保供校验器；供暖季和地区专属规则在迁移旧逻辑时在此补充。"""

    code = "WINTER_SUPPLY_V1"

    def validate(self, request: ForecastRequest) -> None:
        """执行冬季保供当前已明确的基础数据校验。"""
        self.validate_history(request.train_data, "冬季保供")
        self.validate_future(request.future_data, "冬季保供", features_optional=True)
