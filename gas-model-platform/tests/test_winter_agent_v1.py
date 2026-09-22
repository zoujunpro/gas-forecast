import warnings

import numpy as np
import pandas as pd
import pytest
from fastapi import HTTPException

from gas_model_platform.api.routes import predict
from gas_model_platform.core.config import settings
from gas_model_platform.models.registry import ModelRegistry
from gas_model_platform.models.winter_agent.winter_agent_v1.engine import (
    backtest as backtest_module,
)
from gas_model_platform.models.winter_agent.winter_agent_v1.engine.issues import (
    localize_issue_message,
)
from gas_model_platform.models.winter_agent.winter_agent_v1.engine.model_zoo import (
    ModelSpec,
)
from gas_model_platform.models.winter_agent.winter_agent_v1.handler import (
    WinterAgentV1Handler,
)
from gas_model_platform.schemas.modeling import (
    ForecastPoint,
    ModelContext,
    ModelIssue,
    PredictRequest,
    PredictResult,
    TrainRequest,
    TrainResult,
)


def test_train_request_requires_training_batch() -> None:
    with pytest.raises(ValueError, match="train_batch_no"):
        TrainRequest(
            model_code="WINTER_MODEL_V1.0",
            dataset=[{"date": "2020-01-01", "gas_sales": 1.0}],
        )


def test_train_request_rejects_legacy_model_code_name() -> None:
    with pytest.raises(ValueError, match="model_code"):
        TrainRequest(
            modelCode="WINTER_MODEL_V1.0",
            train_batch_no="T00001",
            dataset=[{"date": "2020-01-01", "gas_sales": 1.0}],
        )


def test_model_code_is_used_as_direct_registry_key() -> None:
    request = TrainRequest(
        model_code="WINTER_MODEL_V1.0",
        train_batch_no="T00001",
        dataset=[{"date": "2020-01-01", "gas_sales": 1.0}],
    )

    assert request.agent_code is None
    assert request.model_code == "WINTER_MODEL_V1.0"
    assert WinterAgentV1Handler()._safe_batch_no(request.train_batch_no) == "T00001"
    assert WinterAgentV1Handler()._province(request) is None


def test_winter_training_data_validation_reports_complete_seasons() -> None:
    handler = WinterAgentV1Handler()
    result = handler.validate_training_data(
        ModelContext(
            agent_code="winter-supply",
            model_code=handler.info.model_code,
            dataset=[{"date": "2025-11-01", "gas_sales": 100.0}],
        )
    )

    assert result.valid is False
    assert result.summary["complete_winter_seasons"] == 0
    assert result.errors[0].code == "INSUFFICIENT_COMPLETE_PERIODS"


def test_train_dataset_row_has_fixed_fields_and_keeps_model_features() -> None:
    request = TrainRequest(
        model_code="MODEL_JIANGSHU_DIANLI_V1.0",
        train_batch_no="T00001",
        dataset=[
            {
                "date": "2026-01-01",
                "gas_sales": 100.0,
                "任意外部特征": 20.0,
            }
        ],
    )

    row = request.dataset[0].model_dump()
    assert row["date"].isoformat() == "2026-01-01"
    assert row["gas_sales"] == 100.0
    assert row["任意外部特征"] == 20.0


def test_predict_dataset_row_has_fixed_date_and_keeps_model_features() -> None:
    request = PredictRequest(
        model_code="MODEL_JIANGSHU_DIANLI_V1.0",
        train_batch_no="T00001",
        forecast_batch_no="F00001",
        forecast_horizon=1,
        forecast_unit="day",
        dataset=[{"date": "2026-01-02", "未来天气": 18.5}],
    )

    row = request.dataset[0].model_dump()
    assert row["date"].isoformat() == "2026-01-02"
    assert row["未来天气"] == 18.5


