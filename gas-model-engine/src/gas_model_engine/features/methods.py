from __future__ import annotations

import numpy as np
import pandas as pd

from ..algorithms.contracts import AlgorithmDataset
from ..calendar import LocalHolidayProvider
from ..methods import MethodMetadata
from .base import FeatureMethodRegistry


def _copy(data: AlgorithmDataset, features: pd.DataFrame) -> AlgorithmDataset:
    return AlgorithmDataset(
        features=features,
        dates=data.dates,
        target=data.target,
        sample_weight=data.sample_weight,
        metadata=dict(data.metadata),
    )


class CalendarFeatureMethod:
    """从日期生成稳定的日历特征。"""

    metadata = MethodMetadata("CALENDAR_V1", "日历特征")

    def transform(self, train_data, future_data, params):
        requested = tuple(
            params.get(
                "features",
                ["year", "month", "day", "dow", "is_weekend"],
            )
        )

        def add(data):
            frame = data.features.copy()
            dates = data.dates
            values = {
                "year": dates.year,
                "month": dates.month,
                "day": dates.day,
                "dow": dates.dayofweek,
                "day_of_year": dates.dayofyear,
                "week_of_year": dates.isocalendar().week.to_numpy(dtype=int),
                "quarter": dates.quarter,
                "is_weekend": (dates.dayofweek >= 5).astype(int),
            }
            unknown = set(requested) - set(values) - {"t"}
            if unknown:
                raise ValueError(f"不支持的日历特征: {', '.join(sorted(unknown))}")
            for name in requested:
                if name == "t":
                    continue
                frame[name] = values[name]
            return _copy(data, frame)

        train, future = add(train_data), add(future_data)
        if "t" in requested:
            origin = train_data.dates.min()
            train.features["t"] = (train_data.dates - origin).days
            future.features["t"] = (future_data.dates - origin).days
        return train, future


class ShortTermWeatherFeatureMethod:
    """根据英文天气字段为短期客户动态生成温度和湿度派生特征。"""

    metadata = MethodMetadata("SHORT_TERM_WEATHER_V1", "短期客户天气派生特征")

    def transform(self, train_data, future_data, params):
        requested = set(
            params.get(
                "features",
                [
                    "hdd",
                    "cdd",
                    "hdd_lag1",
                    "hdd_roll3",
                    "cdd_roll3",
                    "temp_range",
                    "temp_roll7",
                    "humidity_roll3",
                ],
            )
        )
        supported = {
            "hdd",
            "cdd",
            "hdd_lag1",
            "hdd_roll3",
            "cdd_roll3",
            "temp_range",
            "temp_roll7",
            "humidity_roll3",
        }
        unknown = requested - supported
        if unknown:
            raise ValueError(f"不支持的天气派生特征: {', '.join(sorted(unknown))}")

        train_size = len(train_data.features)
        combined = pd.concat(
            [train_data.features.copy(), future_data.features.copy()], ignore_index=True
        )
        generated = []
        if "temp" in combined:
            temp = pd.to_numeric(combined["temp"], errors="coerce")
            hdd = (float(params.get("hdd_base_temperature", 18.0)) - temp).clip(lower=0)
            cdd = (temp - float(params.get("cdd_base_temperature", 26.0))).clip(lower=0)
            if "hdd" in requested:
                combined["hdd"] = hdd
                generated.append("hdd")
            if "cdd" in requested:
                combined["cdd"] = cdd
                generated.append("cdd")
            if "hdd_lag1" in requested:
                combined["hdd_lag1"] = hdd.shift(1)
                generated.append("hdd_lag1")
            if "hdd_roll3" in requested:
                combined["hdd_roll3"] = hdd.rolling(
                    int(params.get("hdd_rolling_window", 3)), min_periods=1
                ).mean()
                generated.append("hdd_roll3")
            if "cdd_roll3" in requested:
                combined["cdd_roll3"] = cdd.rolling(
                    int(params.get("cdd_rolling_window", 3)), min_periods=1
                ).mean()
                generated.append("cdd_roll3")
            if "temp_roll7" in requested:
                combined["temp_roll7"] = temp.rolling(
                    int(params.get("temperature_rolling_window", 7)), min_periods=1
                ).mean()
                generated.append("temp_roll7")
        if "temp_range" in requested and {"tempmax", "tempmin"} <= set(combined.columns):
            combined["temp_range"] = pd.to_numeric(
                combined["tempmax"], errors="coerce"
            ) - pd.to_numeric(combined["tempmin"], errors="coerce")
            generated.append("temp_range")
        if "humidity_roll3" in requested and "humidity" in combined:
            humidity = pd.to_numeric(combined["humidity"], errors="coerce")
            combined["humidity_roll3"] = humidity.rolling(
                int(params.get("humidity_rolling_window", 3)), min_periods=1
            ).mean()
            generated.append("humidity_roll3")
        if not generated:
            raise ValueError("未提供temp、tempmax/tempmin或humidity，无法生成天气派生特征")
        return _copy(train_data, combined.iloc[:train_size].reset_index(drop=True)), _copy(
            future_data, combined.iloc[train_size:].reset_index(drop=True)
        )


