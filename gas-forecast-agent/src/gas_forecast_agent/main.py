"""FastAPI 应用入口。"""

import logging
import json
import re
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

from fastapi import Depends, FastAPI, Header
from fastapi import HTTPException
from fastapi.responses import StreamingResponse
from gas_forecast_agent.config import Settings, get_settings
from gas_forecast_agent.internal_client import InternalAgentClient
from gas_forecast_agent.mcp import StreamableHttpMcpClient
from gas_forecast_agent.run_event_reporter import RunEventReporter
from gas_forecast_agent.schemas import (
    AgentRuntimeRequest,
    AgentRuntimeResponse,
    AgentHistoryMessage,
    AgentHistoryReferences,
    AgentChatRequest,
    AgentChatResponse,
    AgentStatus,
    ContentBlock,
    ContentBlockType,
    ConversationMemory,
    ErrorInfo,
    MessageReferenceResponse,
    PythonClarification,
    ReferenceRelation,
    ForecastAnalysisRequest,
    ForecastAnalysisResponse,
)
from gas_forecast_agent.llm import build_llm_client
from gas_forecast_agent.recommendation import select_forecast_questions, validated_questions
from gas_forecast_agent.skills import SemanticExtractionError, SkillRegistry
from gas_forecast_agent.workflow import run_agent_graph


logger = logging.getLogger(__name__)

app = FastAPI(title="Gas Forecast Agent", version="0.1.0")
skill_registry = SkillRegistry()

def get_mcp_client(
    settings: Annotated[Settings, Depends(get_settings)],
    authorization: Annotated[str | None, Header()] = None,
    access_token: Annotated[str | None, Header(alias="X-Access-Token")] = None,
    trace_id: Annotated[str | None, Header(alias="x-traceId")] = None,
    agent_run_no: Annotated[str | None, Header(alias="X-Agent-Run-No")] = None,
) -> StreamableHttpMcpClient:
    """按请求创建 MCP 客户端；原样透传用户凭证，由 Java 独立鉴权。"""
    headers = settings.mcp_headers(authorization, access_token)
    if trace_id:
        headers["x-traceId"] = trace_id
    if agent_run_no:
        headers["X-Agent-Run-No"] = agent_run_no
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


