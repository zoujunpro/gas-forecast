import pytest

from gas_forecast_agent.schemas import PendingOperation


@pytest.fixture
def make_pending():
    """测试只构造当前操作协议，不模拟已经删除的记忆键。"""
    def make(*, parameters=None, issues=None, interaction_type="CLARIFICATION", pending_tool=None):
        return PendingOperation.model_validate({
            "originalMessage": "预测高邮安源",
            "action": pending_tool,
            "arguments": parameters or {},
            "issues": [{"reason": "PARAMETER_MISSING", "message": "请补充", **issue} for issue in issues or []],
        })
    return make
