"""FastAPI 应用入口。"""

import logging
import sys
from pathlib import Path
from typing import Annotated, Any

# 直接运行当前文件时，其所在目录会进入模块搜索路径，导致本地的 mcp
# 子包遮蔽同名第三方依赖。改为从 src 根目录解析项目包。
if __name__ == "__main__" and not __package__:
    package_dir = Path(__file__).resolve().parent
    sys.path[:] = [
        entry for entry in sys.path if Path(entry or ".").resolve() != package_dir
    ]
    sys.path.insert(0, str(package_dir.parent))

from fastapi import Depends, FastAPI, Header, HTTPException
from gas_forecast_agent.analysis import ForecastAnalysisRequest, ForecastAnalysisResponse, ForecastResultAnalyzer
from gas_forecast_agent.config import Settings, get_settings
from gas_forecast_agent.llm import build_llm_client
from gas_forecast_agent.internal_client import InternalAgentClient
from gas_forecast_agent.mcp import McpClientError, StreamableHttpMcpClient
from gas_forecast_agent.schemas import (
    AgentRuntimeRequest,
    AgentRuntimeResponse,
    AgentChatRequest,
    AgentChatResponse,
    AgentStatus,
    ClarificationField,
    ClarificationResponse,
    MessageReferenceResponse,
    ReferenceRelation,
    ReferenceType,
)
from gas_forecast_agent.skills import SemanticExtractionError, SkillRegistry
from gas_forecast_agent.workflow import persistent_parameters, run_agent_graph


logger = logging.getLogger(__name__)

app = FastAPI(title="Gas Forecast Agent", version="0.1.0")
skill_registry = SkillRegistry()


def get_mcp_client(
    settings: Annotated[Settings, Depends(get_settings)],
    authorization: Annotated[str | None, Header()] = None,
    access_token: Annotated[str | None, Header(alias="X-Access-Token")] = None,
    trace_id: Annotated[str | None, Header(alias="x-traceId")] = None,
) -> StreamableHttpMcpClient:
    """按请求创建 MCP 客户端；原样透传用户凭证，由 Java 独立鉴权。"""
    headers = settings.mcp_headers(authorization, access_token)
    if trace_id:
        headers["x-traceId"] = trace_id
    return StreamableHttpMcpClient(
        settings.mcp_url,
        headers,
        settings.mcp_timeout_seconds,
    )


def get_internal_agent_client(
    settings: Annotated[Settings, Depends(get_settings)],
    authorization: Annotated[str | None, Header()] = None,
    access_token: Annotated[str | None, Header(alias="X-Access-Token")] = None,
    trace_id: Annotated[str | None, Header(alias="x-traceId")] = None,
) -> InternalAgentClient:
    headers = settings.mcp_headers(authorization, access_token)
    if trace_id:
        headers["x-traceId"] = trace_id
    return InternalAgentClient(settings.entity_resolve_url, headers, settings.mcp_timeout_seconds)


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "UP"}


@app.post("/api/v1/forecast-analysis", response_model=ForecastAnalysisResponse, response_model_by_alias=True)
async def forecast_analysis(
    request: ForecastAnalysisRequest,
    settings: Annotated[Settings, Depends(get_settings)],
) -> ForecastAnalysisResponse:
    """供Java预测完成事件调用；模型解析统一在Python Agent内执行。"""
    try:
        return await ForecastResultAnalyzer(settings).analyze(request)
    except ValueError as exc:
        logger.exception("预测结果解析配置异常")
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    except Exception as exc:
        logger.exception("预测结果 AI 解析失败")
        raise HTTPException(status_code=502, detail=f"预测结果AI解析失败：{exc}") from exc


