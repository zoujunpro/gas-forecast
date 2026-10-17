"""将MCP状态和异常协议转换成LangGraph下一步动作。"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, NotRequired, TypedDict

from langgraph.graph import END, START, StateGraph
from langgraph.checkpoint.base import BaseCheckpointSaver
from langgraph.runtime import Runtime

from gas_forecast_agent.mcp import StreamableHttpMcpClient

from .decision import ExceptionDecider


class ExecutionMonitorState(TypedDict):
    conversation_no: str
    task_no: NotRequired[str]
    train_batch_no: NotRequired[str]
    forecast_batch_no: NotRequired[str]
    tool_result: NotRequired[dict[str, Any]]
    business_data: NotRequired[dict[str, Any]]
    status: NotRequired[str]
    next_action: NotRequired[str]
    allowed_actions: NotRequired[list[str]]
    reason: NotRequired[str]
    user_message: NotRequired[str | None]
    next_check_seconds: NotRequired[int | None]
    poll_count: NotRequired[int]


@dataclass(frozen=True)
class MonitorContext:
    mcp: StreamableHttpMcpClient
    decider: ExceptionDecider | None


class ExecutionMonitorGraph:
    """查询一次真实状态并决定下一步；等待由外部调度器再次唤醒，图内不死循环。"""

    def __init__(self, checkpointer: BaseCheckpointSaver | None = None) -> None:
        graph = StateGraph(ExecutionMonitorState, context_schema=MonitorContext)
        graph.add_node("load_status", self._load_status)
        graph.add_node("classify", self._classify)
        graph.add_node("model_decision", self._model_decision)
        graph.add_edge(START, "load_status")
        graph.add_edge("load_status", "classify")
        graph.add_conditional_edges("classify", lambda state: "model" if state.get("next_action") == "MODEL_DECISION" else "done",
                                    {"model": "model_decision", "done": END})
        graph.add_edge("model_decision", END)
        self._graph = graph.compile(checkpointer=checkpointer)

    async def invoke(self, state: ExecutionMonitorState, *, mcp: StreamableHttpMcpClient,
                     decider: ExceptionDecider | None = None, thread_id: str | None = None) -> ExecutionMonitorState:
        config = {"configurable": {"thread_id": thread_id}} if thread_id else None
        return await self._graph.ainvoke(state, config=config, context=MonitorContext(mcp, decider))

    async def _load_status(self, state: ExecutionMonitorState, runtime: Runtime[MonitorContext]) -> dict[str, Any]:
        if state.get("tool_result"):
            return {}
        if state.get("task_no"):
            tool, arguments = "task_status_get", {"taskNo": state["task_no"], "includeRecovery": True}
        elif state.get("forecast_batch_no"):
            tool, arguments = "forecast_status_get", {"conversationNo": state["conversation_no"], "forecastBatchNo": state["forecast_batch_no"]}
        elif state.get("train_batch_no"):
            tool, arguments = "training_status_get", {"conversationNo": state["conversation_no"], "trainBatchNo": state["train_batch_no"]}
        else:
            return {"status": "FAILED", "next_action": "ASK_USER", "allowed_actions": ["ASK_USER"],
                    "reason": "缺少taskNo、trainBatchNo或forecastBatchNo"}
        raw = await runtime.context.mcp.call_tool(tool, arguments)
        structured = raw.get("structuredContent") or raw.get("structured_content") or {}
        return {"tool_result": structured}

    async def _classify(self, state: ExecutionMonitorState) -> dict[str, Any]:
        if state.get("next_action"):
            return {}
        result = state.get("tool_result") or {}
        data = result.get("data") if isinstance(result.get("data"), dict) else {}
        status = str(data.get("status") or result.get("status") or "UNKNOWN").upper()
        action = str(data.get("nextAction") or "").upper()
        allowed = data.get("allowedActions") if isinstance(data.get("allowedActions"), list) else []
        if not action:
            action = self._fallback_action(status, bool(result.get("success", True)))
        if action == "RETRY":
            action = "ASK_USER"  # 创建新批次必须由用户确认
        poll_count = state.get("poll_count", 0)
        return {"business_data": data, "status": status, "next_action": action,
                "allowed_actions": [str(item).upper() for item in allowed] or [action],
                "reason": str(data.get("reason") or result.get("message") or ""),
                "next_check_seconds": self._backoff(poll_count) if action in {"WAIT", "QUERY_STATUS"} else None,
                "poll_count": poll_count + 1}

    async def _model_decision(self, state: ExecutionMonitorState, runtime: Runtime[MonitorContext]) -> dict[str, Any]:
        allowed = [action for action in state.get("allowed_actions", []) if action != "MODEL_DECISION"]
        if runtime.context.decider is None:
            return {"next_action": "ASK_USER" if "ASK_USER" in allowed else "FAIL",
                    "user_message": "任务发生业务异常，需要人工确认后续处理方式。"}
        try:
            decision = await runtime.context.decider.decide({"status": state.get("status"), "reason": state.get("reason"),
                                                             "data": state.get("business_data", {})}, allowed)
        except Exception as exc:
            return {"next_action": "ASK_USER" if "ASK_USER" in allowed else "FAIL",
                    "reason": f"异常决策模型调用失败：{exc}", "user_message": "任务异常暂时无法自动判断，需要人工确认。"}
        action = "ASK_USER" if decision.action == "RETRY" else decision.action
        return {"next_action": action, "reason": decision.reason, "user_message": decision.user_message}

    @staticmethod
    def _fallback_action(status: str, success: bool) -> str:
        if status in {"WAITING", "EXECUTING", "RUNNING", "WAITING_TRAINING", "CLAIMED", "PREPARING"}: return "WAIT"
        if status in {"SUCCESS", "COMPLETED"}: return "QUERY_RESULT"
        return "MODEL_DECISION" if not success or status in {"FAILED", "UNKNOWN"} else "FAIL"

    @staticmethod
    def _backoff(poll_count: int) -> int:
        return (10, 20, 30, 60, 120)[min(max(poll_count, 0), 4)]
