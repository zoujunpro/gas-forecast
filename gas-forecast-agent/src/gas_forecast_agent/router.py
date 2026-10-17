"""会话到专业智能体的路由规则。"""

from gas_forecast_agent.schemas import AgentType


class AgentRouter:
    """规则优先的初始路由器；后续模型路由仍需受这些业务边界约束。"""

    _WINTER_WORDS = ("冬季", "采暖季", "保供", "供需缺口", "高峰")
    _MONTHLY_WORDS = ("月度", "本月", "下个月", "月销量", "年度计划")

    def route(self, message: str, requested: AgentType | None = None) -> AgentType:
        if requested is not None:
            return requested
        if any(word in message for word in self._WINTER_WORDS):
            return AgentType.WINTER_SUPPLY
        if any(word in message for word in self._MONTHLY_WORDS):
            return AgentType.MONTHLY_SALES
        return AgentType.SHORT_TERM
