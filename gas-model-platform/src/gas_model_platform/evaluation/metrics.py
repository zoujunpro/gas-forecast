from math import sqrt
from statistics import mean

from gas_model_platform.schemas.modeling import MetricSet


def regression_metrics(actual: list[float], predicted: list[float]) -> MetricSet:
    pairs = [(a, p) for a, p in zip(actual, predicted) if a is not None and p is not None]
    if not pairs:
        return MetricSet()

    abs_errors = [abs(a - p) for a, p in pairs]
    squared_errors = [(a - p) ** 2 for a, p in pairs]
    non_zero_pairs = [(a, p) for a, p in pairs if a != 0]
    mape = mean(abs((a - p) / a) for a, p in non_zero_pairs) * 100 if non_zero_pairs else None
    denominator = sum(abs(a) for a, _ in pairs)
    wmape = sum(abs_errors) / denominator * 100 if denominator else None

    return MetricSet(
        mape=round(mape, 4) if mape is not None else None,
        wmape=round(wmape, 4) if wmape is not None else None,
        rmse=round(sqrt(mean(squared_errors)), 4),
        mae=round(mean(abs_errors), 4),
    )

