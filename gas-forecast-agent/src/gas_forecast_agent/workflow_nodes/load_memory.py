"""load_memory 节点。"""

from __future__ import annotations

from typing import Any

from gas_forecast_agent.workflow_nodes.state import AgentGraphState


def load_memory(state: AgentGraphState) -> dict[str, Any]:
    """
    加载本轮需要使用的结构化会话记忆。

    输入：
        state["request"].memory：Java 从 ai_conversation_tb.memory_json 加载后，
            随内部会话请求传给 Python 的记忆快照。

    处理：
        复制一份记忆字典写入 LangGraph State，避免后续节点直接修改请求对象。
        其中可能包含当前 Skill、已确认参数、上次工具结果和待确认动作。

    输出：
        memory：供 understand_request 和 execute_action 继续使用。

    本节点不查询数据库、不加载聊天历史，也不进行消息压缩；这些工作由 Java
    在调用 Python 前完成。
    """
    request = state["request"]
    return {"memory": dict(request.memory)}

