import pandas as pd

from gas_model_engine.calendar import LocalHolidayProvider


def test_local_holiday_provider_covers_2004_to_2026():
    provider = LocalHolidayProvider()

    assert provider.data["coverage"] == {"start_year": 2004, "end_year": 2026}
    assert provider.data["source"]["version"] == "1.5.9"


def test_local_holiday_provider_distinguishes_holiday_and_makeup_workday():
    calendar = LocalHolidayProvider().build_calendar(
        pd.to_datetime(["2026-02-17", "2026-02-28", "2026-03-02", "2027-01-01"])
    )

    spring_festival = calendar.iloc[0]
    assert spring_festival["is_holiday"] == 1
    assert spring_festival["holiday_name_zh"] == "春节"
    assert spring_festival["is_workday"] == 0

    makeup_workday = calendar.iloc[1]
    assert makeup_workday["is_makeup_workday"] == 1
    assert makeup_workday["is_workday"] == 1

    assert calendar.iloc[2]["is_workday"] == 1
    assert calendar.iloc[3]["holiday_calendar_confirmed"] == 0