class ChinaHolidayFeatureMethod:
    """使用本地中国节假日数据动态生成短期客户节假日特征。"""

    metadata = MethodMetadata("HOLIDAY_CN_V1", "中国节假日特征")

    def __init__(self, provider=None):
        self.provider = provider or LocalHolidayProvider()

    def transform(self, train_data, future_data, params):
        requested = tuple(
            params.get(
                "features",
                [
                    "is_holiday",
                    "is_workday",
                    "is_makeup_workday",
                    "holiday_type",
                    "days_to_holiday",
                    "days_after_holiday",
                    "days_to_cny",
                    "days_to_national_day",
                ],
            )
        )
        dates = train_data.dates.append(future_data.dates)
        calendar = self.provider.build_calendar(dates)
        self._add_distances(calendar, dates)
        available = set(calendar.columns) - {"ds", "holiday_name_en", "holiday_name_zh"}
        unknown = set(requested) - available
        if unknown:
            raise ValueError(f"不支持的节假日特征: {', '.join(sorted(unknown))}")
        train_size = len(train_data.features)
        train = train_data.features.copy()
        future = future_data.features.copy()
        for name in requested:
            train[name] = calendar[name].iloc[:train_size].to_numpy()
            future[name] = calendar[name].iloc[train_size:].to_numpy()
        return _copy(train_data, train), _copy(future_data, future)

    def _add_distances(self, calendar: pd.DataFrame, dates: pd.DatetimeIndex) -> None:
        holiday_dates = pd.DatetimeIndex(
            pd.to_datetime(list(self.provider.data["holidays"].keys()))
        )
        calendar["days_to_holiday"] = [self._next_distance(date, holiday_dates) for date in dates]
        calendar["days_after_holiday"] = [
            self._previous_distance(date, holiday_dates) for date in dates
        ]
        for output, english_name in (
            ("days_to_cny", "Spring Festival"),
            ("days_to_national_day", "National Day"),
        ):
            selected = pd.DatetimeIndex(
                pd.to_datetime(
                    [
                        date
                        for date, info in self.provider.data["holidays"].items()
                        if info.startswith(english_name + ",")
                    ]
                )
            )
            calendar[output] = [self._absolute_distance(date, selected) for date in dates]

    @staticmethod
    def _next_distance(date, candidates: pd.DatetimeIndex) -> int:
        future = candidates[candidates >= date]
        return int((future.min() - date).days) if len(future) else 365

    @staticmethod
    def _previous_distance(date, candidates: pd.DatetimeIndex) -> int:
        history = candidates[candidates <= date]
        return int((date - history.max()).days) if len(history) else 365

    @staticmethod
    def _absolute_distance(date, candidates: pd.DatetimeIndex) -> int:
        if not len(candidates):
            return 365
        return int(np.abs((candidates - date).days).min())


