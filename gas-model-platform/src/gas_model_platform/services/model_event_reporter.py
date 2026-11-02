"""模型平台向 Java 上送真实执行过程事件。"""

from __future__ import annotations

import json
import logging
from contextlib import contextmanager
from dataclasses import dataclass
from threading import Event, Thread
from typing import Any
from urllib.error import URLError
from urllib.request import Request, urlopen

from gas_model_platform.core.config import Settings


logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class ModelEventHandle:
    event_id: int


class ModelEventReporter:
    """尽力回调 Java；事件服务不可用时不应中断模型计算。"""

    def __init__(self, settings: Settings, run_no: str | None, forecast_no: str, task_detail_no: str | None) -> None:
        self._url = (settings.event_callback_url or "").rstrip("/")
        self._run_no = run_no
        self._forecast_no = forecast_no
        self._task_detail_no = task_detail_no

    @property
    def enabled(self) -> bool:
        return bool(self._url and self._run_no)

    def start(self, code: str, message: str, data: dict[str, Any] | None = None) -> ModelEventHandle | None:
        if not self.enabled:
            return None
        payload = {
            "eventCode": code,
            "message": message,
            "eventDataJson": self._json(data),
            "forecastNo": self._forecast_no,
            "taskDetailNo": self._task_detail_no,
        }
        try:
            response = self._request("POST", f"/runs/{self._run_no}/events", payload)
            event_id = response.get("data", {}).get("id")
            return ModelEventHandle(int(event_id)) if event_id is not None else None
        except (OSError, ValueError, URLError) as exc:
            logger.warning("model_event_create_failed run_no=%s code=%s detail=%s", self._run_no, code, exc)
            return None

    def success(self, handle: ModelEventHandle | None, message: str, data: dict[str, Any] | None = None) -> None:
        self._complete(handle, "SUCCESS", message, data, None)

    def failure(self, handle: ModelEventHandle | None, message: str, error: Exception) -> None:
        self._complete(handle, "FAILED", message, None, error)

    def progress(self, handle: ModelEventHandle | None, message: str, data: dict[str, Any] | None = None) -> None:
        """更新同一条 RUNNING 事件；可用于真实进度或不改变文案的长任务心跳。"""
        self._complete(handle, "RUNNING", message, data, None)

    @contextmanager
    def heartbeat(self, handle: ModelEventHandle | None, message: str,
                  data: dict[str, Any] | None = None, interval_seconds: int = 25):
        """长步骤保持 RUNNING 的存活信号，退出上下文后立即停止，不新增事件行。"""
        if handle is None or not self.enabled:
            yield
            return
        stopped = Event()

        def send_heartbeat() -> None:
            while not stopped.wait(interval_seconds):
                self.progress(handle, message, data)

        worker = Thread(target=send_heartbeat, name=f"model-event-heartbeat-{handle.event_id}", daemon=True)
        worker.start()
        try:
            yield
        finally:
            stopped.set()
            worker.join(timeout=1)

    def _complete(self, handle: ModelEventHandle | None, status: str, message: str,
                  data: dict[str, Any] | None, error: Exception | None) -> None:
        if handle is None or not self.enabled:
            return
        payload = {
            "status": status,
            "message": message,
            "eventDataJson": self._json(data),
            "errorCode": "MODEL_EXECUTION_FAILED" if error else None,
            "errorMessage": str(error) if error else None,
        }
        try:
            self._request("PATCH", f"/runs/{self._run_no}/events/{handle.event_id}", payload)
        except (OSError, ValueError, URLError) as exc:
            logger.warning("model_event_update_failed run_no=%s event_id=%s detail=%s", self._run_no, handle.event_id, exc)

    def _request(self, method: str, path: str, payload: dict[str, Any]) -> dict[str, Any]:
        request = Request(
            f"{self._url}{path}",
            data=json.dumps(payload, ensure_ascii=False, default=str).encode("utf-8"),
            method=method,
            headers={"Content-Type": "application/json"},
        )
        with urlopen(request, timeout=3) as response:  # nosec B310: URL is deployment configuration
            return json.loads(response.read().decode("utf-8"))

    @staticmethod
    def _json(data: dict[str, Any] | None) -> str | None:
        return json.dumps(data, ensure_ascii=False, default=str) if data is not None else None
