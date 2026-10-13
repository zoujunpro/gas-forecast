from types import SimpleNamespace

import pandas as pd
import pytest

from gas_model_platform.models.registry import registry
from gas_model_platform.models.short_agent import (
    ModelChemicalGasV1Handler,
    ModelCngV1Handler,
    ModelIndustrialFuelV1Handler,
)
from gas_model_platform.models.short_agent import model_sector_gas_xgb_v1 as module
from gas_model_platform.schemas.modeling import ModelContext


MODEL_CASES = [
    ("MODEL_CNG_V1.0", ModelCngV1Handler, "CNG"),
    ("MODEL_CHEMICAL_GAS_V1.0", ModelChemicalGasV1Handler, "化工"),
    ("MODEL_INDUSTRIAL_FUEL_V1.0", ModelIndustrialFuelV1Handler, "工业燃料"),
]


def _dataset(days: int = 200) -> list[dict]:
    dates = pd.date_range("2025-01-01", periods=days, freq="D")
    return [
        {
            "date": value.date(),
            "gas_sales": 100.0 + index % 20,
            "temp": 10.0 + index % 15,
            "humidity": 60.0,
        }
        for index, value in enumerate(dates)
    ]


@pytest.mark.parametrize("model_code,handler_type,industry", MODEL_CASES)
def test_sector_models_are_registered(model_code, handler_type, industry) -> None:
    handler = registry.resolve(model_code)

    assert isinstance(handler, handler_type)
    assert industry in handler.info.description
    assert handler.info.training_data_range.minimum == 180


@pytest.mark.parametrize("_,handler_type,__", MODEL_CASES)
def test_sector_models_validate_continuous_daily_history(_, handler_type, __) -> None:
    handler = handler_type()
    result = handler.validate_training_data(
        ModelContext(model_code=handler.info.model_code, dataset=_dataset())
    )

    assert result.valid is True
    assert result.summary["row_count"] == 200
    assert result.summary["missing_date_count"] == 0


def test_sector_model_reports_missing_dates() -> None:
    handler = ModelCngV1Handler()
    rows = _dataset()
    del rows[50]

    result = handler.validate_training_data(
        ModelContext(model_code=handler.info.model_code, dataset=rows)
    )

    assert result.valid is False
    assert {error.code for error in result.errors} == {"NON_CONTINUOUS_DATES"}


def test_sector_training_returns_standard_result(monkeypatch, tmp_path) -> None:
    handler = ModelChemicalGasV1Handler()
    artifact = module.SectorGasXgbArtifact(
        model_code=handler.info.model_code,
        prophet_model={"kind": "test"},
        xgb_model={"kind": "test"},
        regressors=["temp", "humidity", "hdd", "cdd"],
        xgb_feature_names=["prophet_yhat", "temp", "y_lag1"],
        history_dates=["2025-01-01"],
        history_values=[100.0],
        selected_params={"cps": 0.05},
        residual_lower=-2.0,
        residual_upper=2.0,
        train_start="2025-01-01",
        train_end="2025-07-19",
    )
    output = module.SectorTrainingOutput(
        artifact=artifact,
        metrics={"rmse": 2.0, "mae": 1.5, "mape": 1.2, "wmape": 1.1, "r2": 0.8},
        backtest_rows=[{"date": "2025-07-19", "actual": 100.0, "prediction": 102.0}],
        candidates=[
            {
                "params": {"cps": 0.05},
                "metrics": {"rmse": 2.0, "mae": 1.5, "mape": 1.2, "wmape": 1.1, "r2": 0.8},
            }
        ],
        selected_params={"cps": 0.05},
    )
    monkeypatch.setattr(module, "settings", SimpleNamespace(artifact_root=tmp_path))
    monkeypatch.setattr(module, "train_sector_model", lambda frame, model_code: output)

    result = handler.train(
        ModelContext(
            model_code=handler.info.model_code,
            train_batch_no="TRBATCH-sector-test",
            dataset=_dataset(),
        )
    )

    assert result.selected_model_name == "Prophet+XGBoost"
    assert result.metrics.rmse == 2.0
    assert len(result.candidate_evaluations) == 1
    assert len(result.rolling_backtest_results) == 1
    assert (tmp_path / "TRBATCH-sector-test/model.joblib").exists()


def test_sector_artifact_cannot_be_used_by_another_model(monkeypatch, tmp_path) -> None:
    monkeypatch.setattr(module, "settings", SimpleNamespace(artifact_root=tmp_path))
    cng = ModelCngV1Handler()
    chemical = ModelChemicalGasV1Handler()
    path = cng._artifact_path("TRBATCH-sector-test")
    path.parent.mkdir(parents=True)
    module.joblib.dump(
        module.SectorGasXgbArtifact(
            model_code=cng.info.model_code,
            prophet_model={}, xgb_model={}, regressors=[], xgb_feature_names=[],
            history_dates=[], history_values=[], selected_params={},
            residual_lower=0.0, residual_upper=0.0,
            train_start="2025-01-01", train_end="2025-01-02",
        ),
        path,
    )

    with pytest.raises(ValueError, match="模型类型"):
        chemical._load_artifact("TRBATCH-sector-test")
