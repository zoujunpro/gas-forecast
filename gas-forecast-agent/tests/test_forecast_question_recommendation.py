from gas_forecast_agent.recommendation import select_forecast_questions, validated_questions


def context() -> dict[str, object]:
    return {
        "forecastHorizon": 10,
        "mape": 12.8,
        "forecast": {"points": [{"date": "2026-11-12", "prediction": 110}, {"date": "2026-11-13", "prediction": 112}]},
        "backtest": {"points": [{"actual": 120}, {"actual": 118}]},
    }


def test_selects_change_cause_and_accuracy_for_meaningful_decline() -> None:
    selected = select_forecast_questions(context())
    assert [item.category for item in selected] == ["CHANGE_RATE", "CAUSE", "ACCURACY"]
    assert all(len(item.text) <= 30 for item in selected)


def test_does_not_recommend_risk_without_threshold() -> None:
    assert "RISK" not in {item.category for item in select_forecast_questions(context())}


def test_ai_can_only_rewrite_selected_questions() -> None:
    selected = select_forecast_questions(context())
    rewritten = [
        {"code": item.code, "category": item.category, "text": f"{index + 1}号业务问题？"}
        for index, item in enumerate(selected)
    ]
    assert [item["text"] for item in validated_questions(rewritten, selected)] == ["1号业务问题？", "2号业务问题？", "3号业务问题？"]


def test_invalid_ai_rewrite_falls_back_to_rule_questions() -> None:
    selected = select_forecast_questions(context())
    result = validated_questions([{"code": "UNKNOWN", "category": "RISK", "text": "系统无法回答的问题？"}], selected)
    assert [item["text"] for item in result] == [item.text for item in selected]