@app.post("/api/v1/llm/chat-completions")
async def llm_chat_completions(
    request: dict[str, Any],
    settings: Annotated[Settings, Depends(get_settings)],
) -> dict[str, Any]:
    """Java业务的统一模型网关；模型地址、密钥和SDK只由Python Agent持有。"""
    if not settings.llm_api_key or not settings.llm_model:
        raise HTTPException(status_code=503, detail="Python Agent未配置LLM_API_KEY或LLM_MODEL")
    messages = request.get("messages")
    if not isinstance(messages, list) or not messages:
        raise HTTPException(status_code=422, detail="messages不能为空")
    try:
        client = build_llm_client(settings, minimum_timeout=120)
        response = await client.chat.completions.create(
            model=settings.llm_model,
            temperature=float(request.get("temperature", 0.7)),
            messages=messages,
        )
        content = (response.choices[0].message.content or "").strip()
        if not content:
            raise RuntimeError("大模型未返回内容")
        return {"model": settings.llm_model, "choices": [{"message": {"role": "assistant", "content": content}}]}
    except Exception as exc:
        logger.exception("Python Agent 模型调用失败，model=%s", settings.llm_model)
        raise HTTPException(status_code=502, detail=f"Python Agent模型调用失败：{exc}") from exc


@app.get("/api/v1/mcp/tools")
async def list_mcp_tools(client: Annotated[StreamableHttpMcpClient, Depends(get_mcp_client)]) -> dict[str, Any]:
    """用于部署联调：确认 Python 能获得 Java 发布的完整工具描述。"""
    try:
        tools = await client.list_tools()
        return {"count": len(tools), "tools": tools}
    except McpClientError as exc:
        logger.exception("MCP 工具发现接口失败")
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.get("/api/v1/skills")
async def list_skills() -> dict[str, Any]:
    """返回可发现的 Skill 及其工具白名单。"""
    return skill_registry.summary()


