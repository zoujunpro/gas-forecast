from gas_forecast_agent.router import AgentRouter
from gas_forecast_agent.schemas import AgentType


def test_routes_winter_supply() -> None:
    assert AgentRouter().route("分析今年冬季保供风险") == AgentType.WINTER_SUPPLY


def test_routes_monthly_sales() -> None:
    assert AgentRouter().route("预测下个月销量") == AgentType.MONTHLY_SALES


def test_defaults_to_short_term() -> None:
    assert AgentRouter().route("预测这个客户未来七天用气量") == AgentType.SHORT_TERM


def test_explicit_agent_has_priority() -> None:
    assert AgentRouter().route("预测下个月销量", AgentType.SHORT_TERM) == AgentType.SHORT_TERM
