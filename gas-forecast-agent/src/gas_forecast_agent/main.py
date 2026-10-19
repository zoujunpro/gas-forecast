"""FastAPI 应用入口。"""

import asyncio
import logging
import sys
import time
import uuid
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
from fastapi.responses import JSONResponse

from gas_forecast_agent.skills import RequestSemanticExtractor, SkillAgent, SkillRegistry, SkillRouter
from gas_forecast_agent.analysis import ForecastAnalysisRequest, ForecastAnalysisResponse, ForecastResultAnalyzer
from gas_forecast_agent.config import Settings, get_settings
from gas_forecast_agent.llm import build_llm_client
from gas_forecast_agent.mcp import McpClientError, StreamableHttpMcpClient
from gas_forecast_agent.presentation import PresentationPlanner
from gas_forecast_agent.schemas import AgentType, ChatRequest, ChatResponse


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


def _tool_data(raw: dict[str, Any]) -> dict[str, Any]:
    structured = raw.get("structuredContent") or raw.get("structured_content") or {}
    if not structured.get("success", False):
        raise McpClientError(str(structured.get("message") or "Java工具调用失败"))
    data = structured.get("data")
    return data if isinstance(data, dict) else {}


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


@app.post("/api/v1/chat")
async def browser_chat(
    request: ChatRequest,
    client: Annotated[StreamableHttpMcpClient, Depends(get_mcp_client)],
    settings: Annotated[Settings, Depends(get_settings)],
    trace_id: Annotated[str | None, Header(alias="x-traceId")] = None,
) -> Any:
    """浏览器对话入口；Python 管理对话轮次，Java 负责鉴权和持久化。"""
    turn_id = request.turn_id or uuid.uuid4().hex
    try:
        prepared = _tool_data(await client.call_tool("conversation_prepare", {
            "conversationNo": request.conversation_no,
            "forecastBatchNo": request.forecast_batch_no,
            "turnId": turn_id,
            "message": request.message,
            "agentType": request.agent_type.value if request.agent_type else None,
            "metadata": request.metadata,
        }))
    except McpClientError as exc:
        status = exc.http_status if exc.http_status in {401, 403} else 503
        message = "登录已过期，请重新登录" if status == 401 else "无权使用智能助手" if status == 403 else "业务服务暂时不可用，请稍后重试"
        return JSONResponse(status_code=status, content={"code": str(status), "message": message, "data": None})
    conversation_no = str(prepared["conversationNo"])
    agent_request = request.model_copy(update={
        "conversation_no": conversation_no,
        "history": prepared.get("history") or [],
        "metadata": prepared.get("metadata") or {},
    })
    try:
        reply = await _run_agent(conversation_no, agent_request, client, settings, trace_id)
    except Exception as exc:
        logger.exception("event=browser_chat_failed trace_id=%s conversation_no=%s", trace_id or "-", conversation_no)
        answer = "本轮智能体处理发生系统异常，未确认创建新的业务任务；已经创建的训练或预测任务不受影响。请稍后重试，如仍失败请提供当前会话时间排查。"
        await _append_reply_safely(client, conversation_no, turn_id, "FAILED", answer, None, {}, {})
        return {"code": "0000", "message": "成功", "data": {
            "conversationNo": conversation_no, "status": "FAILED", "answer": answer,
        }}

    execution = reply.execution_result or {}
    payload = {
        "progress": reply.progress,
        "forecastResult": reply.forecast_result,
        "executionResult": execution,
        "analysis": None,
        "suggestedQuestions": [],
        "presentation": reply.presentation,
    }
    persisted = await _append_reply_safely(
        client, conversation_no, turn_id, reply.status, reply.message, reply.skill_name, execution, payload
    )
    return {"code": "0000", "message": "成功", "data": {
        "conversationNo": conversation_no,
        "status": reply.status,
        "answer": reply.message,
        "trainBatchNo": persisted.get("lastTrainBatchNo") or _find_text(execution, "trainBatchNo"),
        "forecastBatchNo": persisted.get("lastForecastBatchNo") or _find_text(execution, "forecastBatchNo"),
        "progress": reply.progress,
        "forecastResult": reply.forecast_result,
        "executionResult": execution,
        "presentation": reply.presentation,
        "analysis": None,
        "suggestedQuestions": [],
        "skillName": reply.skill_name,
        "skillSelection": reply.skill_selection,
        "parameters": reply.parameters,
        "executedTool": reply.executed_tool,
        "availableTools": reply.available_tools,
    }}