@app.post("/api/v1/internal/chat", response_model=AgentChatResponse, response_model_by_alias=True)
async def agent_chat(
    request: AgentChatRequest,
    client: Annotated[StreamableHttpMcpClient, Depends(get_mcp_client)],
    internal_client: Annotated[InternalAgentClient, Depends(get_internal_agent_client)],
    settings: Annotated[Settings, Depends(get_settings)],
    trace_id: Annotated[str | None, Header(alias="x-traceId")] = None,
) -> AgentChatResponse:
    """
    Java 会话服务调用的 Agent 编排入口。

    Java 在调用前已经创建会话、保存用户消息并创建 Agent Run；Python 只负责
    LangGraph/Skill 编排和 MCP 业务工具调用，不在这里保存会话或生成服务端编号。
    """
    # 1. 当前业务焦点来自用户发送消息时正在查看的页面对象。它可以随页面选择
    #    变化，只作为指代消解候选，不代表用户已经确认用它创建训练或预测任务。
    current_business_context = request.business_context.model_dump(
        by_alias=True,
        exclude_none=True,
    )
    request_metadata: dict[str, Any] = {}
    if current_business_context:
        request_metadata["currentBusinessContext"] = current_business_context

    # 2. 补充本轮执行标识。conversationNo 是 AgentRuntimeRequest 的独立字段，
    #    因此不在 metadata 中重复保存，避免出现两个不一致的会话编号来源。
    request_metadata.update({
        "turnId": request.turn_id,
        "runNo": request.run_no,
        "agentCode": request.agent_code.value,
    })

    # 3. conversationContext 是 Java 从数据库加载的可信会话快照，包含近期消息
    #    以及每条消息关联的执行任务、训练批次或预测批次。
    conversation_context = request.conversation_context

    # 4. 单独提取历史消息的业务对象引用并保留消息归属关系。
    #    普通对话历史只传 role/content；引用放入 metadata，供 Skill 判断用户说的
    #    “这个任务”“上次预测”等对象具体指向哪个业务编号。
    history_references = [
        {
            "messageId": item.message_id,
            "turnId": item.turn_id,
            "references": [
                # 转为可直接传给工具和模型的小驼峰 JSON 字典，并省略空展示字段。
                reference.model_dump(mode="json", by_alias=True, exclude_none=True)
                for reference in item.references
            ],
        }
        for item in conversation_context.recent_messages
        # 没有关联对象的消息无需出现在 historyReferences 中，减少上下文体积。
        if item.references
    ]
    if history_references:
        request_metadata["historyReferences"] = history_references

    # 5. 将对外的 Java 请求适配为现有 Agent 编排层使用的内部请求。
    #    仅 USER/ASSISTANT 消息进入自然语言对话历史；SYSTEM/TOOL 消息不会在此处
    #    直接注入模型，以免外部持久化内容改变 Python 侧的系统指令或工具协议。
    agent_request = AgentRuntimeRequest(
        turn_id=request.turn_id,
        conversation_no=request.conversation_no,
        message=request.message,
        input_type=request.input_type,
        interaction_id=request.interaction_id,
        reply_to_turn_id=request.reply_to_turn_id,
        answers=dict(request.answers),
        agent_type=request.agent_code,
        conversation_summary=conversation_context.summary,
        memory=dict(conversation_context.memory),
        history=[
            {"role": item.role.lower(), "content": item.content}
            for item in conversation_context.recent_messages
            if item.role.upper() in {"USER", "ASSISTANT"}
        ],
        metadata=request_metadata,
    )

    # 6. 进入 LangGraph 主流程：加载会话记忆、识别语义、选择 Skill、
    #    执行 Skill、保存记忆并组装结果。图中的每个节点只做一件事。
    try:
        reply = await run_agent_graph(
            request=agent_request,
            client=client,
            internal_client=internal_client,
            settings=settings,
            skills=skill_registry,
        )
    except SemanticExtractionError:
        logger.exception(
            "event=agent_semantics_failed trace_id=%s conversation_no=%s run_no=%s",
            trace_id or "-",
            request.conversation_no,
            request.run_no,
        )
        answer = "本轮请求的意图识别暂时失败，尚未执行任何业务工具。请稍后重试。"
        return AgentChatResponse(
            conversation_no=request.conversation_no,
            turn_id=request.turn_id,
            run_no=request.run_no,
            agent_code=request.agent_code,
            status=AgentStatus.FAILED,
            message=answer,
            error_code="SEMANTIC_EXTRACTION_FAILED",
            error_message=answer,
        )
    except Exception:
        # 7. 未预期异常统一转换为稳定的业务响应。
        #    这里不尝试回滚或重放业务工具，因为异常前工具可能已经成功创建任务；
        #    自动重试可能导致重复训练或重复预测。
        logger.exception(
            "event=agent_chat_failed trace_id=%s conversation_no=%s run_no=%s",
            trace_id or "-",
            request.conversation_no,
            request.run_no,
        )
        answer = "本轮智能体处理发生系统异常，未确认创建新的业务任务；已经创建的训练或预测任务不受影响。请稍后重试，如仍失败请提供当前会话时间排查。"
        return AgentChatResponse(
            conversation_no=request.conversation_no,
            turn_id=request.turn_id,
            run_no=request.run_no,
            agent_code=request.agent_code,
            status=AgentStatus.FAILED,
            message=answer,
            error_code="AGENT_EXECUTION_ERROR",
            error_message=answer,
        )

    # 8. 将内部运行结果规范化为 Java 会话服务约定的响应结构。
    #    execution_result 可能为空；统一使用空字典可以简化后续状态和编号提取。
    execution = reply.execution_result or {}

    # 内部 Skill 状态可能包含 EXECUTING、NEED_CONFIRMATION 等细分值，先映射为
    # AgentStatus；再从真实工具结果提取消息需要持久化的业务对象引用。
    status = _chat_status(reply.status)
    references = _chat_references(execution, reply.executed_tool)

    # 9. 返回给 Java。Java 负责把消息、意图、引用和运行结果落库，并更新
    # agent_run_tb；Python 只回传结构化结果，不在本接口直接操作会话表。
    return AgentChatResponse(
        # 服务端编号全部原样回传，Java 可据此校验响应属于当前会话和运行记录。
        conversation_no=request.conversation_no,
        turn_id=request.turn_id,
        run_no=request.run_no,
        # Agent 未推导出更具体的类型时，回退到 Java 已确定的会话智能体。
        agent_code=reply.agent_type or request.agent_code,
        status=status,
        message=reply.message,
        # WAITING 状态返回结构化补充项；其他状态通常为空。
        clarification=_clarification(reply, status),
        # references 只携带轻量编号，任务进度和结果仍由对应业务接口查询。
        references=references,
        # 意图与持久参数用于审计及后续轮次恢复，不保存单轮控制参数。
        intent_code=_intent_code(reply),
        intent_data={
            "parameters": persistent_parameters(reply.parameters),
            "entityResolution": reply.entity_resolution,
        },
        skill_code=reply.skill_name,
        # resultData 保存轻量执行摘要，不重复嵌入可能很大的完整工具响应。
        result_data={
            "executedTool": reply.executed_tool,
            "toolStatus": _find_text(execution, "status"),
            "decisionReason": reply.decision_reason,
            "references": [
                item.model_dump(mode="json", by_alias=True, exclude_none=True)
                for item in references
            ],
        },
        memory_patch=reply.memory_patch,
        presentation=reply.presentation,
        execution_result=execution,
        # 只有最终失败时才填写稳定错误码和可展示错误信息。
        error_code="AGENT_RESULT_FAILED" if status == AgentStatus.FAILED else None,
        error_message=reply.message if status == AgentStatus.FAILED else None,
    )


