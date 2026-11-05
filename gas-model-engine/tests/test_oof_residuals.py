import numpy as np
import pandas as pd

from gas_model_engine import AlgorithmDataset
from gas_model_engine.algorithms.contracts import (
    AlgorithmCapabilities,
    AlgorithmFamily,
    AlgorithmMetadata,
    AlgorithmModel,
    PredictionResult,
)
from gas_model_engine.backtesting import WalkForwardOOFResidualBuilder


class RecordingMeanAlgorithm:
    metadata = AlgorithmMetadata("RECORDING_MEAN", "测试均值", AlgorithmFamily.BASELINE)

    def __init__(self):
        self.training_end_dates = []

    def get_capabilities(self):
        return AlgorithmCapabilities(minimum_training_rows=2)

    def fit(self, data, params):
        self.training_end_dates.append(data.dates.max())
        return AlgorithmModel(self.metadata.code, "1", float(data.target.mean()))

    def predict(self, model, data):
        return PredictionResult(np.full(len(data.features), model.payload))


def test_oof_residuals_are_generated_only_by_models_trained_on_earlier_dates():
    data = AlgorithmDataset(
        features=pd.DataFrame({"x": np.arange(12)}),
        dates=pd.date_range("2025-01-01", periods=12),
        target=pd.Series(np.arange(1, 13, dtype=float)),
    )
    algorithm = RecordingMeanAlgorithm()

    result = WalkForwardOOFResidualBuilder().build(
        data,
        algorithm,
        {},
        {"folds": 3, "minimum_train_size": 3},
    )

    assert result.fold_count == 3
    assert result.dataset.metadata["residual_training_mode"] == "WALK_FORWARD_OOF"
    assert len(result.dataset.target) == 9
    for training_end, validation_start in zip(
        algorithm.training_end_dates,
        (result.dataset.dates[0], result.dataset.dates[3], result.dataset.dates[6]),
    ):
        assert training_end < validation_start


def test_oof_residuals_use_execution_plan_window_instead_of_spreading_history():
    data = AlgorithmDataset(
        features=pd.DataFrame({"x": np.arange(30)}),
        dates=pd.date_range("2025-01-01", periods=30),
        target=pd.Series(np.arange(1, 31, dtype=float)),
    )
    algorithm = RecordingMeanAlgorithm()

    result = WalkForwardOOFResidualBuilder().build(
        data,
        algorithm,
        {},
        {
            "folds": 3,
            "minimum_train_size": 10,
            "validation_size": 4,
            "step_size": 4,
        },
    )

    assert len(result.dataset.target) == 12
    assert result.dataset.metadata["oof_validation_size"] == 4
    assert result.dataset.metadata["oof_step_size"] == 4
    assert result.source_indices.tolist() == list(range(18, 30))
