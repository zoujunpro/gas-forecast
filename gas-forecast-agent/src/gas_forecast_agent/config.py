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
    mcp_url: str = "http://127.0.0.1:18083/gas-forecast/mcp"
    mcp_authorization: str | None = Field(default=None, repr=False)
    mcp_timeout_seconds: float = 30.0
    intent_mode: str = "hybrid"
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
    checkpoint_enabled: bool = False
    checkpoint_host: str = "127.0.0.1"
    checkpoint_port: int = 3306
    checkpoint_database: str = "gas_data"
    checkpoint_username: str = "gas_agent"
    checkpoint_password: str | None = Field(default=None, repr=False)
    checkpoint_table: str = "agent_graph_checkpoint_tb"

    def mcp_headers(self, authorization: str | None = None) -> dict[str, str]:
        """构造 MCP 请求头；优先透传当前用户凭证，本地开发才使用固定凭证。"""
        credential = authorization or self.mcp_authorization
        if not credential:
            return {}
        return {"Authorization": credential}


@lru_cache
def get_settings() -> Settings:
    """返回进程级配置单例。"""
    return Settings()
