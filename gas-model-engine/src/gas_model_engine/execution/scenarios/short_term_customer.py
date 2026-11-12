from __future__ import annotations

from ...data_preparation import ShortTermCustomerDataCleaner
from ...validation import ShortTermCustomerValidator
from ..scenario import ForecastScenario


class ShortTermCustomerScenario(ForecastScenario):
    """短期客户预测策略。

    复用公共配置驱动流程：按本次预测长度验证、均匀安排 Walk-forward
    起点、使用前段折 Tune、末段折 Eval，并统一比较原子、残差和结果组合。
    输入和输出仍为统一的 ForecastRequest / ForecastResult。
    """

    code = "SHORT_TERM_CUSTOMER_V1"

    def __init__(self, *args, data_cleaner=None, validator=None, **kwargs):
        super().__init__(*args, **kwargs)
        self.data_cleaner = data_cleaner or ShortTermCustomerDataCleaner()
        self.validator = validator or ShortTermCustomerValidator()

    def prepare_request(self, request, profile):
        """在特征解析和执行计划前，先清洗，再执行短期客户专属校验。"""
        cleaned = self.data_cleaner.clean(request)
        self.validator.validate(cleaned)
        return cleaned

    def prepare_plan_params(self, request, profile) -> dict:
        """按历史长度和预测长度计算短期客户的回测约束。

        历史足以容纳一年训练和至少6个完整预测窗口时，最低训练长度取365天；
        否则降为至少90天或两个预测周期。具体折数继续由回测规划器计算。
        """
        history_rows = len(request.train_data.features)
        horizon = len(request.future_data.features)
        seasonal_minimum = 365 if history_rows >= 365 + 6 * horizon else max(90, 2 * horizon)
        params = {
            "mode": "auto",
            "minimum_train_floor": seasonal_minimum,
            "min_folds": 6,
            "max_folds": 10,
            "minimum_tune_folds": 3,
            "minimum_eval_folds": 3,
            "eval_ratio": 0.4,
            "prediction_interval_coverage": 0.8,
        }
        params.update(profile.backtest.params)
        return params
