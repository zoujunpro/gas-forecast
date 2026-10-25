from gas_forecast_agent.workflow_nodes.understand_request import (
    _allows_explicit_batch_scope,
    _collect_clarification_parameters,
    _has_resolved_pending_entities,
    _is_complete_new_business_request,
    _pending_requires_entity_resolution,
)
from gas_forecast_agent.skills import RequestSemantics
from gas_forecast_agent.operation_models import EntityParameters


def test_forecast_horizon_reply_does_not_revalidate_selected_customer(make_pending) -> None:
    assert not _pending_requires_entity_resolution(make_pending(
        issues=[{"field": "forecastHorizon", "reason": "PARAMETER_MISSING"}],
    ))


def test_customer_reply_still_requires_entity_resolution(make_pending) -> None:
    assert _pending_requires_entity_resolution(make_pending(
        issues=[{"field": "customerNames:1", "reason": "ENTITY_AMBIGUOUS"}],
    ))


def test_entity_clarification_cannot_fall_back_to_region_industry_scope(make_pending) -> None:
    pending = make_pending(issues=[{"field": "customerNames:1"}])

    assert not _has_resolved_pending_entities(
        EntityParameters(region_codes=["REG-1"], industry_codes=["IND-1"]), pending
    )
    assert _has_resolved_pending_entities(EntityParameters(customer_codes=["CUS-1"]), pending)


def test_explicit_all_matched_scope_can_switch_to_batch_prediction() -> None:
    semantics = RequestSemantics.model_validate({
        "requests": [{
            "intent": "FORECAST",
            "targetSelection": "ALL_MATCHED",
            "entities": {"regions": ["江苏"], "industries": ["城燃"], "customers": []},
        }],
    })

    assert _allows_explicit_batch_scope(semantics)


def test_second_answer_is_kept_while_waiting_for_first_entity_answer(make_pending) -> None:
    semantics = RequestSemantics.model_validate({
        "requests": [{"intent": "FORECAST", "parameters": {"forecastHorizon": 34}}],
    })

    assert _collect_clarification_parameters(
        make_pending(parameters={"customerCodes": ["CUS-2"], "_lockedParameters": ["customerCodes"]}),
        semantics,
        EntityParameters(),
    ).forecast_horizon == 34


def test_complete_batch_forecast_request_interrupts_old_clarification() -> None:
    assert _is_complete_new_business_request("帮我对江苏所有城燃客户进行预测")
    assert not _is_complete_new_business_request("30天")
    assert not _is_complete_new_business_request("取消")
