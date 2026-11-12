"""从预测特征中选择业务人员本次最值得关注的三个问题。"""

from dataclasses import dataclass
from statistics import fmean, pstdev
from typing import Any

from .forecast_question_catalog import FORECAST_QUESTION_CATALOG, ForecastQuestionTemplate


@dataclass(frozen=True)
class SelectedForecastQuestion:
    code: str
    category: str
    text: str


def _number(value: Any) -> float | None:
    try:
        return float(value) if value is not None else None
    except (TypeError, ValueError):
        return None


def _points(context: dict[str, Any], section: str) -> list[dict[str, Any]]:
    value = context.get(section) or {}
    points = value.get("points") if isinstance(value, dict) else None
    return [item for item in points or [] if isinstance(item, dict)]


def _values(points: list[dict[str, Any]], *keys: str) -> list[float]:
    result: list[float] = []
    for point in points:
        value = None
        for key in keys:
            value = _number(point.get(key))
            if value is not None:
                break
        if value is not None:
            result.append(value)
    return result


def _available(template: ForecastQuestionTemplate, features: dict[str, Any]) -> bool:
    return all(features.get(name) is not None for name in template.required_data)


def select_forecast_questions(context: dict[str, Any]) -> list[SelectedForecastQuestion]:
    forecast_points = _points(context, "forecast")
    backtest_points = _points(context, "backtest")
    forecast_values = _values(forecast_points, "prediction", "forecast_value", "value")
    reference_values = _values(backtest_points, "actual", "actual_value", "actualValue")
    forecast_average = fmean(forecast_values) if forecast_values else None
    reference_average = fmean(reference_values) if reference_values else None
    change_rate = ((forecast_average - reference_average) / reference_average * 100) if forecast_average is not None and reference_average not in (None, 0) else None
    direction = "上升" if change_rate is not None and change_rate > 1 else "下降" if change_rate is not None and change_rate < -1 else "基本持平"
    peak_index = forecast_values.index(max(forecast_values)) if forecast_values else None
    peak_date = None if peak_index is None else str(forecast_points[peak_index].get("forecast_date") or forecast_points[peak_index].get("date") or forecast_points[peak_index].get("ds") or "预测期内")
    volatility = pstdev(forecast_values) / abs(forecast_average) if len(forecast_values) > 1 and forecast_average else 0.0
    mape = _number(context.get("mape"))
    days = int(context.get("forecastHorizon") or len(forecast_values) or 0)
    features = {
        "forecast_points": forecast_points or None,
        "forecast_average": forecast_average,
        "reference_average": reference_average,
        "peak_date": peak_date,
        "mape": mape,
        "risk_threshold": context.get("riskThreshold"),
    }
    scores = {"TREND": 40, "CHANGE_RATE": 0, "CAUSE": 20, "PEAK": 0, "ACCURACY": 25, "RISK": 0}
    if change_rate is not None and abs(change_rate) >= 5:
        scores["CHANGE_RATE"] += 90
        scores["CAUSE"] += 70
    if volatility >= 0.1:
        scores["PEAK"] += 80
    if mape is not None:
        scores["ACCURACY"] += 60 if mape >= 10 else 40
    if context.get("riskThreshold") is not None:
        scores["RISK"] += 60
    selected = sorted((item for item in FORECAST_QUESTION_CATALOG if _available(item, features)), key=lambda item: scores[item.category], reverse=True)[:3]
    values = {"days": days, "direction": direction, "mape_text": "未提供" if mape is None else f"{mape:.2f}%"}
    return [SelectedForecastQuestion(item.code, item.category, item.template.format(**values)) for item in selected]
