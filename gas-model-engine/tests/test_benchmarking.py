import numpy as np
import pandas as pd
import pytest

from gas_model_engine import (
    BenchmarkDataset,
    ForecastBenchmark,
    LegacyForecastAdapter,
    PipelineForecastAdapter,
    render_markdown,
)


def test_forecast_benchmark_compares_metrics_folds_holiday_and_time():
    dataset = BenchmarkDataset(
        dates=pd.date_range("2025-01-27", periods=6),
        actual=np.array([100, 100, 100, 200, 200, 200]),
        fold_ids=np.array([1, 1, 1, 2, 2, 2]),
        payload={"request": "same-input"},
    )

    report = ForecastBenchmark().compare(
        dataset,
        lambda payload: np.array([90, 90, 90, 180, 180, 180]),
        lambda payload: np.array([95, 95, 95, 190, 190, 190]),
    )

    assert report.old.metrics["mape"] == pytest.approx(10.0)
    assert report.new.metrics["mape"] == pytest.approx(5.0)
    assert report.metric_delta["mape"] == pytest.approx(-5.0)
    assert set(report.new.fold_metrics) == {"1", "2"}
    assert report.spring_festival_sample_count == 5
    assert report.new.spring_festival_metrics is not None
    assert "每折MAPE标准差" in render_markdown(report)


def test_forecast_benchmark_rejects_different_result_length():
    dataset = BenchmarkDataset(
        dates=pd.date_range("2025-03-01", periods=2),
        actual=np.array([1, 2]),
        fold_ids=np.array([1, 1]),
    )

    with pytest.raises(ValueError, match="预测结果长度"):
        ForecastBenchmark().compare(dataset, lambda _: [1], lambda _: [1, 2])


def test_old_and_new_adapters_normalize_different_result_shapes():
    legacy = LegacyForecastAdapter(lambda payload: {"forecast": payload}, lambda x: x["forecast"])

    class Pipeline:
        def run(self, request):
            prediction = type("Prediction", (), {"values": np.asarray(request)})()
            result = type("Result", (), {"final_prediction": prediction})()
            return type("Forecast", (), {"prediction": result})()

    modern = PipelineForecastAdapter(Pipeline(), lambda payload: payload)

    assert legacy([1, 2]).tolist() == [1.0, 2.0]
    assert modern([3, 4]).tolist() == [3.0, 4.0]
