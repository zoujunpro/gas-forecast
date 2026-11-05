import pytest

from gas_model_engine.evaluation import conformal_radius


def test_conformal_radius_uses_finite_sample_oof_error_quantile():
    radius = conformal_radius([10, 20, 30, 40], [9, 18, 27, 36], coverage=0.75)

    assert radius == pytest.approx(4.0)
