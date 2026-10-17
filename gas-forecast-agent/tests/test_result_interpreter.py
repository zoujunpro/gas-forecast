from gas_forecast_agent.execution import ToolResultInterpreter


def test_empty_page_becomes_empty_result() -> None:
    result = ToolResultInterpreter.normalize({
        "success": True, "status": "COMPLETED", "resultType": "PAGE",
        "message": "查询成功", "data": {"records": [], "total": 0},
    })
    assert result["status"] == "EMPTY_RESULT"
    assert result["businessStatus"] == "COMPLETED"


def test_failed_tool_cannot_be_mistaken_for_empty_page() -> None:
    result = ToolResultInterpreter.normalize({
        "success": False, "status": "COMPLETED", "resultType": "PAGE",
        "message": "数据库异常", "data": {"records": [], "total": 0},
    })
    assert result["status"] == "FAILED"


def test_entity_multiple_becomes_need_selection() -> None:
    result = ToolResultInterpreter.normalize({
        "success": True, "status": "COMPLETED", "resultType": "ENTITY",
        "data": {"status": "MULTIPLE", "candidates": [{"code": "C1"}, {"code": "C2"}]},
    })
    assert result["status"] == "NEED_SELECTION"


def test_old_page_envelope_is_inferred_for_compatibility() -> None:
    result = ToolResultInterpreter.normalize({
        "success": True, "status": "COMPLETED", "data": {"records": [], "total": 0},
    })
    assert result["resultType"] == "PAGE"
    assert result["status"] == "EMPTY_RESULT"
