"""业务人员可提问的问题目录；目录只定义系统能够回答的问题。"""

from dataclasses import dataclass


@dataclass(frozen=True)
class ForecastQuestionTemplate:
    code: str
    category: str
    template: str
    required_data: tuple[str, ...]


FORECAST_QUESTION_CATALOG = (
    ForecastQuestionTemplate("FORECAST_TREND", "TREND", "未来{days}天用气趋势如何？", ("forecast_points",)),
    ForecastQuestionTemplate("FORECAST_CHANGE_RATE", "CHANGE_RATE", "未来{days}天用气量预计{direction}多少？", ("forecast_average", "reference_average")),
    ForecastQuestionTemplate("FORECAST_CAUSE", "CAUSE", "用气量{direction}可能受哪些因素影响？", ("forecast_points", "reference_average")),
    ForecastQuestionTemplate("FORECAST_PEAK", "PEAK", "未来{days}天用气峰值何时出现？", ("forecast_points", "peak_date")),
    ForecastQuestionTemplate("FORECAST_ACCURACY", "ACCURACY", "{mape_text}的MAPE意味着什么？", ("mape",)),
    ForecastQuestionTemplate("FORECAST_RISK", "RISK", "未来{days}天是否存在异常用气风险？", ("forecast_points", "risk_threshold")),
)
