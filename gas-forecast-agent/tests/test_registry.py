from gas_forecast_agent.agents import AgentRegistry
from gas_forecast_agent.schemas import AgentType


def test_each_agent_can_discover_execution_tools() -> None:
    registry = AgentRegistry()
    for agent_type in AgentType:
        agent = registry.get(agent_type)
        assert "training_start" in agent.allowed_tools
        assert "forecast_start" in agent.allowed_tools
        assert "task_status_get" in agent.allowed_tools


def test_agent_frequency_matches_business_type() -> None:
    registry = AgentRegistry()
    assert registry.get(AgentType.SHORT_TERM).frequency == "DAILY"
    assert registry.get(AgentType.MONTHLY_SALES).frequency == "MONTHLY"
    assert registry.get(AgentType.WINTER_SUPPLY).frequency == "TENDAY"
