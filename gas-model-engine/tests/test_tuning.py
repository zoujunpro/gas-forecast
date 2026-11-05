from gas_model_engine.tuning import expand_parameter_grid


def test_parameter_grid_supports_nested_algorithm_params():
    variants = expand_parameter_grid(
        {"prophet_params": {"seasonality_mode": "additive"}},
        {
            "prophet_params.changepoint_prior_scale": [0.01, 0.05],
            "residual_params.max_depth": [3, 5],
        },
    )

    assert len(variants) == 4
    assert variants[0]["prophet_params"]["changepoint_prior_scale"] == 0.01
    assert variants[-1]["residual_params"]["max_depth"] == 5
