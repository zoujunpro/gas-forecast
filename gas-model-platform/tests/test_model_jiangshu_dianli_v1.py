import numpy as np
import pandas as pd
import pytest

from gas_model_platform.core.config import settings
from gas_model_platform.models.registry import registry
from gas_model_platform.models.short_agent.model_jiangshu_dianli_v1 import (
    JiangshuDianliV1Artifact,
    ModelJiangshuDianliV1Handler,
    predict_with_artifact,
    prepare_future_data,
    prepare_training_data,
    train_model,
)
from gas_model_platform.models.short_agent import model_jiangshu_dianli_v1 as short_engine


class FakeProphet:
    def __init__(self, **kwargs) -> None:
        self.kwargs = kwargs

    def fit(self, frame: pd.DataFrame):
        self.level = float(frame["y"].mean())
        return self

    def predict(self, frame: pd.DataFrame) -> pd.DataFrame:
        count = len(frame)
        level = getattr(self, "level", 100.0)
        return pd.DataFrame(
            {
                "ds": frame["ds"],
                "yhat": np.full(count, level),
                "trend": np.full(count, level),
                "weekly": np.zeros(count),
                "yearly": np.zeros(count),
                "additive_terms": np.zeros(count),
            }
        )


class FakeResidualModel:
    def __init__(self, **kwargs) -> None:
        self.kwargs = kwargs

    def fit(self, frame: pd.DataFrame, target, **kwargs):
        return self

    def predict(self, frame: pd.DataFrame) -> np.ndarray:
        return np.zeros(len(frame))


def test_jiangshu_dianli_v1_is_registered_by_model_code() -> None:
    handler = registry.resolve("MODEL_JIANGSHU_DIANLI_V1")

    assert isinstance(handler, ModelJiangshuDianliV1Handler)
    assert handler.info.agent_code == "short-term"


def test_jiangshu_dianli_v1_training_aliases_are_normalized() -> None:
    dates = pd.date_range("2025-01-01", periods=150, freq="D")
    frame = pd.DataFrame({"日期": dates, "发电量": np.arange(150) + 100.0})

    clean = prepare_training_data(frame)

    assert list(clean.columns) == ["ds", "y"]
    assert len(clean) == 150


def test_jiangshu_dianli_v1_future_date_alias_is_supported() -> None:
    future = prepare_future_data(pd.DataFrame({"statDate": ["2026-01-01"]}))

    assert future.loc[0, "ds"] == pd.Timestamp("2026-01-01")


def test_jiangshu_dianli_v1_keeps_artifacts_by_batch_only() -> None:
    path = ModelJiangshuDianliV1Handler()._artifact_path("T00001")

    assert path == settings.artifact_root / "T00001/model.joblib"


def test_jiangshu_dianli_v1_prediction_is_recursive_and_daily() -> None:
    history_dates = pd.date_range("2025-01-01", periods=40, freq="D")
    feature_names = [
        "day_of_week", "month", "day_of_year", "is_weekend", "quarter", "t",
        *[f"weekly_{kind}_{index}" for index in range(1, 5) for kind in ("sin", "cos")],
        *[f"yearly_{kind}_{index}" for index in range(1, 6) for kind in ("sin", "cos")],
        "clim_mean", "clim_std", *list({"lag1": 1, "lag2": 2, "lag3": 3, "lag7": 7, "lag14": 14, "lag30": 30}),
        "roll_mean_7", "roll_mean_14", "roll_mean_30", "roll_std_7", "roll_std_14",
        "diff1", "diff7", "prophet_yhat", "prophet_trend", "prophet_weekly",
        "prophet_yearly", "prophet_add",
    ]
    artifact = JiangshuDianliV1Artifact(
        prophet_model=FakeProphet(),
        residual_model=FakeResidualModel(),
        feature_names=feature_names,
        selected_params={},
        history_dates=[str(value.date()) for value in history_dates],
        history_values=[100.0] * len(history_dates),
        climatology_mean={},
        climatology_std={},
        climatology_fallback_mean=100.0,
        climatology_fallback_std=1.0,
        start_date="2025-01-01",
        train_start="2025-01-01",
        train_end="2025-02-09",
        residual_lower=-5.0,
        residual_upper=5.0,
    )

    result = predict_with_artifact(
        artifact,
        pd.DataFrame({"date": ["2025-02-10", "2025-02-11"]}),
    )

    assert [row["prediction"] for row in result] == [100.0, 100.0]
    assert result[0]["lower"] == 95.0


def test_jiangshu_dianli_v1_rejects_non_continuous_future_dates() -> None:
    history_dates = pd.date_range("2025-01-01", periods=40, freq="D")
    artifact = JiangshuDianliV1Artifact(
        prophet_model=FakeProphet(), residual_model=FakeResidualModel(), feature_names=[],
        selected_params={}, history_dates=[str(value.date()) for value in history_dates],
        history_values=[100.0] * 40, climatology_mean={}, climatology_std={},
        climatology_fallback_mean=100.0, climatology_fallback_std=1.0,
        start_date="2025-01-01", train_start="2025-01-01", train_end="2025-02-09",
        residual_lower=-5.0, residual_upper=5.0,
    )

    with pytest.raises(ValueError, match="按天连续"):
        predict_with_artifact(
            artifact,
            pd.DataFrame({"date": ["2025-02-10", "2025-02-12"]}),
        )


def test_jiangshu_dianli_v1_smoke_training_pipeline(monkeypatch) -> None:
    monkeypatch.setattr(short_engine, "_prophet_class", lambda: FakeProphet)
    monkeypatch.setattr(short_engine, "LGBMRegressor", FakeResidualModel)
    dates = pd.date_range("2025-01-01", periods=880, freq="D")
    values = 100 + np.sin(np.arange(880) * 2 * np.pi / 7) * 10

    output = train_model(
        pd.DataFrame({"date": dates, "y": values}),
    )

    assert output.artifact.train_end == str(dates[-1].date())
    assert len(output.backtest_rows) == 120
    assert len(output.evolution_history) == 4
    assert len(output.candidate_evaluations) == 16
    assert output.n_folds == 1
    assert output.metrics["mape"] >= 0
