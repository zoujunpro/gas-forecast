import pandas as pd
import pytest

from gas_model_engine import AlgorithmDataset
from gas_model_engine.features import ConfiguredFeatureProcessor, FeaturePolicyResolver


def test_short_term_policy_resolves_common_and_available_industry_features():
    plan = FeaturePolicyResolver().resolve(
        "SHORT_TERM_CUSTOMER_FEATURES_V1",
        {"industry_name": "CHEMICAL", "industry_code": "CHEMICAL"},
        ["temperature", "production_plan"],
        ["temperature", "production_plan"],
    )

    selected = {item["code"] for item in plan.selected_features}
    assert {"CALENDAR_V1", "HOLIDAY_CN_V1", "PRODUCTION_PLAN_V1"} <= selected
    assert {"LAG_V1", "TARGET_CHANGE_V1"} <= selected
    assert "ROLLING_V1" not in selected
    assert any(item["code"] == "MAINTENANCE_STATUS_V1" for item in plan.skipped_features)
    dynamic = next(spec for spec in plan.specs if spec.code == "DYNAMIC_PASSTHROUGH_V1")
    assert dynamic.params["required_columns"] == ["production_plan"]


def test_policy_validates_required_context_and_required_input_columns():
    resolver = FeaturePolicyResolver()
    with pytest.raises(ValueError, match="region_code"):
        resolver.resolve("WINTER_SUPPLY_FEATURES_V1", {}, ["temperature"], ["temperature"])
    with pytest.raises(ValueError, match="temperature"):
        resolver.resolve(
            "WINTER_SUPPLY_FEATURES_V1",
            {"region_code": "JIANGSU"},
            ["humidity"],
            ["humidity"],
        )


def test_city_gas_policy_generates_configured_weather_calendar_and_holiday_features():
    resolver = FeaturePolicyResolver()
    columns = ["temp", "tempmax", "tempmin", "humidity"]
    plan = resolver.resolve(
        "SHORT_TERM_CUSTOMER_FEATURES_V1",
        {"industry_name": "城燃"},
        columns,
        columns,
    )
    history = AlgorithmDataset(
        features=pd.DataFrame(
            {
                "temp": [10.0, 12.0, 14.0],
                "tempmax": [15.0, 16.0, 17.0],
                "tempmin": [5.0, 7.0, 9.0],
                "humidity": [70.0, 60.0, 50.0],
            }
        ),
        dates=pd.date_range("2026-02-14", periods=3),
        target=pd.Series([100.0, 110.0, 120.0]),
    )
    future = AlgorithmDataset(
        features=pd.DataFrame(
            {
                "temp": [16.0],
                "tempmax": [20.0],
                "tempmin": [10.0],
                "humidity": [40.0],
            }
        ),
        dates=pd.to_datetime(["2026-02-17"]),
    )

    train, predicted = ConfiguredFeatureProcessor().prepare(history, future, plan.specs)

    assert predicted.features.iloc[0]["hdd"] == 2.0
    assert predicted.features.iloc[0]["hdd_roll3"] == 4.0
    assert predicted.features.iloc[0]["temp_roll7"] == 13.0
    assert predicted.features.iloc[0]["is_holiday"] == 1
    assert predicted.features.iloc[0]["is_workday"] == 0
    assert {"dow", "month", "t", "is_weekend"} <= set(train.features.columns)
