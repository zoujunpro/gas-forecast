"""工作流节点共享的 State 与运行时依赖类型。"""

from __future__ import annotations

from dataclasses import dataclass

from pydantic import BaseModel, ConfigDict, Field

from gas_forecast_agent.config import Settings
from gas_forecast_agent.internal_client import InternalAgentClient
from gas_forecast_agent.mcp import StreamableHttpMcpClient
from gas_forecast_agent.schemas import (
    AgentRuntimeRequest,
    AgentRuntimeResponse,
    ResolutionIssue,
)
from gas_forecast_agent.operation_models import OperationParameters
from gas_forecast_agent.entity_models import EntityResolution
from gas_forecast_agent.skills import (
    RequestSemantics,
    SkillDefinition,
    SkillRegistry,
    SkillRunResult,
    SkillSelection,
)


class RequestUnderstanding(BaseModel):
    """本轮请求理解的完整结果，禁止再拆成多个 State 顶层字段。"""

    semantics: RequestSemantics
    skill: SkillDefinition
    selection: SkillSelection
    parameters: OperationParameters = Field(default_factory=OperationParameters)
    entity_resolution: EntityResolution = Field(default_factory=EntityResolution)
    issues: list[ResolutionIssue] = Field(default_factory=list)
    continuing_operation: bool = False


class AgentTiming(BaseModel):
    """单次 Agent Run 的阶段耗时，不混入业务状态。"""

    started_at: float
    semantics_ms: int = 0
    routing_ms: int = 0
    agent_ms: int = 0


class AgentGraphState(BaseModel):
    """
    单次 LangGraph 执行期间由所有节点共享的状态。

    LangGraph 支持 Pydantic State：图启动时只需要 request 和 timing；每个节点
    返回局部字段更新，框架将其合并为下一节点可读取的状态。节点不应直接修改
    传入的 state。这样调试器看到的是明确对象，而不是不断变化的大字典。

    该 State 只属于一次 Agent Run，不负责跨请求持久化。下一轮会话仍由 Java
    从数据库加载会话、记忆和历史消息，再创建一个新的 AgentGraphState。
    """

    model_config = ConfigDict(extra="forbid")

    # Java 内部会话接口传入的本轮原始请求。
    # 包含会话编号、轮次编号、用户消息、压缩摘要、近期消息、会话记忆和业务参数。
    # 该字段在 run_agent_graph 创建初始 State 时写入，所有后续节点都可以读取。
    request: AgentRuntimeRequest

    # 本次图的性能时间线，不是业务时间，也不能保存到数据库作为创建时间。
    timing: AgentTiming

    # 请求理解阶段的完整对象：意图、Skill、实体解析结果、累计参数和待澄清问题。
    understanding: RequestUnderstanding | None = None

    # 公共动作执行器对本轮请求的实际执行结果。
    # 包含 Agent 状态、回复文本、已解析参数、调用工具和工具返回摘要；它表示
    # Agent 本轮是否处理成功，不等同于异步训练或预测任务的最终执行状态。
    result: SkillRunResult | None = None

    # 根据 Skill 执行结果组装出的 Python 内部响应。
    # 由 build_response 节点写入，run_agent_graph 从最终 State 中取出并返回给 Java。
    response: AgentRuntimeResponse | None = None

    def require_understanding(self) -> RequestUnderstanding:
        if self.understanding is None:
            raise RuntimeError("understand_request 节点尚未完成")
        return self.understanding

    def require_result(self) -> SkillRunResult:
        if self.result is None:
            raise RuntimeError("execute_action 节点尚未完成")
        return self.result



@dataclass(frozen=True)
class AgentGraphContext:
    """
    单次图运行需要的外部依赖。

    这些对象只为节点提供服务，不属于业务状态，因此通过 LangGraph Runtime
    Context 传入，不写入 AgentGraphState，也不会出现在图的最终业务结果中。
    """

    # 当前服务配置，包括模型、路由方式、超时和 Agent 执行预算等配置。
    settings: Settings

    # 当前 HTTP 请求专用的 Java MCP 客户端，已经携带本次用户的鉴权请求头。
    mcp: StreamableHttpMcpClient
    internal_client: InternalAgentClient

    # 进程内 Skill 注册表，用于根据路由结果读取对应的 Skill 定义。
    skills: SkillRegistry


# 让前向注解或类型拼写错误在服务导入阶段立即失败，不能等到用户发送消息才暴露。
AgentGraphState.model_rebuild()