def _chat_status(status: str) -> AgentStatus:
    """将旧 Agent 内部状态收敛为稳定的对外会话状态。"""
    normalized = status.strip().upper()
    if normalized in {"NEED_INFORMATION", "NEED_SELECTION", "NEED_CONFIRMATION", "NEED_CLARIFICATION"}:
        return AgentStatus.WAITING
    if normalized in {"FAILED", "NOT_FOUND", "SYSTEM_ERROR", "UNKNOWN"}:
        return AgentStatus.FAILED
    if normalized == "CANCELLED":
        return AgentStatus.CANCELLED
    if normalized == "PENDING":
        return AgentStatus.PENDING
    if normalized == "RUNNING":
        return AgentStatus.RUNNING
    # COMPLETED、SUCCESS、EXECUTING、WAITING_TRAINING 都表示 Agent 已完成本轮处理；
    # 后台训练或预测是否完成，应通过消息引用的执行对象继续查询。
    return AgentStatus.SUCCESS


def _chat_references(
    execution_result: dict[str, Any],
    executed_tool: str | None,
) -> list[MessageReferenceResponse]:
    """从本轮真实工具结果提取轻量业务引用，不回传完整执行数据。"""
    relation = (
        ReferenceRelation.CREATED
        if executed_tool in {"training_start", "forecast_start"}
        else ReferenceRelation.DISPLAYED
    )
    status = _find_text(execution_result, "status")
    references: list[MessageReferenceResponse] = []
    for field, reference_type in (
        ("executionBatchNo", ReferenceType.EXECUTION_BATCH),
        ("taskNo", ReferenceType.EXECUTION_TASK),
        ("trainBatchNo", ReferenceType.TRAIN_BATCH),
        ("forecastBatchNo", ReferenceType.FORECAST_BATCH),
    ):
        for reference_no in _find_texts(execution_result, field):
            references.append(MessageReferenceResponse(
                reference_type=reference_type,
                reference_no=reference_no,
                relation_type=relation,
                status=status,
            ))
    return references


