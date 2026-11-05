import numpy as np
import pandas as pd
import pytest

from gas_model_engine import AlgorithmDataset, ForecastRequest
from gas_model_engine.data_preparation import ShortTermCustomerDataCleaner
from gas_model_engine.validation import ShortTermCustomerValidator


def test_short_term_cleaner_keeps_future_features_optional():
    request = ForecastRequest(
        "short-term",
        AlgorithmDataset(
            features=pd.DataFrame({"temperature": [10, "bad", 12]}),
            dates=pd.to_datetime(["2025-01-03", "2025-01-01", "2025-01-02"]),
            target=pd.Series([3, np.nan, 2]),
        ),
        AlgorithmDataset(
            features=pd.DataFrame(index=range(2)),
            dates=pd.date_range("2025-01-04", periods=2),
        ),
    )

    cleaned = ShortTermCustomerDataCleaner().clean(request)

    assert list(cleaned.train_data.target) == [2, 3]
    assert len(cleaned.future_data.features.columns) == 0


def test_short_term_cleaner_validates_future_features_when_provided():
    request = ForecastRequest(
        "short-term",
        AlgorithmDataset(
            features=pd.DataFrame({"temperature": [10, 11]}),
            dates=pd.date_range("2025-01-01", periods=2),
            target=pd.Series([1, 2]),
        ),
        AlgorithmDataset(
            features=pd.DataFrame({"temperature": ["bad", "bad"]}),
            dates=pd.date_range("2025-01-03", periods=2),
        ),
    )

    cleaned = ShortTermCustomerDataCleaner().clean(request)
    with pytest.raises(ValueError, match="未来外部特征"):
        ShortTermCustomerValidator().validate(cleaned)
