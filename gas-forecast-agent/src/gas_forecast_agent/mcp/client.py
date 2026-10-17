"""连接 Java Streamable HTTP MCP 服务。"""

import logging
from typing import Any

import httpx


logger = logging.getLogger(__name__)


class McpClientError(RuntimeError):
    """MCP 初始化、工具发现或工具调用失败。"""


class StreamableHttpMcpClient:
    """调用项目内无状态 Java MCP JSON-RPC 端点。"""

    _PROTOCOL_VERSION = "2025-03-26"

    def __init__(self, url: str, headers: dict[str, str] | None = None, timeout_seconds: float = 30.0) -> None:
        self._url = url
        self._headers = headers or {}
        self._timeout_seconds = timeout_seconds

    def _log_context(self) -> tuple[str, str]:
        return self._headers.get("x-user", "-"), self._headers.get("x-traceId", "-")

    async def _post(self, client: httpx.AsyncClient, payload: dict[str, Any], *, session_id: str | None = None) -> httpx.Response:
        headers = {"Accept": "application/json", "Content-Type": "application/json", **self._headers}
        if session_id:
            headers["Mcp-Session-Id"] = session_id
            headers["MCP-Protocol-Version"] = self._PROTOCOL_VERSION
        response = await client.post(self._url, json=payload, headers=headers)
        await response.aread()
        if response.is_error:
            body = response.text.strip()
            if len(body) > 2000:
                body = body[:2000] + "..."
            raise McpClientError(f"MCP method={payload.get('method')} HTTP失败：status={response.status_code} body={body or '<empty>'}")
        return response

    async def _request(self, method: str, params: dict[str, Any] | None = None) -> dict[str, Any]:
        async with httpx.AsyncClient(timeout=self._timeout_seconds, trust_env=False) as client:
            initialized = await self._post(client, {
                "jsonrpc": "2.0", "id": 1, "method": "initialize",
                "params": {"protocolVersion": self._PROTOCOL_VERSION, "capabilities": {},
                           "clientInfo": {"name": "gas-forecast-agent", "version": "0.1.0"}},
            })
            session_id = initialized.headers.get("Mcp-Session-Id")
            await self._post(client, {"jsonrpc": "2.0", "method": "notifications/initialized"}, session_id=session_id)
            response = await self._post(
                client, {"jsonrpc": "2.0", "id": 2, "method": method, "params": params or {}}, session_id=session_id
            )
            payload = response.json()
            if payload.get("error"):
                raise McpClientError(f"MCP method={method} JSON-RPC失败：{payload['error']}")
            result = payload.get("result")
            if not isinstance(result, dict):
                raise McpClientError(f"MCP method={method} 未返回合法result")
            return result

    async def list_tools(self) -> list[dict[str, Any]]:
        """从 Java MCP 获取工具名称、描述和 JSON Schema。"""
        try:
            result = await self._request("tools/list")
            tools = result.get("tools")
            if not isinstance(tools, list):
                raise McpClientError("MCP tools/list未返回tools数组")
            return tools
        except Exception as exc:
            error = exc if isinstance(exc, McpClientError) else McpClientError(f"MCP工具发现失败：{type(exc).__name__}: {exc}")
            user_id, trace_id = self._log_context()
            logger.exception("event=mcp_list_failed user_id=%s trace_id=%s url=%s detail=%s", user_id, trace_id, self._url, error)
            raise error from exc

    async def call_tool(self, name: str, arguments: dict[str, Any] | None = None) -> dict[str, Any]:
        """调用 Java MCP 工具并返回协议 result。"""
        try:
            return await self._request("tools/call", {"name": name, "arguments": arguments or {}})
        except Exception as exc:
            error = exc if isinstance(exc, McpClientError) else McpClientError(f"MCP工具{name}调用失败：{type(exc).__name__}: {exc}")
            user_id, trace_id = self._log_context()
            logger.exception("event=mcp_call_failed user_id=%s trace_id=%s url=%s tool=%s detail=%s",
                             user_id, trace_id, self._url, name, error)
            raise error from exc
