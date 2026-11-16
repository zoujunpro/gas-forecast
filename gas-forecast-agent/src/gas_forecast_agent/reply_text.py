"""用户正文的公共边界：协议对象不能作为聊天文字展示。"""
from __future__ import annotations

import ast
import json
import re

FALLBACK_REPLY = "本次回复格式异常，暂时无法展示说明。已执行的业务操作不受影响，请查看结果组件或稍后重试。"
ALLOWED_STATUSES = {"COMPLETED", "NEED_INFORMATION", "NEED_CLARIFICATION", "NOT_FOUND", "FAILED"}


def parse_reply(value: object, _depth: int = 0) -> tuple[str, str | None] | None:
    """返回纯正文和可选合法状态；畸形协议返回 None，不猜测业务成功。"""
    if _depth >= 4:
        return None
    if not isinstance(value, str) or not value.strip():
        return None
    text = value.strip()
    candidate = re.sub(r"^```(?:json|python)?\s*|\s*```$", "", text, flags=re.I).strip()
    for _ in range(3):
        parsed = None
        for parser in (json.loads, ast.literal_eval):
            try:
                parsed = parser(candidate)
                break
            except (ValueError, SyntaxError, TypeError):
                pass
        if isinstance(parsed, dict):
            status = parsed.get("status")
            if status is not None and (not isinstance(status, str) or status not in ALLOWED_STATUSES):
                return None
            message = parsed.get("message")
            if not isinstance(message, str) or not message.strip():
                return None
            inner = parse_reply(message, _depth + 1)
            return (inner[0], status or inner[1]) if inner else None
        if isinstance(parsed, str):
            candidate = parsed.strip()
            continue
        if parsed is not None:
            return None
        break
    # 保留普通标点、Markdown列表和业务词语，只拒绝明显的协议/异常输出。
    if (candidate.startswith(("{", "```json"))
        or re.search(r'''["'](?:status|message|resultType|errorCode|success)["']\s*:''', candidate)
        or re.search(r'''["'}]{2,}\s*$''', candidate)
        or re.search(r"\b(?:COMPLETED|RUNNING|PENDING|NEED_CLARIFICATION|NEED_INFORMATION|FAILED)\b", candidate)
        or "Traceback (most recent call last)" in candidate):
        return None
    # 兼容旧模型的多段自然语言转义，保留代码与路径原文。
    if ("`" not in candidate and "\\\\" not in candidate
        and candidate.count("\\n") >= 2 and re.search(r"[。！？；]", candidate)):
        candidate = candidate.replace("\\r\\n", "\n").replace("\\n", "\n")
    return candidate, None


def user_reply(value: object, fallback: str = FALLBACK_REPLY) -> str:
    parsed = parse_reply(value)
    return parsed[0] if parsed else fallback
