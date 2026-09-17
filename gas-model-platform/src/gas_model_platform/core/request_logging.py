from __future__ import annotations

import json
import logging
from time import perf_counter
from typing import Any
from uuid import uuid4

from gas_model_platform.core.config import settings
from gas_model_platform.core.logging import client_context, request_id_context

logger = logging.getLogger("gas_model_platform.http")

_SENSITIVE_PARTS = (
    "password",
    "passwd",
    "secret",
    "token",
    "authorization",
    "api_key",
    "apikey",
)


def _sanitize(value: Any) -> Any:
    if isinstance(value, dict):
        sanitized = {}
        for key, item in value.items():
            normalized = str(key).lower()
            sanitized[str(key)] = (
                "***"
                if any(part in normalized for part in _SENSITIVE_PARTS)
                else _sanitize(item)
            )
        return sanitized
    if isinstance(value, list):
        limit = max(settings.log_body_max_items, 0)
        items = [_sanitize(item) for item in value[:limit]]
        if len(value) > limit:
            return {
                "_type": "list",
                "count": len(value),
                "items": items,
                "truncated_items": len(value) - limit,
            }
        return items
    return value


def _render_body(body: bytes, content_type: str, enabled: bool) -> str:
    if not enabled:
        return "<disabled>"
    if not body:
        return "-"
    text = body.decode("utf-8", errors="replace")
    if "json" in content_type.lower():
        try:
            text = json.dumps(
                _sanitize(json.loads(text)),
                ensure_ascii=False,
                separators=(",", ":"),
            )
        except (TypeError, ValueError, json.JSONDecodeError):
            pass
    maximum = max(settings.log_body_max_length, 0)
    if len(text) > maximum:
        return f"{text[:maximum]}...<truncated total_chars={len(text)}>"
    return text


class RequestResponseLoggingMiddleware:
    """无损捕获 HTTP 入参和出参，并写入带请求上下文的访问日志。"""

    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        headers = {
            key.decode("latin-1").lower(): value.decode("latin-1")
            for key, value in scope.get("headers", [])
        }
        request_id = headers.get("x-request-id") or uuid4().hex
        forwarded_for = headers.get("x-forwarded-for", "").split(",", 1)[0].strip()
        peer = scope.get("client")
        client_ip = forwarded_for or (str(peer[0]) if peer else "-")
        caller = headers.get("x-user-id") or headers.get("x-user") or client_ip
        request_token = request_id_context.set(request_id)
        client_token = client_context.set(caller)
        request_body = bytearray()
        response_body = bytearray()
        response_status = 500
        response_content_type = ""
        started = perf_counter()

        async def receive_with_capture():
            message = await receive()
            if message["type"] == "http.request":
                request_body.extend(message.get("body", b""))
            return message

        async def send_with_capture(message):
            nonlocal response_status, response_content_type
            if message["type"] == "http.response.start":
                response_status = int(message["status"])
                response_headers = list(message.get("headers", []))
                if not any(key.lower() == b"x-request-id" for key, _ in response_headers):
                    response_headers.append((b"x-request-id", request_id.encode("ascii")))
                message["headers"] = response_headers
                for key, value in response_headers:
                    if key.lower() == b"content-type":
                        response_content_type = value.decode("latin-1")
            elif message["type"] == "http.response.body":
                response_body.extend(message.get("body", b""))
            await send(message)

        try:
            await self.app(scope, receive_with_capture, send_with_capture)
        except Exception:
            logger.exception(
                "http request failed method=%s path=%s query=%s",
                scope.get("method"),
                scope.get("path"),
                scope.get("query_string", b"").decode("latin-1"),
            )
            raise
        finally:
            elapsed_ms = (perf_counter() - started) * 1000
            request_content_type = headers.get("content-type", "")
            logger.info(
                "http completed method=%s path=%s query=%s status=%d duration_ms=%.2f "
                "request_body=%s response_body=%s",
                scope.get("method"),
                scope.get("path"),
                scope.get("query_string", b"").decode("latin-1"),
                response_status,
                elapsed_ms,
                _render_body(
                    bytes(request_body),
                    request_content_type,
                    settings.log_request_body_enabled,
                ),
                _render_body(
                    bytes(response_body),
                    response_content_type,
                    settings.log_response_body_enabled,
                ),
            )
            request_id_context.reset(request_token)
            client_context.reset(client_token)
