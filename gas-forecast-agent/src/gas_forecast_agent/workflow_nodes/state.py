"""工作流节点共享的 State 与运行时依赖类型。"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, TypedDict

from gas_forecast_agent.config import Settings
from gas_forecast_agent.internal_client import InternalAgentClient
from gas_forecast_agent.mcp import StreamableHttpMcpClient
from gas_forecast_agent.schemas import AgentRuntimeRequest, AgentRuntimeResponse, ResolutionIssue
from gas_forecast_agent.skills import (
    RequestSemantics,
    SkillDefinition,
    SkillRegistry,
    SkillRunResult,
    SkillSelection,
)


class AgentGraphState(TypedDict, total=False):
    """
    单次 LangGraph 执行期间由所有节点共享的状态。

    ``total=False`` 表示字段可以分阶段写入：图启动时只需要 request 和
    started_at，后续节点返回的部分字典会合并到同一个 State 中。节点不需要、
    也不应该复制整个 State；前面节点已经产生的字段会一直保留到本次图执行结束。

    该 State 只属于一次 Agent Run，不负责跨请求持久化。下一轮会话仍由 Java
    从数据库加载会话、记忆和历史消息，再创建一个新的 AgentGraphState。
    """

    # Java 内部会话接口传入的本轮原始请求。
    # 包含会话编号、轮次编号、用户消息、压缩摘要、近期消息、会话记忆和业务参数。
    # 该字段在 run_agent_graph 创建初始 State 时写入，所有后续节点都可以读取。
    request: AgentRuntimeRequest

    # 本次图开始执行时的单调时钟时间点。
    # 只用于计算总耗时，不是业务时间，也不能保存到数据库作为创建时间。
    started_at: float

    # Java 随请求传入的当前会话结构化记忆副本。
    # 由 load_memory 节点写入，可能包含已确认参数、上次工具结果和待确认动作。
    memory: dict[str, Any]

    # 对用户本轮原话的结构化语义识别结果。
    # 由 understand_request 节点写入，供 Skill 路由和 Skill 执行阶段共同使用。
    semantics: RequestSemantics

    # 意图识别产生的 Skill 决策，包含 Skill 编码、置信度和选择原因。
    # 由 understand_request 写入，最终也会用于生成 Agent Run 的审计信息。
    selection: SkillSelection

    # 根据 selection 从 SkillRegistry 取得的完整 Skill 定义。
    # 包含 Skill 说明、模型指令和允许调用的 MCP 工具白名单。
    skill: SkillDefinition

    # 公共动作执行器对本轮请求的实际执行结果。
    # 包含 Agent 状态、回复文本、已解析参数、调用工具和工具返回摘要；它表示
    # Agent 本轮是否处理成功，不等同于异步训练或预测任务的最终执行状态。
    result: SkillRunResult

    # 根据 Skill 执行结果组装出的 Python 内部响应。
    # 由 build_response 节点写入，run_agent_graph 从最终 State 中取出并返回给 Java。
    response: AgentRuntimeResponse

    # Python 仅计算本轮应更新或删除的会话记忆字段；由 Java 与助手消息同事务落库。
    memory_patch: dict[str, Any]

    # understand_request 节点耗时，单位为毫秒，仅用于日志和性能分析。
    semantics_ms: int

    # Skill 确定耗时，单位为毫秒，仅用于日志和性能分析。
    routing_ms: int

    # execute_action 节点耗时，单位为毫秒，仅用于日志和性能分析。
    agent_ms: int

    # 结构化澄清回复从上一轮候选项恢复出的可信参数，例如候选 token 对应的
    # customerCode。该字段只在本轮内使用，不接受客户端直接提交业务编码。
    clarification_parameters: dict[str, Any]

    # understand_request 通过主数据校验后产生的标准实体参数（例如 customerCodes）。
    # 只在当前请求中传递给 Skill，不由客户端提交，也不跨轮直接复用。
    resolved_entity_parameters: dict[str, Any]

    # 在 Skill 路由前发现的意图、实体或引用问题。存在必需问题时执行节点不会
    # 调用任何业务工具，而是生成结构化澄清交互。
    preflight_issues: list[ResolutionIssue]


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
