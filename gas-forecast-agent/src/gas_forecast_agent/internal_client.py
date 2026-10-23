"""Java Agent 内部接口客户端；不属于 MCP 工具调用。"""

from typing import Any

import httpx


class InternalAgentClient:
    def __init__(self, url: str, headers: dict[str, str] | None = None, timeout_seconds: float = 30.0) -> None:
        self._url = url
        self._headers = headers or {}
        self._timeout_seconds = timeout_seconds

    async def resolve_entities(self, arguments: dict[str, Any]) -> dict[str, Any]:
        headers = {"Accept": "application/json", "Content-Type": "application/json", **self._headers}
        async with httpx.AsyncClient(timeout=self._timeout_seconds, trust_env=False) as client:
            response = await client.post(self._url, json=arguments, headers=headers)
        if response.is_error:
            raise RuntimeError(f"实体校验内部接口失败：HTTP {response.status_code}")
        body = response.json()
        if not isinstance(body, dict) or not isinstance(body.get("data"), dict):
            raise RuntimeError("实体校验内部接口未返回合法数据")
        return body["data"]
