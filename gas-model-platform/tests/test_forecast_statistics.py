import pytest
from gas_model_platform.services.forecast_statistics import calculate_forecast_statistics as calculate


@pytest.mark.parametrize('grain', ['day', 'month', 'tenday'])
def test_same_grain_statistics_and_actual_comparison(grain):
    result = calculate([('2026-11-01', 200), ('2026-11-11', 300)],
                       [('2026-10-01', 100), ('2026-10-11', 150)], [], grain)
    assert result['total'] == 500
    assert result['periodAverage'] == 250
    assert result['historicalComparison']['changePercent'] == 100
    assert result['peakDates'] == ['2026-11-11']
    assert result['troughDates'] == ['2026-11-01']


def test_yoy_matches_dates_not_positions_and_reports_partial_coverage():
    result = calculate([('2026-01-01', 200), ('2026-01-02', 300)], [],
                       [('2025-01-02', 150)], 'day')
    assert result['yearOverYear']['matchedCount'] == 1
    assert result['yearOverYear']['coverage'] == 'PARTIAL'
    assert result['yearOverYear']['changePercent'] == 100
    assert result['yearOverYear']['forecastValue'] == 300


def test_invalid_and_duplicate_points_are_not_zero_filled():
    result = calculate([('2026-01-01', 10), ('2026-01-01', 20),
                        ('2026-01-02', None), ('2026-01-03', float('nan'))], [], [], 'day')
    assert result['count'] == 0
    assert result['missingCount'] == 4


def test_zero_baseline_and_tied_extrema():
    result = calculate([('2026-01-01', 10), ('2026-01-02', 10)],
                       [('2025-12-31', 0)], [('2025-01-01', 0)], 'day')
    assert result['historicalComparison']['changePercent'] is None
    assert result['yearOverYear']['changePercent'] is None
    assert len(result['peakDates']) == 2


def test_month_and_tenday_do_not_compare_raw_daily_history():
    for grain in ['month', 'tenday']:
        result = calculate([('2026-01-01', 100)], [], [('2025-01-01', 10)], grain)
        assert result['yearOverYear']['status'] == 'UNAVAILABLE'


def test_leap_day_has_no_exact_prior_year_counterpart():
    result = calculate([('2024-02-29', 100)], [], [('2023-02-28', 50)], 'day')
    assert result['yearOverYear']['matchedCount'] == 0


@pytest.mark.parametrize('grain,agent', [('day', 'short-term'), ('month', 'monthly-sales'), ('tenday', 'winter-supply')])
def test_execution_response_saves_statistics_inside_forecast(grain, agent):
    from types import SimpleNamespace
    from gas_model_platform.schemas.modeling import ForecastExecuteRequest, TrainResult, PredictResult, ForecastPoint
    from gas_model_platform.services.runtime import ModelRuntime

    class Handler:
        info = SimpleNamespace(agent_code=agent, model_code='TEST', model_version='1')

        def validate_training_data(self, context):
            return SimpleNamespace(valid=True, summary={}, errors=[], warnings=[])

        def train(self, context):
            assert "comparison_dataset" not in context.model_dump()
            assert context.dataset[0]["gas_sales"] == 100
            return TrainResult(agent_code=agent, model_code='TEST', train_batch_no='FORECAST-1')

        def predict(self, context):
            return PredictResult(agent_code=agent, model_code='TEST', forecast_batch_no='FORECAST-1',
                                 points=[ForecastPoint(forecast_date='2026-01-01', prediction=120)])

    runtime = ModelRuntime(SimpleNamespace(resolve=lambda code: Handler()))
    result = runtime.execute_forecast(ForecastExecuteRequest(
        model_code='TEST', agent_code=agent, forecast_no='FORECAST-1', forecast_horizon=1,
        forecast_unit=grain, history_dataset=[{'date': '2025-12-01', 'gas_sales': 100}],
        future_dataset=[{'date': '2026-01-01'}],
        comparison_dataset=[{'date': '2026-01-01', 'gas_sales': 100}]))
    snapshot = result.model_dump(mode='json')['forecast']['statistics']
    assert snapshot['grain'] == grain
    assert snapshot['total'] == 120
    assert snapshot['yearOverYear']['changePercent'] == 20
    assert snapshot['lastYearPoints'] == [{'date': '2026-01-01', 'value': 100}]
    assert snapshot['historicalComparison']['status'] == 'UNAVAILABLE'


@pytest.mark.parametrize('grain', ['day', 'month', 'tenday'])
def test_explicit_comparison_is_already_aligned_and_overrides_old_sources(grain):
    result = calculate([('2026-01-01', 200), ('2026-02-01', 300)], [],
                       [('2025-01-01', 1)], grain,
                       comparison=[('2026-01-01', 100)])
    assert result['yearOverYear']['changePercent'] == 100
    assert result['yearOverYear']['coverage'] == 'PARTIAL'
    assert result['lastYearPoints'] == [{'date': '2026-01-01', 'value': 100}]


def test_explicit_empty_comparison_does_not_fall_back_to_training_history():
    result = calculate([('2026-01-01', 200)], [], [('2025-01-01', 100)], 'day', comparison=[])
    assert result['yearOverYear']['matchedCount'] == 0
    assert result['lastYearPoints'] == []