def _clarification(
    reply: AgentRuntimeResponse,
    status: AgentStatus,
) -> ClarificationResponse | None:
    """把 Agent 的缺失参数转换成前端可渲染的结构化补充请求。"""
    if (
        status != AgentStatus.WAITING
        or not reply.resolution_issues
        or not reply.interaction_id
        or reply.interaction_type is None
        or not reply.reply_to_turn_id
        or reply.clarification_reason is None
        or not reply.resume_from
    ):
        return None
    return ClarificationResponse(
        interaction_id=reply.interaction_id,
        interaction_type=reply.interaction_type,
        reply_to_turn_id=reply.reply_to_turn_id,
        request_id=reply.request_id,
        reason=reply.clarification_reason,
        resume_from=reply.resume_from,
        prompt=reply.message,
        fields=[
            ClarificationField(
                parameter_code=issue.field,
                parameter_name=_parameter_name(issue.field),
                reason=issue.reason,
                input_type=issue.input_type,
                required=issue.required,
                options=issue.options,
            )
            for issue in reply.resolution_issues
        ],
    )


def _parameter_name(parameter: str) -> str:
    """把稳定字段编码转换成默认中文展示名，未知字段保留原编码。"""
    # 批量实体澄清使用 customerNames:1 之类的内部唯一键，序号只用于
    # 服务端把用户答案映射回对应实体，不能暴露到前端。
    base = parameter.split(":", 1)[0]
    return {
        "intent": "请求类型",
        "businessScope": "业务范围",
        "customerName": "客户名称",
        "regionName": "地区名称",
        "industryName": "行业名称",
        "forecastHorizon": "预测周期",
        "confirmExecution": "执行确认",
        "parameters": "业务参数",
        "request": "请求说明",
        "selection": "候选选择",
        "customerNames": "客户名称",
        "regionNames": "地区名称",
        "industryNames": "行业名称",
    }.get(base, parameter)


def _intent_code(reply: AgentRuntimeResponse) -> str:
    """优先返回语义理解的稳定意图；仅为旧运行结果保留工具名兜底。"""
    if reply.intent_code:
        return reply.intent_code

    skill_intents = {
        "model-training-management": "TRAIN",
        "customer-gas-forecast": "FORECAST",
        "business-data-query": "QUERY",
        "forecast-analysis": "ANALYZE",
        "general-chat": "CHAT",
    }
    if reply.skill_name in skill_intents:
        return skill_intents[reply.skill_name]

    tool = reply.executed_tool or ""
    if tool.startswith("training_"):
        return "TRAIN" if tool == "training_start" else "QUERY"
    if tool.startswith("forecast_"):
        return "FORECAST" if tool == "forecast_start" else "QUERY"
    if tool:
        return "QUERY"
    return "CHAT"


def _find_text(value: Any, name: str) -> str | None:
    """查找第一个非空字段值，用于读取单值状态和意图。"""
    values = _find_texts(value, name)
    return values[0] if values else None


def _find_texts(value: Any, name: str) -> list[str]:
    """递归收集同名字段的全部非空文本值，并按出现顺序去重。"""
    found: list[str] = []

    def visit(current: Any) -> None:
        if isinstance(current, dict):
            direct = current.get(name)
            if direct is not None and not isinstance(direct, (dict, list)):
                normalized = str(direct).strip()
                if normalized and normalized not in found:
                    found.append(normalized)
            for child in current.values():
                visit(child)
        elif isinstance(current, list):
            for child in current:
                visit(child)

    visit(value)
    return found


def run() -> None:
    """命令行启动入口。"""
    import uvicorn

    settings = get_settings()
    logger.warning(
        "event=agent_start host=%s port=%s mcp_url=%s",
        settings.agent_host,
        settings.agent_port,
        settings.mcp_url,
    )
    uvicorn.run(
        "gas_forecast_agent.main:app",
        host=settings.agent_host,
        port=settings.agent_port,
        log_level="debug",
        access_log=True,
    )


if __name__ == "__main__":
    run()