def test_predict_request_requires_training_batch() -> None:
    with pytest.raises(ValueError, match="train_batch_no"):
        PredictRequest(
            model_code="WINTER_MODEL_V1.0",
            region_name="河北",
            forecast_unit="tenday",
            forecast_horizon=2,
            dataset=[{"date": "2026-11-01"}, {"date": "2026-11-11"}],
        )


def test_predict_request_requires_forecast_batch() -> None:
    with pytest.raises(ValueError, match="forecast_batch_no"):
        PredictRequest(
            model_code="WINTER_MODEL_V1.0",
            train_batch_no="WGTRAIN-test",
            forecast_unit="tenday",
            forecast_horizon=1,
            dataset=[{"date": "2026-11-01"}],
        )


def test_predict_result_does_not_include_metrics() -> None:
    result = PredictResult(
        agent_code="short-term",
        model_code="MODEL_JIANGSHU_DIANLI_V1.0",
        forecast_batch_no="JS-FC-001",
        points=[ForecastPoint(forecast_date="2026-05-26", prediction=100.0)],
    )

    assert "metrics" not in result.model_dump()


def test_predict_rejects_training_batch_inside_params() -> None:
    with pytest.raises(HTTPException) as raised:
        predict(
            PredictRequest(
                model_code="WINTER_MODEL_V1.0",
                train_batch_no="WGTRAIN-top-level",
                forecast_batch_no="WGFC-test",
                region_name="河北",
                forecast_unit="tenday",
                forecast_horizon=1,
                params={"train_batch_no": "WGTRAIN-inside-params"},
                dataset=[{"date": "2026-11-01"}],
            )
        )

    assert raised.value.status_code == 422
    assert "顶层 train_batch_no" in raised.value.detail


def test_registry_rejects_duplicate_model_registration() -> None:
    model_registry = ModelRegistry()
    model_registry.register("WINTER_MODEL_V1.0", WinterAgentV1Handler())

    with pytest.raises(ValueError, match="already registered"):
        model_registry.register("WINTER_MODEL_V1.0", WinterAgentV1Handler())


def test_training_config_accepts_profile_and_model_names() -> None:
    config = WinterAgentV1Handler()._training_config(
        {
            "profile": "smoke",
            "model_names": ["SeasonalNaive36"],
        }
    )

    assert config["profile"] == "smoke"
    assert config["backtest_seasons"] == 3
    assert config["model_names"] == ["SeasonalNaive36"]


def test_predict_requires_training_batch_instead_of_artifact_path() -> None:
    with pytest.raises(HTTPException) as raised:
        predict(
            PredictRequest(
                model_code="WINTER_MODEL_V1.0",
                train_batch_no="WGTRAIN-test",
                forecast_batch_no="WGFC-test",
                forecast_unit="tenday",
                forecast_horizon=1,
                params={"artifact_path": "/tmp/untrusted.joblib"},
                dataset=[{"date": "2026-11-01"}],
            )
        )

    assert raised.value.status_code == 422
    assert "train_batch_no" in raised.value.detail
    assert "artifact_path 不再支持" in raised.value.detail


def test_artifact_path_is_created_under_platform_store() -> None:
    path = WinterAgentV1Handler()._artifact_path("WGTRAIN-test")

    assert path == settings.artifact_root / "WGTRAIN-test/model.joblib"


def test_artifact_path_rejects_unsafe_training_batch() -> None:
    with pytest.raises(ValueError, match="train_batch_no"):
        WinterAgentV1Handler()._artifact_path(
            "../other-model",
        )


def test_predict_locates_artifact_by_training_batch() -> None:
    with pytest.raises(HTTPException) as raised:
        predict(
            PredictRequest(
                model_code="WINTER_MODEL_V1.0",
                region_code="beijing",
                region_name="北京",
                train_batch_no="WGTRAIN-not-found",
                forecast_batch_no="WGFC-test",
                forecast_unit="tenday",
                forecast_horizon=1,
                dataset=[{"date": "2026-11-01"}],
            )
        )

    assert raised.value.status_code == 404
    assert "WGTRAIN-not-found/model.joblib" in raised.value.detail