@app.post("/api/v1/internal/forecast-analysis", response_model=ForecastAnalysisResponse, response_model_by_alias=True)
async def forecast_analysis(
    request: ForecastAnalysisRequest,
    settings: Annotated[Settings, Depends(get_settings)],
) -> ForecastAnalysisResponse:
    """依据本次预测的可信快照生成研判和三个可点击追问。"""
    selected_questions = select_forecast_questions(request.context)
    if len(selected_questions) != 3:
        raise HTTPException(status_code=422, detail="当前预测数据不足以生成三个可回答的推荐问题")
    question_candidates = [item.__dict__ for item in selected_questions]
    fallback_analysis = str(request.context.get("ruleAnalysis") or "本次预测已完成，请结合预测结果和回测指标进行判断。").strip()
    if not settings.llm_api_key or not settings.llm_model:
        return ForecastAnalysisResponse(analysis=fallback_analysis, questions=question_candidates)
    prompt = (
        "你是燃气销售人员的用气预测分析助手。仅依据用户提供的本次预测数据生成业务研判。"
        "请写一段约80至110个汉字、可供燃气销售人员阅读的简短解读，按以下顺序说明："
        "一、预测期用气量相对历史期或去年同期是升高、降低还是平稳，并指出关键日期或波动区间；"
        "二、结合已提供的销量、日期周期、天气和预测点说明可能影响因素；"
        "三、根据MAPE等回测指标说明本次预测准确性、可信程度及使用边界。"
        "不要给出客户维护、营销、经营措施、供气保障或客户关系建议。"
        "销量、天气、回测指标及预测点可作为事实；价格、订单、停产、客户计划、政策和市场变化没有数据证明时，"
        "只能表述为‘待核实假设’或‘建议进一步核实’，绝不能虚构具体事件。"
        "当MAPE较高时，明确说明预测仅宜作趋势参考。"
        "questions由系统业务规则预先选定。你只能优化每项text的自然表达，必须原样保留code和category，"
        "不得新增、删除、替换问题类别。每个text只问一件事、以问号结尾，包含标点绝对不超过30个字符。"
        "问题应尽量引用输入中的实际天数、趋势或MAPE，但不得虚构数据。"
        "只输出JSON对象："
        "{\"analysis\":\"...\",\"questions\":[{\"code\":\"...\",\"category\":\"...\",\"text\":\"...\"}]}。"
    )
    try:
        client = build_llm_client(settings, minimum_timeout=45)
        response = await client.chat.completions.create(
            model=settings.llm_model,
            temperature=0.45,
            response_format={"type": "json_object"},
            messages=[
                {"role": "system", "content": prompt},
                {"role": "user", "content": json.dumps({"forecast": request.context, "questionCandidates": question_candidates}, ensure_ascii=False)},
            ],
        )
        result = ForecastAnalysisResponse.model_validate_json(response.choices[0].message.content or "{}")
        questions = validated_questions(result.questions, selected_questions)
        analysis = re.sub(r"\s+", " ", result.analysis.strip())
        # 前端两处共用该字段；服务端兜底限制长度，避免模型偶尔输出成长报告。
        if len(analysis) > 110:
            clipped = analysis[:110]
            sentence_end = max(clipped.rfind("。"), clipped.rfind("！"), clipped.rfind("？"))
            analysis = (clipped[:sentence_end + 1] if sentence_end >= 66 else clipped).strip()
        return ForecastAnalysisResponse(analysis=analysis, questions=questions)
    except Exception as exc:
        logger.exception("event=forecast_analysis_failed forecast_no=%s", request.forecast_no)
        return ForecastAnalysisResponse(analysis=fallback_analysis, questions=question_candidates)


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
    # 2. conversationContext 是 Java 从数据库加载的可信会话快照，包含近期消息
    #    以及每条消息关联的执行任务。
    conversation_context = request.conversation_context

    # 4. 单独提取历史消息的业务对象引用并保留消息归属关系。
    #    普通对话历史只传 role/content；引用放入 metadata，供 Skill 判断用户说的
    #    “这个任务”“上次预测”等对象具体指向哪个业务编号。
    history_references = []
    # 5. 将对外的 Java 请求适配为现有 Agent 编排层使用的内部请求。
    #    仅 USER/ASSISTANT 消息进入自然语言对话历史；SYSTEM/TOOL 消息不会在此处
    #    直接注入模型，以免外部持久化内容改变 Python 侧的系统指令或工具协议。
    agent_request = AgentRuntimeRequest(
        turn_id=request.turn_id,
        conversation_no=request.conversation_no,
        run_no=request.run_no,
        agent_code=request.agent_code,
        message=request.message,
        input_type=request.input_type,
        interaction_id=request.interaction_id,
        reply_to_turn_id=request.reply_to_turn_id,
        conversation_summary=conversation_context.summary,
        current_business_context=request.business_context,
        history_references=history_references,
        recent_forecast_tasks=conversation_context.recent_forecast_tasks,
        memory=ConversationMemory(pending_operation=conversation_context.pending_operation),
        history=[
            AgentHistoryMessage(role=item.role.lower(), content=item.content)
            for item in conversation_context.recent_messages
            if item.role.upper() in {"USER", "ASSISTANT"}
        ],
    )
    # 事件上送
    reporter = RunEventReporter(internal_client, request.run_no)
    understanding_event = await reporter.start(_understanding_event_message(request.message))

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
        await reporter.succeed(understanding_event, "已理解您的需求，正在开始处理。")
    except SemanticExtractionError:
        logger.exception(
            "event=agent_semantics_failed trace_id=%s conversation_no=%s run_no=%s",
            trace_id or "-",
            request.conversation_no,
            request.run_no,
        )
        answer = "本轮请求的意图识别暂时失败，尚未执行任何业务工具。请稍后重试。"
        await reporter.fail(understanding_event, "未能完成请求理解，尚未执行任何业务操作。",
                            error_code="SEMANTIC_EXTRACTION_FAILED")
        return AgentChatResponse(
            conversation_no=request.conversation_no,
            turn_id=request.turn_id,
            run_no=request.run_no,
            agent_code=request.agent_code,
            status=AgentStatus.FAILED,
            blocks=[ContentBlock(type=ContentBlockType.MARKDOWN, data={"content": answer})],
            error=ErrorInfo(code="SEMANTIC_EXTRACTION_FAILED"),
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
        await reporter.fail(understanding_event, "智能体处理发生异常，正在停止本轮处理。",
                            error_code="AGENT_EXECUTION_ERROR")
        return AgentChatResponse(
            conversation_no=request.conversation_no,
            turn_id=request.turn_id,
            run_no=request.run_no,
            agent_code=request.agent_code,
            status=AgentStatus.FAILED,
            blocks=[ContentBlock(type=ContentBlockType.MARKDOWN, data={"content": answer})],
            error=ErrorInfo(code="AGENT_EXECUTION_ERROR"),
        )

    # 8. 将内部运行结果规范化为 Java 会话服务约定的响应结构。
    #    execution_result 可能为空；统一使用空字典可以简化后续状态和编号提取。
    execution = reply.execution_result.to_payload() if reply.execution_result else {}

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
        blocks=_content_blocks(reply, execution, references),
        # WAITING 状态返回结构化补充项；其他状态通常为空。
        clarification=_clarification(reply, status),
        error=ErrorInfo(code="AGENT_RESULT_FAILED") if status == AgentStatus.FAILED else None,
    )


