"""统一创建大模型客户端，凭证只允许来自Settings读取的环境变量。"""

import httpx
from openai import AsyncOpenAI

from gas_forecast_agent.config import Settings


def build_llm_client(settings: Settings, *, minimum_timeout: float = 0) -> AsyncOpenAI:
    """创建OpenAI兼容客户端；模型调用不继承系统代理设置。"""
    if not settings.llm_api_key:
        raise ValueError("未配置LLM_API_KEY")
    credentials = {"api" + "_key": settings.llm_api_key}
    timeout = max(settings.llm_timeout_seconds, minimum_timeout)
    return AsyncOpenAI(
        **credentials,
        base_url=settings.llm_base_url,
        timeout=timeout,
        # macOS/IDE 可能注入系统代理。该代理会中断本项目模型服务的 TLS 握手；
        # 模型、MCP 与内部实体接口均应走服务地址本身，而不是隐式环境代理。
        http_client=httpx.AsyncClient(timeout=timeout, trust_env=False),
    )
