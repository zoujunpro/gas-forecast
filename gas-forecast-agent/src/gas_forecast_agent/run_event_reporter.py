"""Python 智能体侧的公共运行事件记录器。"""

import logging
from dataclasses import dataclass

from gas_forecast_agent.internal_client import InternalAgentClient


logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class RunEventHandle:
    """Java 持久化后返回的事件标识，用于更新同一条事件。"""

    event_id: int


class RunEventReporter:
    """以 run_no 为边界记录真实智能体动作；记录失败不应中断主业务流程。"""

    def __init__(self, client: InternalAgentClient, run_no: str) -> None:
        self._client = client
        self._run_no = run_no

    async def start(self, message: str, *, event_code: str = "") -> RunEventHandle | None:
        try:
            event = await self._client.create_run_event(self._run_no, {
                "eventCode": event_code or "AGENT_ACTION",
                "message": message,
                "status": "RUNNING",
            })
            event_id = event.get("id")
            return RunEventHandle(int(event_id)) if event_id is not None else None
        except Exception:
            logger.exception("event=agent_run_event_create_failed run_no=%s", self._run_no)
            return None

    async def succeed(self, handle: RunEventHandle | None, message: str) -> None:
        await self._finish(handle, "SUCCESS", message)

    async def progress(self, handle: RunEventHandle | None, message: str) -> None:
        """更新同一条 RUNNING 事件，供长查询或工具调用报告真实进度/心跳。"""
        await self._finish(handle, "RUNNING", message)

    async def fail(self, handle: RunEventHandle | None, message: str, *, error_code: str) -> None:
        await self._finish(handle, "FAILED", message, error_code=error_code, error_message=message)

    async def _finish(self, handle: RunEventHandle | None, status: str, message: str,
                      *, error_code: str | None = None, error_message: str | None = None) -> None:
        if handle is None:
            return
        try:
            await self._client.update_run_event(self._run_no, handle.event_id, {
                "status": status,
                "message": message,
                "errorCode": error_code,
                "errorMessage": error_message,
            })
        except Exception:
            logger.exception("event=agent_run_event_update_failed run_no=%s event_id=%s", self._run_no, handle.event_id)
