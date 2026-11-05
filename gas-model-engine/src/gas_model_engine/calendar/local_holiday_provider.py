from __future__ import annotations

import json
from importlib.resources import files
from pathlib import Path

import pandas as pd


class LocalHolidayProvider:
    """读取项目内维护的中国法定节假日和调休补班数据。"""

    DEFAULT_RESOURCE = "config/holidays/cn-2004-2026.json"

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
        return pd.DataFrame(rows)

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
