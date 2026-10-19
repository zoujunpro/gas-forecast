"""应用配置，只从环境变量读取部署差异和凭证。"""

from functools import lru_cache
from pathlib import Path

from pydantic import AliasChoices, Field
from pydantic_settings import BaseSettings, SettingsConfigDict


_AGENT_PROJECT_DIR = Path(__file__).resolve().parents[2]
_WORKSPACE_DIR = _AGENT_PROJECT_DIR.parent


class Settings(BaseSettings):
    """智能体服务运行参数。"""

    # 先读取仓库公共模型配置，再读取Agent私有配置；不再依赖进程从哪个目录启动。
    model_config = SettingsConfigDict(
        env_prefix="GAS_",
        env_file=(_WORKSPACE_DIR / ".env", _AGENT_PROJECT_DIR / ".env"),
        extra="ignore",
    )

    agent_host: str = "0.0.0.0"
    agent_port: int = 8091
    # local profile 将 Java 服务绑定到 127.0.0.1；固定使用 IPv4，避免 ::1 与实际监听地址不一致。
    # 本地开发 Agent(8091)连接 IDEA Java(18083)；Jenkins 部署通过
    # /Users/zoujun/data/gas/gas-agent/config/agent.env 显式覆盖为 18081。
    mcp_url: str = "http://127.0.0.1:18083/gas-forecast/mcp"
    mcp_authorization: str | None = Field(default=None, repr=False)
    mcp_timeout_seconds: float = 30.0
    skill_routing_mode: str = "hybrid"
    llm_api_key: str | None = Field(
        default=None,
        repr=False,
        validation_alias=AliasChoices("GAS_LLM_API_KEY", "LLM_QWEN_API_KEY", "LLM_API_KEY"),
    )
    llm_base_url: str | None = Field(
        default=None,
        validation_alias=AliasChoices("GAS_LLM_BASE_URL", "LLM_BASE_URL", "LLM_API_BASE_URL"),
    )
    llm_model: str | None = Field(
        default=None,
        validation_alias=AliasChoices("GAS_LLM_MODEL", "LLM_MODEL", "LLM_API_MODEL"),
    )
    llm_timeout_seconds: float = 30.0
    # 单次对话请求的安全预算。它们限制运行资源，不代表固定业务工作流。
    agent_max_steps: int = Field(default=8, ge=1, le=20)
    agent_max_duration_seconds: float = Field(default=45.0, ge=1, le=300)
    agent_max_same_tool_calls: int = Field(default=2, ge=1, le=5)
    agent_max_write_calls: int = Field(default=1, ge=1, le=2)
    # 只读幂等工具发生明确系统异常时的额外重试次数；写工具不使用此配置。
    agent_max_read_retries: int = Field(default=1, ge=0, le=3)

    def mcp_headers(self, authorization: str | None = None, access_token: str | None = None) -> dict[str, str]:
        """构造 MCP 请求头；优先透传当前用户凭证，本地开发才使用固定凭证。"""
        if access_token:
            return {"X-Access-Token": access_token}
        credential = authorization or self.mcp_authorization
        return {"Authorization": credential} if credential else {}


@lru_cache
def get_settings() -> Settings:
    """返回进程级配置单例。"""
    return Settings()
