from pathlib import Path

import numpy as np
import pandas as pd
import pytest

from gas_model_engine import AlgorithmDataset, create_default_registry


def dataset(values, feature_values=None):
    values = np.asarray(values, dtype=float)
    feature_values = values if feature_values is None else np.asarray(feature_values, dtype=float)
    return AlgorithmDataset(
        features=pd.DataFrame({"x": feature_values}),
        target=pd.Series(values),
        dates=pd.date_range("2025-01-01", periods=len(values), freq="D"),
    )


def future(size):
    return AlgorithmDataset(
        features=pd.DataFrame({"x": np.arange(size, dtype=float)}),
        dates=pd.date_range("2025-03-01", periods=size, freq="D"),
    )


def test_default_registry_contains_30_atomic_and_10_composite_algorithms():
    registry = create_default_registry()
    metadata = registry.list_metadata()
    assert len(metadata) == 40
    assert sum(item.kind == "ATOMIC" for item in metadata) == 30
    assert sum(item.kind == "COMPOSITE" for item in metadata) == 10
    assert registry.get("LIGHTGBM_V1").metadata.family == "TREE"
    assert registry.get("RESIDUAL_COMBINATION_V1").metadata.family == "COMPOSITE"


def test_baseline_fit_predict_save_and_load(tmp_path: Path):
    algorithm = create_default_registry().get("SEASONAL_NAIVE_V1")
    model = algorithm.fit(dataset([1, 2, 3, 4]), {"period": 2})
    result = algorithm.predict(model, future(3))
    assert result.values.tolist() == [3.0, 4.0, 3.0]

    path = tmp_path / "model.joblib"
    algorithm.save(model, path)
    loaded = algorithm.load(path)
    assert loaded.algorithm_code == "SEASONAL_NAIVE_V1"


def test_linear_algorithm_uses_standard_contract():
    algorithm = create_default_registry().get("RIDGE_V1")
    train = dataset(np.arange(1, 21), np.arange(1, 21))
    model = algorithm.fit(train, {"alpha": 0.01})
    prediction = algorithm.predict(
        model,
        AlgorithmDataset(
            features=pd.DataFrame({"x": [21.0, 22.0]}),
            dates=pd.date_range("2025-02-01", periods=2),
        ),
    )
    assert prediction.values.shape == (2,)
    assert prediction.values[1] > prediction.values[0]


def test_parameter_and_input_validation():
    registry = create_default_registry()
    validation = registry.get("EWMA_V1").validate_params({"alpha": 2})
    assert not validation.valid
    assert validation.errors[0].field == "alpha"

    with pytest.raises(ValueError, match="至少需要7条"):
        registry.get("SEASONAL_NAIVE_V1").fit(dataset([1, 2]), {"period": 7})
