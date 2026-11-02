"""Java Agent 内部接口客户端；不属于 MCP 工具调用。"""

from typing import Any

import httpx


class InternalAgentClient:
    def __init__(self, url: str, headers: dict[str, str] | None = None, timeout_seconds: float = 30.0) -> None:
        self._url = url
        self._agent_base_url = url.rsplit("/entities:resolve", 1)[0]
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

    async def create_run_event(self, run_no: str, payload: dict[str, Any]) -> dict[str, Any]:
        """创建运行事件；Java 返回持久化 id，供同一事件后续状态更新。"""
        return await self._request("POST", f"{self._agent_base_url}/runs/{run_no}/events", payload)

    async def update_run_event(self, run_no: str, event_id: int, payload: dict[str, Any]) -> dict[str, Any]:
        """更新同一条运行事件，不能以追加记录替代状态变化。"""
        return await self._request("PATCH", f"{self._agent_base_url}/runs/{run_no}/events/{event_id}", payload)

    async def _request(self, method: str, url: str, payload: dict[str, Any]) -> dict[str, Any]:
        headers = {"Accept": "application/json", "Content-Type": "application/json", **self._headers}
        async with httpx.AsyncClient(timeout=self._timeout_seconds, trust_env=False) as client:
            response = await client.request(method, url, json=payload, headers=headers)
        if response.is_error:
            raise RuntimeError(f"Agent运行事件内部接口失败：HTTP {response.status_code}")
        body = response.json()
        if not isinstance(body, dict) or not isinstance(body.get("data"), dict):
            raise RuntimeError("Agent运行事件内部接口未返回合法数据")
        return body["data"]
