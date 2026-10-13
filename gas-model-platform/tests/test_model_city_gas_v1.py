import pandas as pd
from types import SimpleNamespace

from gas_model_platform.models.registry import registry
from gas_model_platform.models.short_agent import ModelCityGasV1Handler
from gas_model_platform.models.short_agent import model_city_gas_v1
from gas_model_platform.models.short_agent import city_gas_v3_engine
from gas_model_platform.schemas.modeling import ModelContext


def _context() -> ModelContext:
    dates = pd.date_range("2023-01-01", periods=880, freq="D")
    return ModelContext(
        agent_code="short-term",
        model_code="MODEL_CITY_GAS_V1.0",
        region_code="REG-ZHEJIANG",
        region_name="浙江",
        industry_code="CITY_GAS",
        industry_name="城市燃气",
        customer_code="CUS-HANGZHOU-GAS",
        customer_name="杭州城市燃气客户",
        dataset=[
            {
                "date": value.date(),
                "gas_sales": 100.0 + index,
                "temp": 10.0 + index % 20,
                "tempmax": 15.0 + index % 20,
                "tempmin": 5.0 + index % 20,
                "humidity": 60.0,
            }
            for index, value in enumerate(dates)
        ],
    )


def test_city_gas_model_is_registered_with_supported_scope() -> None:
    handler = registry.resolve("MODEL_CITY_GAS_V1.0")

    assert isinstance(handler, ModelCityGasV1Handler)
    assert "城市燃气行业" in handler.info.description
    assert "任意地区和任意客户" in handler.info.description


def test_city_gas_model_accepts_any_region_and_customer() -> None:
    result = ModelCityGasV1Handler().validate_training_data(_context())

    assert result.valid is True
    assert result.model_code == "MODEL_CITY_GAS_V1.0"


def test_city_gas_model_builds_weather_features() -> None:
    frame, regressors = ModelCityGasV1Handler._prepare(_context().dataset)

    assert {"hdd", "cdd", "hdd_roll3", "temp_range", "humidity_roll3"} <= set(regressors)
    assert frame.iloc[0]["ds"] == pd.Timestamp("2022-12-31")


def test_city_gas_prediction_uses_requested_dates(monkeypatch) -> None:
    history = pd.DataFrame({"ds": [pd.Timestamp("2025-12-26")], "y": [100.0]})
    artifact = SimpleNamespace(history=history, regressors=[], selected_version="v1")
    monkeypatch.setattr(model_city_gas_v1.joblib, "load", lambda _: artifact)

    def fake_forecast(frame, regressors, periods, frequency, params, ar_order):
        assert periods == 34
        dates = pd.date_range("2025-12-27", periods=periods, freq="D")
        return pd.DataFrame({
            "ds": dates, "y": [float("nan")] * periods,
            "yhat": range(periods), "yhat_lower": range(periods),
            "yhat_upper": range(periods),
        }), None, None

    monkeypatch.setattr(model_city_gas_v1.engine, "run_single_forecast_v1", fake_forecast)
    requested = pd.date_range("2026-01-01", periods=30, freq="D")
    context = ModelContext(
        model_code="MODEL_CITY_GAS_V1.0", train_batch_no="BATCH-1",
        forecast_batch_no="FORECAST-1", forecast_horizon=30, forecast_unit="day",
        dataset=[{"date": value.date()} for value in requested],
    )

    result = ModelCityGasV1Handler().predict(context)

    assert [point.forecast_date for point in result.points] == [value.date() for value in requested]


def test_rolling_backtest_accepts_exact_minimum_140_days(monkeypatch) -> None:
    dates = pd.date_range("2026-05-14", periods=140, freq="D")
    frame = pd.DataFrame({"ds": dates, "y": range(140)})

    def fake_forecast(df, regs, periods, freq, prophet_params, future_regs_df, ar_order):
        future_dates = pd.date_range(df["ds"].max() + pd.Timedelta(days=1), periods=periods, freq=freq)
        future = pd.DataFrame({
            "ds": future_dates,
            "y": [float("nan")] * periods,
            "yhat": range(periods),
            "yhat_lower": range(periods),
            "yhat_upper": range(periods),
            "yhat_base": range(periods),
            "resid_hat": [0.0] * periods,
        })
        return pd.concat([df.assign(yhat=float("nan")), future], ignore_index=True), None, None

    monkeypatch.setattr(city_gas_v3_engine, "run_single_forecast_v1", fake_forecast)
    points, _, _ = city_gas_v3_engine.rolling_backtest(
        frame, [], horizon=50, freq="D", min_train_days=90, stride=50,
        eval_start=dates[90], eval_end=dates[-1], version="v1",
    )

    assert len(points) == 50
    assert points["ds"].min() == dates[90]
    assert points["ds"].max() == dates[-1]