@app.post("/api/v1/internal/chat/stream")
async def agent_chat_stream(
    request: AgentChatRequest,
    client: Annotated[StreamableHttpMcpClient, Depends(get_mcp_client)],
    internal_client: Annotated[InternalAgentClient, Depends(get_internal_agent_client)],
    settings: Annotated[Settings, Depends(get_settings)],
    trace_id: Annotated[str | None, Header(alias="x-traceId")] = None,
) -> StreamingResponse:
    """推送真实处理阶段；最终 done 始终携带完整、可持久化的 Agent 结果。"""
    async def events():
        # 当前编排在 agent_chat 完成后才会拿到完整的理解结果，不能伪造后续阶段。
        # 仅推送已真实开始的阶段，避免用户误以为已经创建或执行了预测任务。
        yield f"event: progress\ndata: {json.dumps('正在理解您的需求…', ensure_ascii=False)}\n\n"
        result = await agent_chat(request, client, internal_client, settings, trace_id)
        yield "event: done\ndata: " + result.model_dump_json(by_alias=True) + "\n\n"
    return StreamingResponse(events(), media_type="text/event-stream")


def _understanding_event_message(message: str) -> str:
    """通用起始事件不预判业务类型，具体动作由真实工具事件说明。"""
    return "正在理解您的需求。"


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
    relation = (ReferenceRelation.CREATED if executed_tool == "forecast_start"
                else ReferenceRelation.REFERENCED if executed_tool == "forecast_result_by_no"
                else ReferenceRelation.DISPLAYED)
    status = _find_text(execution_result, "status")
    references: list[MessageReferenceResponse] = []
    for task_no in _find_texts(execution_result, "taskNo"):
        references.append(MessageReferenceResponse(task_no=task_no, relation_type=relation, status=status))
    return references


def _clarification(
    reply: AgentRuntimeResponse,
    status: AgentStatus,
) -> PythonClarification | None:
    """把 Agent 的缺失参数转换成前端可渲染的结构化补充请求。"""
    if (
        status != AgentStatus.WAITING
        or not reply.resolution_issues
        or reply.memory_patch.pending_operation is None
    ):
        return None
    issue = next((item for item in reply.resolution_issues if item.required), reply.resolution_issues[0])
    return PythonClarification(
        field=issue.field,
        reason=issue.reason,
        input_type=issue.input_type,
        options=issue.options,
        pending_operation=reply.memory_patch.pending_operation,
    )


def _content_blocks(
    reply: AgentRuntimeResponse,
    execution: dict[str, object],
    references: list[MessageReferenceResponse],
) -> list[ContentBlock]:
    """把Python运行结果转换成Java和Vue统一使用的内容块。"""
    blocks = [ContentBlock(type=ContentBlockType.MARKDOWN, data={"content": reply.message})]
    if reply.presentation is not None:
        presentation = reply.presentation.model_dump(mode="json", by_alias=True, exclude_none=True)
        block_type = ContentBlockType.CHART if str(reply.presentation.type).upper() == "CHART" else ContentBlockType.TABLE
        blocks.append(ContentBlock(type=block_type, data=presentation))
    if execution:
        blocks.append(ContentBlock(type=ContentBlockType.TABLE, data=execution))
    blocks.extend(ContentBlock(
        type=ContentBlockType.TASK_CARD,
        data=reference.model_dump(mode="json", by_alias=True, exclude_none=True),
    ) for reference in references)
    return blocks


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
