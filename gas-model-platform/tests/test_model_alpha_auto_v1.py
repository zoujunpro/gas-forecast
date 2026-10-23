from types import SimpleNamespace

import pandas as pd
import pytest

from gas_model_platform.models.alpha_model import ModelAlphaAutoV1Handler
from gas_model_platform.models.alpha_model import handler as module
from gas_model_platform.models.alpha_model.auto_engine import AutoTrainingOutput
from gas_model_platform.models.registry import registry
from gas_model_platform.schemas.modeling import ModelContext


def _dataset(days: int = 40) -> list[dict]:
    dates = pd.date_range("2026-01-01", periods=days, freq="D")
    return [
        {"date": date.date(), "gas_sales": 100.0 + index % 7}
        for index, date in enumerate(dates)
    ]


def _output() -> AutoTrainingOutput:
    frame = pd.DataFrame(_dataset())
    train = frame.iloc[:24].copy()
    validation = frame.iloc[24:30].copy()
    test = frame.iloc[30:].copy()
    predictions = [float(value) + 1.0 for value in test["gas_sales"]]
    metrics = {"rmse": 1.0, "mae": 1.0, "mape": 0.98, "smape": 0.97, "r2": 0.8}
    return AutoTrainingOutput(
        history=frame, train=train, validation=validation, test=test,
        best_model="naive", tuned_params={},
        candidate_metrics={"naive": metrics}, test_metrics=metrics,
        test_predictions=predictions, issues=[], outlier_bounds={"lower": 90, "upper": 110},
    )


def test_alpha_auto_model_is_registered() -> None:
    handler = registry.resolve("MODEL_ALPHA_AUTO_V1.0")
    assert isinstance(handler, ModelAlphaAutoV1Handler)
    assert handler.info.agent_code == "short-term"
    assert handler.info.training_data_range.minimum == 365


def test_alpha_auto_validates_daily_history() -> None:
    result = ModelAlphaAutoV1Handler().validate_training_data(
        ModelContext(model_code="MODEL_ALPHA_AUTO_V1.0", dataset=_dataset(365))
    )
    assert result.valid is True
    assert result.summary["row_count"] == 365
    assert {warning.code for warning in result.warnings} == {"HISTORY_BELOW_RECOMMENDED"}


def test_alpha_auto_rejects_missing_day() -> None:
    rows = _dataset(365)
    del rows[10]
    result = ModelAlphaAutoV1Handler().validate_training_data(
        ModelContext(model_code="MODEL_ALPHA_AUTO_V1.0", dataset=rows)
    )
    assert result.valid is False
    assert "NON_CONTINUOUS_DATES" in {issue.code for issue in result.errors}


def test_alpha_auto_train_returns_platform_result(monkeypatch, tmp_path) -> None:
    monkeypatch.setattr(module, "settings", SimpleNamespace(artifact_root=tmp_path))
    monkeypatch.setattr(module, "train_auto", lambda *args, **kwargs: _output())
    result = ModelAlphaAutoV1Handler().train(ModelContext(
        model_code="MODEL_ALPHA_AUTO_V1.0",
        train_batch_no="TRBATCH-alpha-test",
        params={"candidate_models": ["naive"]},
        dataset=_dataset(),
    ))
    assert result.selected_model_name == "naive"
    assert result.metrics.rmse == 1.0
    assert len(result.rolling_backtest_results) == 10
    assert len(result.metadata["artifact_sha256"]) == 64
    assert (tmp_path / "TRBATCH-alpha-test" / "model.joblib").exists()


def test_alpha_auto_does_not_overwrite_batch(monkeypatch, tmp_path) -> None:
    monkeypatch.setattr(module, "settings", SimpleNamespace(artifact_root=tmp_path))
    monkeypatch.setattr(module, "train_auto", lambda *args, **kwargs: _output())
    context = ModelContext(
        model_code="MODEL_ALPHA_AUTO_V1.0", train_batch_no="TRBATCH-alpha-test",
        params={"candidate_models": ["naive"]}, dataset=_dataset(),
    )
    handler = ModelAlphaAutoV1Handler()
    handler.train(context)
    with pytest.raises(ValueError, match="禁止覆盖"):
        handler.train(context)


def test_alpha_auto_predict_uses_requested_dates(monkeypatch, tmp_path) -> None:
    monkeypatch.setattr(module, "settings", SimpleNamespace(artifact_root=tmp_path))
    handler = ModelAlphaAutoV1Handler()
    artifact = module.AlphaAutoArtifact(
        model_code=handler.info.model_code,
        model_version=handler.info.model_version,
        artifact_version=module.ARTIFACT_VERSION,
        history=pd.DataFrame(_dataset()),
        selected_model_name="naive", selected_model_params={}, metrics={}, backtest_rows=[],
    )
    path = handler._artifact_path("TRBATCH-alpha-test")
    path.parent.mkdir(parents=True)
    module.joblib.dump(artifact, path)
    monkeypatch.setattr(module, "forecast_auto", lambda *args, **kwargs: pd.DataFrame({
        "date": pd.date_range("2026-02-10", periods=3, freq="D"),
        "gas_sales": [101.0, 102.0, 103.0],
        "gas_sales_lower": [99.0, 100.0, 101.0],
        "gas_sales_upper": [103.0, 104.0, 105.0],
    }))
    result = handler.predict(ModelContext(
        model_code=handler.info.model_code,
        train_batch_no="TRBATCH-alpha-test",
        forecast_batch_no="FORECAST-alpha-test",
        forecast_horizon=3,
        forecast_unit="day",
        dataset=[{"date": "2026-02-10"}, {"date": "2026-02-11"}, {"date": "2026-02-12"}],
    ))
    assert result.forecast_batch_no == "FORECAST-alpha-test"
    assert [point.prediction for point in result.points] == [101.0, 102.0, 103.0]


def test_alpha_auto_rejects_incompatible_artifact(monkeypatch, tmp_path) -> None:
    monkeypatch.setattr(module, "settings", SimpleNamespace(artifact_root=tmp_path))
    handler = ModelAlphaAutoV1Handler()
    path = handler._artifact_path("TRBATCH-alpha-test")
    path.parent.mkdir(parents=True)
    module.joblib.dump({"wrong": "artifact"}, path)
    with pytest.raises(ValueError, match="产物类型"):
        handler._load_artifact("TRBATCH-alpha-test")
