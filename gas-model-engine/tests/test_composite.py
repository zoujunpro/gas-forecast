import numpy as np
import pandas as pd

from gas_model_engine import (
    AlgorithmDataset,
    AlgorithmModel,
    PredictionResult,
    create_default_registry,
)
from gas_model_engine.algorithms.composite._legacy_residual import (
    LegacyResidualComposite,
    LegacyResidualPayload,
)


def train_data():
    return AlgorithmDataset(
        features=pd.DataFrame({"x": np.arange(1, 21, dtype=float)}),
        target=pd.Series(np.arange(1, 21, dtype=float)),
        dates=pd.date_range("2025-01-01", periods=20),
    )


def future_data():
    return AlgorithmDataset(
        features=pd.DataFrame({"x": [21.0, 22.0]}),
        dates=pd.date_range("2025-02-01", periods=2),
    )


def member_params():
    return {
        "members": [
            {"code": "LINEAR_REGRESSION_V1"},
            {"code": "RIDGE_V1", "params": {"alpha": 1.0}},
        ]
    }


def test_weighted_average_reuses_registered_atomic_algorithms():
    algorithm = create_default_registry().get("WEIGHTED_AVERAGE_V1")
    model = algorithm.fit(
        train_data(),
        {
            **member_params(),
            "weights": {
                "LINEAR_REGRESSION_V1": 0.7,
                "RIDGE_V1": 0.3,
            },
        },
    )
    result = algorithm.predict(model, future_data())
    assert result.values.shape == (2,)
    assert set(result.metadata["weights"]) == {"LINEAR_REGRESSION_V1", "RIDGE_V1"}


def test_nnls_learns_weights_only_from_oof_predictions():
    data = train_data()
    actual = data.target.to_numpy()
    data.metadata["member_oof_predictions"] = {
        "LINEAR_REGRESSION_V1": actual,
        "RIDGE_V1": actual + 5,
    }
    algorithm = create_default_registry().get("NNLS_WEIGHT_V1")
    model = algorithm.fit(data, member_params())
    assert model.payload.weights["LINEAR_REGRESSION_V1"] > model.payload.weights["RIDGE_V1"]


def test_composite_supports_multiple_parameter_variants_of_same_algorithm():
    algorithm = create_default_registry().get("WEIGHTED_AVERAGE_V1")
    model = algorithm.fit(
        train_data(),
        {
            "members": [
                {"id": "ridge_regular", "code": "RIDGE_V1", "params": {"alpha": 10}},
                {"id": "ridge_strong", "code": "RIDGE_V1", "params": {"alpha": 100}},
            ]
        },
    )
    result = algorithm.predict(model, future_data())
    assert set(result.metadata["weights"]) == {"ridge_regular", "ridge_strong"}


def test_legacy_short_term_residual_combinations_are_registered():
    registry = create_default_registry()

    assert registry.get("PROPHET_AR_RESIDUAL_V1").dependencies() == (
        "PROPHET_V1",
        "AUTOREGRESSION_V1",
    )
    assert registry.get("PROPHET_XGBOOST_AR_RESIDUAL_V1").dependencies() == (
        "PROPHET_V1",
        "XGBOOST_V1",
        "AUTOREGRESSION_V1",
    )
    assert registry.get("PROPHET_LIGHTGBM_RESIDUAL_V1").dependencies() == (
        "PROPHET_V1",
        "LIGHTGBM_V1",
        "AUTOREGRESSION_V1",
    )
    registry.validate_dependencies()
    assert registry.get("PROPHET_AR_RESIDUAL_V1").validate_params({}).valid


def test_residual_combination_clips_future_correction_by_oof_distribution_bounds():
    class FixedAlgorithm:
        def predict(self, model, data):
            return PredictionResult(np.full(len(data.features), model.payload))

    class Registry:
        def get(self, code):
            return FixedAlgorithm()

    class TestComposite(LegacyResidualComposite):
        metadata = create_default_registry().get("PROPHET_AR_RESIDUAL_V1").metadata

        def residual_codes(self):
            return ("RESIDUAL",)

        def fit(self, train_data, params):
            raise NotImplementedError

    algorithm = TestComposite(Registry())
    payload = LegacyResidualPayload(
        AlgorithmModel("BASE", "1", 100.0),
        {"member": AlgorithmModel("RESIDUAL", "1", 80.0)},
        {"member": 1.0},
        0.0,
        (-20.0, 30.0),
        np.arange(1, 21, dtype=float),
    )
    model = AlgorithmModel("TEST", "1", payload)

    result = algorithm.predict(model, future_data())

    assert result.values.tolist() == [130.0, 130.0]
    assert result.metadata["residual_clipped_count"] == 2


def test_lightgbm_residual_uses_direct_features_but_xgboost_uses_target_history():
    registry = create_default_registry()

    assert (
        registry.get("PROPHET_LIGHTGBM_RESIDUAL_V1").get_default_params()["residual_feature_policy"]
        == "PASSTHROUGH_V1"
    )
    assert (
        registry.get("PROPHET_XGBOOST_AR_RESIDUAL_V1").get_default_params()[
            "residual_feature_policy"
        ]
        == "TARGET_HISTORY_V1"
    )
