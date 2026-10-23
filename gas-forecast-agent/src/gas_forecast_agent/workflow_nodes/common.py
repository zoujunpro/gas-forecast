"""工作流节点共用的小型无状态工具。"""

from __future__ import annotations

import time


def elapsed_ms(started_at: float) -> int:
    """计算从指定单调时钟时间点开始经过的毫秒数。"""
    return round((time.monotonic() - started_at) * 1000)
