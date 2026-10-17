"""专业智能体注册表。"""

from gas_forecast_agent.schemas import AgentType

from .base import AgentDefinition

COMMON_TOOLS = frozenset(
    {
        "memory_get",
        "memory_update",
        "customer_search",
        "region_search",
        "industry_search",
        "region_list",
        "industry_list",
        "customer_list",
        "business_reference_resolve",
        "training_start",
        "training_status_get",
        "training_result_get",
        "training_latest_get",
        "training_history_get",
        "forecast_start",
        "forecast_status_get",
        "forecast_result_get",
        "forecast_latest_get",
        "forecast_history_get",
        "task_status_get",
        "task_steps_get",
        "task_step_record",
        "task_resume",
        "task_retry",
    }
)


class AgentRegistry:
    """集中维护三个智能体，避免路由层散落硬编码。"""

    def __init__(self) -> None:
        self._agents = {
            AgentType.SHORT_TERM: AgentDefinition(
                AgentType.SHORT_TERM,
                "短期客户日预测智能体",
                "DAILY",
                "负责客户日用气量训练、预测、进度查询和结果解释。",
                COMMON_TOOLS,
            ),
            AgentType.MONTHLY_SALES: AgentDefinition(
                AgentType.MONTHLY_SALES,
                "月度销量预测智能体",
                "MONTHLY",
                "负责客户或区域月度销量训练、预测和趋势解释。",
                COMMON_TOOLS,
            ),
            AgentType.WINTER_SUPPLY: AgentDefinition(
                AgentType.WINTER_SUPPLY,
                "冬季保供智能体",
                "TENDAY",
                "负责采暖季需求、峰值和供需风险预测。",
                COMMON_TOOLS,
            ),
        }

    def get(self, agent_type: AgentType) -> AgentDefinition:
        return self._agents[agent_type]
