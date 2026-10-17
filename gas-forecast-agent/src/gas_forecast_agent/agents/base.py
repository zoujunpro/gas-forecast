"""专业智能体公共定义。"""

from dataclasses import dataclass

from gas_forecast_agent.schemas import AgentType


@dataclass(frozen=True, slots=True)
class AgentDefinition:
    """描述智能体的职责、时间粒度和允许使用的 MCP 工具。"""

    agent_type: AgentType
    title: str
    frequency: str
    description: str
    allowed_tools: frozenset[str]
