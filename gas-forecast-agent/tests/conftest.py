import pytest

from gas_forecast_agent.schemas import PendingOperation


@pytest.fixture
def make_pending():
    """测试只构造当前操作协议，不模拟已经删除的记忆键。"""
    def make(*, parameters=None, issues=None, interaction_type="CLARIFICATION", pending_tool=None):
        return PendingOperation.model_validate({
            "interaction": {
                "interactionId": "INT-1", "sourceTurnId": "TURN-1",
                "interactionType": interaction_type,
                "reason": "PARAMETER_MISSING", "resumeFrom": "PlanValidator",
            },
            "semantics": {"requests": [{"intent": "FORECAST"}]},
            "parameters": parameters or {},
            "pendingTool": pending_tool,
            "issues": [{"reason": "PARAMETER_MISSING", "message": "请补充", **issue} for issue in issues or []],
        })
    return make
