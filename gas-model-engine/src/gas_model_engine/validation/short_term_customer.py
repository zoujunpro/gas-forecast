from __future__ import annotations

from typing import TYPE_CHECKING

from .base import BaseForecastRequestValidator

if TYPE_CHECKING:
    from ..execution.contracts import ForecastRequest


class ShortTermCustomerValidator(BaseForecastRequestValidator):
    """短期客户校验：历史销量必填，未来外部特征可选、传入则必须有效。"""

    code = "SHORT_TERM_CUSTOMER_V1"

    def validate(self, request: ForecastRequest) -> None:
        """校验清洗后的短期客户历史数据和未来输入。"""
        self.validate_history(request.train_data, "短期客户")
        self.validate_future(
            request.future_data,
            "短期客户",
            features_optional=True,
        )
        if request.future_data.dates.min() <= request.train_data.dates.max():
            raise ValueError("短期客户未来预测日期必须晚于历史数据")