def test_winter_agent_v1_exposes_model_version() -> None:
    handler = WinterAgentV1Handler()

    assert handler.info.model_version == "1.0.0"
    assert handler.info.model_code == "WINTER_MODEL_V1.0"


def test_candidate_evaluations_expose_ranking_and_metrics() -> None:
    evaluations = WinterAgentV1Handler()._candidate_evaluations(
        [
            {
                "rank": 1,
                "model": "Ridge",
                "type": "ml",
                "MAPE": 1.2,
                "WMAPE": 1.1,
                "sMAPE": 1.19,
                "RMSE": 10.0,
                "MAE": 8.0,
                "R2": 0.98,
                "constituents": "",
            }
        ],
        "Ridge",
    )

    assert evaluations[0].selected is True
    assert evaluations[0].metrics.r2 == 0.98
    assert evaluations[0].metrics.smape == 1.19


def test_training_response_exposes_rolling_backtest_comparison() -> None:
    handler = WinterAgentV1Handler()
    points = handler._rolling_backtest_results(
        [
            {
                "model": "Ridge",
                "season": "2024-2025",
                "date": "2024-11-01T00:00:00.000",
                "actual": 100.0,
                "prediction": 92.0,
            }
        ]
    )
    folds = handler._rolling_backtest_fold_metrics(
        [
            {
                "model": "Ridge",
                "season": "2024-2025",
                "stage": "evaluation",
                "MAPE": 8.0,
                "RMSE": 8.0,
                "R2": 0.95,
            }
        ]
    )

    assert points[0].error_value == -8.0
    assert points[0].absolute_error == 8.0
    assert points[0].error_rate == 8.0
    assert folds[0].metrics.mape == 8.0
    assert folds[0].metrics.r2 == 0.95


def test_candidate_warning_is_returned_as_structured_issue(monkeypatch) -> None:
    dates = pd.date_range("2025-11-01", periods=15, freq="10D")
    frame = pd.DataFrame({"date": dates, "gas_sales": np.arange(15) + 1.0})
    folds = [{"season": "2025-2026", "dates": dates}]

    def forecast_with_warning(*args, **kwargs):
        warnings.warn("Maximum Likelihood optimization failed", UserWarning)
        return np.arange(15) + 1.0

    monkeypatch.setattr(backtest_module, "forecast_candidate", forecast_with_warning)

    _, _, _, issues = backtest_module.evaluate_candidate(
        ModelSpec("WarningModel", "stat"),
        frame,
        folds,
        [],
    )

    assert issues.to_dict(orient="records") == [
        {
            "severity": "warning",
            "stage": "backtest",
            "model_name": "WarningModel",
            "season": "2025-2026",
            "category": "UserWarning",
            "message": "Maximum Likelihood optimization failed",
        }
    ]


def test_train_result_exposes_issues_at_top_level() -> None:
    result = TrainResult(
        agent_code="winter-supply",
        model_code="WINTER_MODEL_V1.0",
        train_batch_no="WGTRAIN-test",
        issues=[
            ModelIssue(
                severity="warning",
                stage="evaluation_backtest",
                model_name="SARIMAX",
                season="2024-2025",
                category="ConvergenceWarning",
                message="Maximum Likelihood optimization failed to converge.",
            )
        ],
    )

    assert result.model_dump()["issues"][0]["category"] == "ConvergenceWarning"


def test_convergence_warning_is_localized_to_chinese() -> None:
    message = localize_issue_message(
        "ConvergenceWarning",
        "Objective did not converge. You might want to increase the number of "
        "iterations, check the scale of the features or consider increasing "
        "regularisation. Duality gap: 1.280423e+08, tolerance: 2.152e+07",
    )

    assert message == (
        "模型优化未收敛。建议增加迭代次数、检查特征量纲，或增强正则化"
        "（对偶间隙：1.280423e+08，容差：2.152e+07）。"
    )
