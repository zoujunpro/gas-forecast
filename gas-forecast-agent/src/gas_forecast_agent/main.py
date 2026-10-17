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

from gas_forecast_agent.capabilities import CapabilityRegistry
from gas_forecast_agent.conversation import ConversationResponder
from gas_forecast_agent.analysis import ForecastAnalysisRequest, ForecastAnalysisResponse, ForecastResultAnalyzer
from gas_forecast_agent.checkpoint import build_checkpoint_saver
from gas_forecast_agent.config import Settings, get_settings
from gas_forecast_agent.entities import EntityResolver
from gas_forecast_agent.execution import ExceptionDecider, QwenExceptionDecider
from gas_forecast_agent.graph import AgentMainFlow, MainFlowError
from gas_forecast_agent.intent import IntentRecognizer, build_intent_recognizer
from gas_forecast_agent.llm import build_llm_client
from gas_forecast_agent.mcp import McpClientError, StreamableHttpMcpClient
from gas_forecast_agent.router import AgentRouter
from gas_forecast_agent.schemas import AgentType, ChatRequest, ChatResponse


logger = logging.getLogger(__name__)

app = FastAPI(title="Gas Forecast Agent", version="0.1.0")
router = AgentRouter()
capability_registry = CapabilityRegistry()
main_flow = AgentMainFlow()
entity_resolver = EntityResolver()


def get_mcp_client(
    settings: Annotated[Settings, Depends(get_settings)],
    authorization: Annotated[str | None, Header()] = None,
    user_id: Annotated[str | None, Header(alias="x-user")] = None,
    trace_id: Annotated[str | None, Header(alias="x-traceId")] = None,
) -> StreamableHttpMcpClient:
    """按请求创建 MCP 客户端；优先透传当前登录用户身份。"""
    headers = settings.mcp_headers(authorization)
    if user_id:
        headers["x-user"] = user_id
    if trace_id:
        headers["x-traceId"] = trace_id
    return StreamableHttpMcpClient(
        settings.mcp_url,
        headers,
        settings.mcp_timeout_seconds,
    )


def get_intent_recognizer(settings: Annotated[Settings, Depends(get_settings)]) -> IntentRecognizer:
    """根据配置选择结构化大模型识别或本地规则兜底。"""
    return build_intent_recognizer(settings, capability_registry)


def get_exception_decider(settings: Annotated[Settings, Depends(get_settings)]) -> ExceptionDecider | None:
    """复用项目千问配置；未配置模型时异常流程安全降级为人工确认。"""
    return QwenExceptionDecider(settings) if settings.llm_api_key and settings.llm_model else None


def get_checkpointer(settings: Annotated[Settings, Depends(get_settings)]):
    """返回只操作agent_graph_checkpoint_tb的LangGraph Checkpointer。"""
    return build_checkpoint_saver(settings)


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


@app.get("/api/v1/capabilities")
async def list_capabilities(include_unavailable: bool = True) -> dict[str, Any]:
    """返回业务能力目录，供管理页面、意图路由和缺口检查使用。"""
    if include_unavailable:
        return capability_registry.summary()
    capabilities = [item.model_dump(mode="json", by_alias=True) for item in capability_registry.list(False)]
    return {"total": len(capabilities), "capabilities": capabilities}


@app.post("/api/v1/conversations/{conversation_no}/messages", response_model=ChatResponse)
async def chat(
    conversation_no: str,
    request: ChatRequest,
    client: Annotated[StreamableHttpMcpClient, Depends(get_mcp_client)],
    recognizer: Annotated[IntentRecognizer, Depends(get_intent_recognizer)],
    exception_decider: Annotated[ExceptionDecider | None, Depends(get_exception_decider)],
    checkpointer: Annotated[Any, Depends(get_checkpointer)],
    settings: Annotated[Settings, Depends(get_settings)],
    user_id: Annotated[str | None, Header(alias="x-user")] = None,
    trace_id: Annotated[str | None, Header(alias="x-traceId")] = None,
) -> ChatResponse:
    """统一会话入口：读取记忆、识别意图、匹配能力并保存当前上下文。"""
    try:
        state = await main_flow.invoke(
            {
                "conversation_no": conversation_no,
                "message": request.message,
                "history": request.history[-12:],
                "requested_agent": request.agent_type,
                "bound_context": request.metadata,
            },
            mcp=client,
            recognizer=recognizer,
            capabilities=capability_registry,
            entity_resolver=entity_resolver,
            exception_decider=exception_decider,
            checkpointer=checkpointer,
            conversation_responder=ConversationResponder(settings),
        )
    except MainFlowError as exc:
        logger.exception(
            "event=agent_flow_failed user_id=%s trace_id=%s conversation_no=%s message_length=%s",
            user_id or "-",
            trace_id or "-",
            conversation_no,
            len(request.message),
        )
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    except Exception as exc:
        logger.exception(
            "event=agent_flow_error user_id=%s trace_id=%s conversation_no=%s message_length=%s",
            user_id or "-",
            trace_id or "-",
            conversation_no,
            len(request.message),
        )
        raise HTTPException(status_code=503, detail=f"智能体主流程执行失败：{exc}") from exc

    scenario = (state.get("intent") or {}).get("scenario")
    agent_type = request.agent_type or {
        "MONTHLY_SALES": AgentType.MONTHLY_SALES,
        "WINTER_SUPPLY": AgentType.WINTER_SUPPLY,
        "SHORT_TERM": AgentType.SHORT_TERM,
    }.get(scenario)
    return ChatResponse(
        conversation_no=conversation_no,
        agent_type=agent_type,
        status=state["status"],
        message=state["response_message"],
        capability_code=state.get("capability_code"),
        intent=state.get("intent"),
        parameters=state.get("parameters", {}),
        entity_resolution=state.get("entity_resolution"),
        missing_parameters=state.get("missing_parameters", []),
        memory_loaded=True,
        available_tools=state.get("available_tools", []),
        executed_tool=state.get("executed_tool"),
        execution_result=state.get("execution_result"),
        presentation=state.get("presentation"),
        progress=state.get("progress"),
        forecast_result=state.get("forecast_result"),
        next_action=state.get("next_action"),
        next_check_seconds=state.get("next_check_seconds"),
        decision_reason=state.get("decision_reason"),
    )


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
