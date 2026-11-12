from __future__ import annotations

import json
from importlib.resources import files
from pathlib import Path

import pandas as pd


class LocalHolidayProvider:
    """读取项目内维护的中国法定节假日和调休补班数据。"""

    DEFAULT_RESOURCE = "config/holidays/cn-2004-2026.json"
    # 连续放假达到3天时视为长假；影响期向假期起止日期两侧各扩展2天。
    LONG_HOLIDAY_MIN_DAYS = 3
    LONG_HOLIDAY_LEAD_DAYS = 2
    LONG_HOLIDAY_LAG_DAYS = 2

    def __init__(self, path: str | Path | None = None) -> None:
        self.path = Path(path) if path is not None else None
        self._data: dict | None = None

    @property
    def data(self) -> dict:
        """延迟加载并返回本地节假日原始数据。"""
        if self._data is None:
            if self.path is not None:
                text = self.path.read_text(encoding="utf-8")
            else:
                resource = files("gas_model_engine").joinpath(self.DEFAULT_RESOURCE)
                text = resource.read_text(encoding="utf-8")
            self._data = json.loads(text)
            self._validate_data(self._data)
        return self._data

    def build_calendar(self, dates) -> pd.DataFrame:
        """为给定日期生成节假日、普通周末、工作日和调休补班标识。"""
        index = pd.DatetimeIndex(pd.to_datetime(dates)).normalize()
        holidays = self.data["holidays"]
        makeup_workdays = self.data["workdays"]
        coverage = self.data["coverage"]
        rows = []
        for date in index:
            key = date.strftime("%Y-%m-%d")
            holiday_info = holidays.get(key)
            workday_info = makeup_workdays.get(key)
            info = holiday_info or workday_info
            name_en, name_zh, holiday_type = self._parse_info(info)
            is_holiday = holiday_info is not None
            is_makeup_workday = workday_info is not None
            rows.append(
                {
                    "ds": date,
                    "is_holiday": int(is_holiday),
                    "is_weekend": int(date.dayofweek >= 5),
                    "is_makeup_workday": int(is_makeup_workday),
                    "is_workday": int(is_makeup_workday or (date.dayofweek < 5 and not is_holiday)),
                    "holiday_name_en": name_en,
                    "holiday_name_zh": name_zh,
                    "holiday_type": holiday_type,
                    "holiday_calendar_confirmed": int(
                        coverage["start_year"] <= date.year <= coverage["end_year"]
                    ),
                }
            )
        calendar = pd.DataFrame(rows)
        phases = self._holiday_phases()
        for column in (
            "holiday_day_index",
            "holiday_total_days",
            "days_until_holiday_end",
        ):
            calendar[column] = [phases.get(date, {}).get(column, 0) for date in index]
        holiday_ends = pd.DatetimeIndex(
            [values["holiday_end"] for values in phases.values()]
        ).unique()
        calendar["days_since_holiday_end"] = [
            0 if date in phases else self._days_since(date, holiday_ends) for date in index
        ]
        long_holiday_windows = self._long_holiday_windows()
        calendar["is_long_holiday_window"] = [
            int(any(start <= date <= end for start, end, _ in long_holiday_windows))
            for date in index
        ]
        return calendar

    def build_prophet_events(self) -> pd.DataFrame:
        """生成Prophet事件；长假覆盖实际假期以及开始前、结束后各2天。"""
        rows = []
        for block in self._holiday_blocks():
            start = block[0][0]
            name_en = self._dominant_holiday_name(block)
            is_long_holiday = len(block) >= self.LONG_HOLIDAY_MIN_DAYS
            if is_long_holiday:
                # Prophet的窗口以ds为基准，所以upper_window需要包含整个假期长度。
                rows.append(
                    {
                        "holiday": name_en,
                        "ds": start,
                        "lower_window": -self.LONG_HOLIDAY_LEAD_DAYS,
                        "upper_window": len(block) - 1 + self.LONG_HOLIDAY_LAG_DAYS,
                    }
                )
            else:
                for date, _ in block:
                    rows.append(
                        {
                            "holiday": name_en,
                            "ds": date,
                            "lower_window": 0,
                            "upper_window": 0,
                        }
                    )
        return pd.DataFrame(rows).sort_values(["ds", "holiday"]).reset_index(drop=True)

    def _holiday_phases(self) -> dict[pd.Timestamp, dict[str, int | pd.Timestamp]]:
        """把同名且日期连续的放假记录识别为一个假期区间。"""
        blocks = self._holiday_blocks()
        phases = {}
        for block in blocks:
            end = block[-1][0]
            total = len(block)
            for offset, (date, _) in enumerate(block):
                phases[date] = {
                    "holiday_day_index": offset + 1,
                    "holiday_total_days": total,
                    "days_until_holiday_end": (end - date).days,
                    "holiday_end": end,
                }
        return phases

    def _holiday_blocks(self) -> list[list[tuple[pd.Timestamp, str]]]:
        """按连续日期切分实际放假区间；相连的中秋、国庆也属于同一长假。"""
        entries = []
        for value, info in self.data["holidays"].items():
            name_en, _, _ = self._parse_info(info)
            entries.append((pd.Timestamp(value), name_en))
        entries.sort()
        blocks: list[list[tuple[pd.Timestamp, str]]] = []
        for entry in entries:
            if (
                blocks
                and (entry[0] - blocks[-1][-1][0]).days == 1
            ):
                blocks[-1].append(entry)
            else:
                blocks.append([entry])
        return blocks

    @staticmethod
    def _dominant_holiday_name(block: list[tuple[pd.Timestamp, str]]) -> str:
        """用区间内出现天数最多的节日命名，保证跨节日长假的名称稳定。"""
        names = [name for _, name in block]
        return max(dict.fromkeys(names), key=names.count)

    def _long_holiday_windows(self) -> list[tuple[pd.Timestamp, pd.Timestamp, str]]:
        """返回长假影响区间：假期开始前2天至假期结束后2天。"""
        delta_before = pd.Timedelta(days=self.LONG_HOLIDAY_LEAD_DAYS)
        delta_after = pd.Timedelta(days=self.LONG_HOLIDAY_LAG_DAYS)
        return [
            (
                block[0][0] - delta_before,
                block[-1][0] + delta_after,
                self._dominant_holiday_name(block),
            )
            for block in self._holiday_blocks()
            if len(block) >= self.LONG_HOLIDAY_MIN_DAYS
        ]

    @staticmethod
    def _days_since(date: pd.Timestamp, candidates: pd.DatetimeIndex) -> int:
        previous = candidates[candidates < date]
        return int((date - previous.max()).days) if len(previous) else 365

    @staticmethod
    def _parse_info(info: str | None) -> tuple[str | None, str | None, int]:
        if info is None:
            return None, None, 0
        parts = info.split(",")
        if len(parts) != 3:
            raise ValueError(f"无效的节假日记录: {info}")
        return parts[0], parts[1], int(parts[2])

    @staticmethod
    def _validate_data(data: dict) -> None:
        required = {"schema_version", "country", "coverage", "source", "holidays", "workdays"}
        missing = required.difference(data)
        if missing:
            raise ValueError(f"节假日数据缺少字段: {', '.join(sorted(missing))}")
        if data["country"] != "CN":
            raise ValueError("当前节假日提供器只支持CN数据")
        overlap = set(data["holidays"]).intersection(data["workdays"])
        if overlap:
            raise ValueError(f"日期不能同时为放假和补班: {', '.join(sorted(overlap))}")
