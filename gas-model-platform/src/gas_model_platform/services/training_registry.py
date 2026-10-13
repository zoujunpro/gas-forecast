"""当前进程训练运行态和并发容量管理。"""

from __future__ import annotations

from contextlib import contextmanager
from datetime import datetime, timezone
from threading import BoundedSemaphore, Lock
from typing import Iterator


class TrainingCapacityExceeded(RuntimeError):
    pass


class TrainingRegistry:
    """只记录轻量运行态，不缓存请求数据或模型训练集。"""

    def __init__(self, max_concurrency: int) -> None:
        self.max_concurrency = max(1, max_concurrency)
        self._semaphore = BoundedSemaphore(self.max_concurrency)
        self._lock = Lock()
        self._tasks: dict[str, dict[str, str]] = {}

    @contextmanager
    def running(self, batch_no: str, model_code: str) -> Iterator[None]:
        if not self._semaphore.acquire(blocking=False):
            raise TrainingCapacityExceeded("模型平台训练任务已满，请稍后重试")
        with self._lock:
            if batch_no in self._tasks:
                self._semaphore.release()
                raise ValueError(f"训练批次正在执行：{batch_no}")
            self._tasks[batch_no] = {
                "train_batch_no": batch_no,
                "model_code": model_code,
                "status": "RUNNING",
                "started_at": datetime.now(timezone.utc).isoformat(),
            }
        try:
            yield
        finally:
            # 成功或失败都及时清除运行态并释放槽位；平台不保存训练请求数据。
            with self._lock:
                self._tasks.pop(batch_no, None)
            self._semaphore.release()

    def tasks(self) -> list[dict[str, str]]:
        with self._lock:
            return [dict(task) for task in self._tasks.values()]

    def contains(self, batch_no: str) -> bool:
        with self._lock:
            return batch_no in self._tasks

    def capacity(self) -> dict[str, int]:
        running = len(self.tasks())
        return {
            "max_concurrency": self.max_concurrency,
            "running_count": running,
            "available_slots": max(0, self.max_concurrency - running),
        }