class DynamicPassthroughFeatureMethod:
    """保留调用方同时提供给历史和未来数据的数值动态字段。"""

    metadata = MethodMetadata("DYNAMIC_PASSTHROUGH_V1", "动态特征透传")

    def transform(self, train_data, future_data, params):
        excluded = set(params.get("exclude", ()))
        common = [
            column
            for column in train_data.features.columns
            if column in future_data.features.columns
            and column not in excluded
            and pd.api.types.is_numeric_dtype(train_data.features[column])
            and pd.api.types.is_numeric_dtype(future_data.features[column])
        ]
        required = set(params.get("required_columns", ()))
        missing = required - set(common)
        if missing:
            raise ValueError(f"缺少必需动态特征: {', '.join(sorted(missing))}")
        return _copy(train_data, train_data.features[common]), _copy(
            future_data, future_data.features[common]
        )


class LagFeatureMethod:
    """根据目标历史生成滞后特征；训练开头保留空值供预热过滤。"""

    metadata = MethodMetadata("LAG_V1", "目标滞后特征")

    def transform(self, train_data, future_data, params):
        if train_data.target is None:
            raise ValueError("LAG_V1需要历史目标")
        lags = sorted({int(value) for value in params.get("lags", [1, 7, 14])})
        if not lags or min(lags) < 1:
            raise ValueError("lags必须为正整数")
        history = train_data.target.reset_index(drop=True)
        train = train_data.features.copy()
        future = future_data.features.copy()
        extended = pd.concat(
            [history, pd.Series([np.nan] * len(future), dtype=float)], ignore_index=True
        ).ffill()
        for lag in lags:
            name = f"target_lag_{lag}"
            train[name] = history.shift(lag)
            future[name] = extended.shift(lag).iloc[len(history) :].to_numpy()
        return _copy(train_data, train), _copy(future_data, future)


class RollingFeatureMethod:
    """根据过去目标生成滚动统计，计算时始终先shift以避免看到当期目标。"""

    metadata = MethodMetadata("ROLLING_V1", "目标滚动特征")

    def transform(self, train_data, future_data, params):
        if train_data.target is None:
            raise ValueError("ROLLING_V1需要历史目标")
        windows = sorted({int(value) for value in params.get("windows", [3, 7, 14])})
        statistics = tuple(params.get("statistics", ["mean", "std"]))
        unsupported = set(statistics) - {"mean", "std", "min", "max"}
        if unsupported:
            raise ValueError(f"不支持的滚动统计: {', '.join(sorted(unsupported))}")
        history = train_data.target.reset_index(drop=True)
        train = train_data.features.copy()
        future = future_data.features.copy()
        extended = pd.concat(
            [history, pd.Series([np.nan] * len(future), dtype=float)], ignore_index=True
        ).ffill()
        for window in windows:
            if window < 1:
                raise ValueError("rolling windows必须为正整数")
            train_rolling = history.shift(1).rolling(window, min_periods=window)
            future_rolling = extended.shift(1).rolling(window, min_periods=window)
            for statistic in statistics:
                name = f"target_rolling_{statistic}_{window}"
                train[name] = getattr(train_rolling, statistic)()
                future[name] = getattr(future_rolling, statistic)().iloc[len(history) :].to_numpy()
        return _copy(train_data, train), _copy(future_data, future)


def create_default_feature_registry() -> FeatureMethodRegistry:
    return FeatureMethodRegistry(
        [
            DynamicPassthroughFeatureMethod(),
            CalendarFeatureMethod(),
            ChinaHolidayFeatureMethod(),
            ShortTermWeatherFeatureMethod(),
            LagFeatureMethod(),
            RollingFeatureMethod(),
        ]
    )