async def _run_agent(
    conversation_no: str,
    request: ChatRequest,
    client: StreamableHttpMcpClient,
    settings: Settings,
    trace_id: str | None = None,
) -> ChatResponse:
    """选择 Skill，让模型在工具白名单内逐步处理请求。"""
    try:
        started_at = time.monotonic()
        memory = await _read_memory(client, conversation_no)
        memory_ms = round((time.monotonic() - started_at) * 1000)
        selection = await SkillRouter(settings, skill_registry).select(request.message, memory, request.metadata)
        routing_ms = round((time.monotonic() - started_at) * 1000) - memory_ms
        skill = skill_registry.get(selection.skill_name)
        semantics = await RequestSemanticExtractor(settings).extract(request.message, skill.name)
        semantics_ms = round((time.monotonic() - started_at) * 1000) - memory_ms - routing_ms
        result = await SkillAgent(settings).run(
            conversation_no=conversation_no,
            skill=skill,
            message=request.message,
            history=request.history[-12:],
            memory=memory,
            metadata=request.metadata,
            semantics=semantics,
            mcp=client,
        )
        agent_ms = round((time.monotonic() - started_at) * 1000) - memory_ms - routing_ms - semantics_ms
        logger.info(
            "event=agent_timing conversation_no=%s skill=%s tool=%s memory_ms=%s routing_ms=%s semantics_ms=%s agent_ms=%s total_ms=%s",
            conversation_no, skill.name, result.executed_tool or "-", memory_ms, routing_ms, semantics_ms,
            agent_ms, round((time.monotonic() - started_at) * 1000),
        )
        try:
            await _write_memory(client, conversation_no, skill.name, result)
        except Exception:
            # 业务工具可能已经成功，记忆失败不能把整次请求伪装成业务失败并诱发重复提交。
            logger.exception("event=agent_memory_write_failed conversation_no=%s", conversation_no)
    except Exception as exc:
        logger.exception(
            "event=agent_flow_error trace_id=%s conversation_no=%s message_length=%s",
            trace_id or "-",
            conversation_no,
            len(request.message),
        )
        raise HTTPException(status_code=503, detail=f"智能体主流程执行失败：{exc}") from exc

    agent_type = request.agent_type
    if agent_type is None and skill.name == "customer-gas-forecast":
        agent_type = AgentType.WINTER_SUPPLY if "冬季" in request.message or "保供" in request.message else \
            AgentType.MONTHLY_SALES if "月度" in request.message or "销量" in request.message else AgentType.SHORT_TERM
    presentation = PresentationPlanner.plan(result.executed_tool, result.execution_result or {})
    return ChatResponse(
        conversation_no=conversation_no,
        agent_type=agent_type,
        status=result.status,
        message=result.message,
        skill_name=skill.name,
        skill_selection={"skill_name": skill.name, "confidence": selection.confidence, "reason": selection.reason},
        parameters=result.parameters,
        memory_loaded=True,
        available_tools=result.available_tools,
        executed_tool=result.executed_tool,
        execution_result=result.execution_result,
        presentation=presentation.get("presentation"),
        progress=presentation.get("progress"),
        forecast_result=presentation.get("forecast_result"),
        decision_reason=selection.reason,
    )


async def _read_memory(client: StreamableHttpMcpClient, conversation_no: str) -> dict[str, Any]:
    raw = await client.call_tool("memory_get", {"conversationNo": conversation_no})
    structured = raw.get("structuredContent") or raw.get("structured_content") or {}
    data = structured.get("data")
    return data if isinstance(data, dict) else {}


async def _write_memory(client: StreamableHttpMcpClient, conversation_no: str, skill_name: str, result: Any) -> None:
    values = {
        "currentSkill": skill_name,
        "currentParameters": result.parameters,
        "lastTool": result.executed_tool,
        "lastExecution": result.execution_result,
        "pendingAction": {"tool": result.pending_tool, "parameters": result.parameters}
        if result.pending_tool else None,
    }
    await client.call_tool(
        "memory_update",
        {"conversationNo": conversation_no, "operation": "UPSERT", "values": values},
    )


async def _append_reply_safely(
    client: StreamableHttpMcpClient,
    conversation_no: str,
    turn_id: str,
    status: str,
    answer: str,
    skill_name: str | None,
    execution_result: dict[str, Any],
    payload: dict[str, Any],
) -> dict[str, Any]:
    for attempt in range(3):
        try:
            return _tool_data(await client.call_tool("conversation_reply_append", {
                "conversationNo": conversation_no,
                "turnId": turn_id,
                "status": status,
                "answer": answer,
                "skillName": skill_name,
                "executionResult": execution_result,
                "payload": payload,
            }))
        except Exception:
            logger.exception(
                "event=conversation_reply_append_failed conversation_no=%s turn_id=%s attempt=%s",
                conversation_no, turn_id, attempt + 1,
            )
            if attempt < 2:
                await asyncio.sleep(0.2 * (attempt + 1))
    # turnId 唯一键使重试安全；最终失败也不能把已成功的业务工具改写为失败。
    return {}


def _find_text(value: Any, name: str) -> str | None:
    if isinstance(value, dict):
        direct = value.get(name)
        if direct is not None and str(direct).strip():
            return str(direct)
        for child in value.values():
            found = _find_text(child, name)
            if found:
                return found
    elif isinstance(value, list):
        for child in value:
            found = _find_text(child, name)
            if found:
                return found
    return None


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
