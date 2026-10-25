from gas_forecast_agent.workflow_nodes.understand_request import (
    _numbered_candidate_resolution,
)
from gas_forecast_agent.entity_models import EntityLookupResponse


def test_entity_resolution_exposes_typed_candidate_and_parameters() -> None:
    resolution = EntityLookupResponse.model_validate({
        "status": "MULTIPLE",
        "message": "找到多个客户",
        "items": [{
            "entityType": "CUSTOMER",
            "status": "MULTIPLE",
            "candidates": [{
                "code": "CUS-2",
                "name": "海安新奥",
                "regionName": "江苏",
                "industryName": "城燃",
            }],
        }],
    })

    candidate = resolution.items[0].candidates[0]
    assert candidate.code == "CUS-2"
    assert candidate.display_name == "江苏 / 城燃 / 海安新奥"
    assert resolution.proposed_parameters is None


def test_numbered_candidate_ignores_null_candidate_map(make_pending) -> None:
    assert _numbered_candidate_resolution(
        "2",
        make_pending(issues=[{"field": "customerNames", "details": {"candidateMap": None}}]),
    ) is None


def test_numbered_candidate_restores_selected_entity(make_pending) -> None:
    resolution = _numbered_candidate_resolution(
        "2",
        make_pending(issues=[{"field": "customerNames", "details": {"candidateMap": {
            "candidate-2": {"customerCode": "CUS-2", "customerName": "海安新奥（新奥天津）"},
        }}}]),
    )
    assert resolution is not None
    assert resolution.parameters.to_payload() == {"customerCode": "CUS-2", "customerName": "海安新奥（新奥天津）"}
    assert resolution.issues == []


def test_numbered_candidate_can_be_combined_with_forecast_horizon(make_pending) -> None:
    resolution = _numbered_candidate_resolution(
        "选择 2，预测34天",
        make_pending(issues=[{"field": "customerNames", "details": {"candidateMap": {
            "candidate-2": {"customerCodes": ["CUS-2"], "customerNames": ["海安新奥（新奥天津）"]},
        }}}]),
    )
    assert resolution is not None
    assert resolution.parameters.to_payload() == {"customerCodes": ["CUS-2"], "customerNames": ["海安新奥（新奥天津）"]}


def test_forecast_horizon_is_not_mistaken_for_a_candidate_number(make_pending) -> None:
    pending = make_pending(issues=[{
        "field": "customerNames",
        "details": {"candidateMap": {
            "candidate-30": {"customerCodes": ["CUS-30"], "customerNames": ["不应被选中"]},
        }},
    }])

    assert _numbered_candidate_resolution("预测30天", pending) is None
