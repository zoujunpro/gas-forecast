"""应用公共配置加载。

默认读取项目根目录的 config/application.yaml；环境变量拥有最高优先级。
"""

from __future__ import annotations

import os
import re
from collections.abc import Mapping
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import yaml


def _discover_default_config_path() -> Path:
    """从工作目录或源码项目根目录定位公共配置。"""
    candidates = [
        Path.cwd() / "config/application.yaml",
        Path(__file__).resolve().parents[3] / "config/application.yaml",
    ]
    return next((path for path in candidates if path.exists()), candidates[0])


DEFAULT_CONFIG_PATH = _discover_default_config_path()


@dataclass(frozen=True)
class Settings:
    app_name: str = "gas-model-platform"
    app_version: str = "0.1.0"
    server_host: str = "0.0.0.0"
    server_port: int = 8090
    server_reload: bool = False
    artifact_root: Path = Path("artifacts")
    log_dir: Path = Path("logs")
    log_level: str = "INFO"
    log_retention_days: int = 30
    log_console_enabled: bool = True
    log_file_enabled: bool = True
    log_request_body_enabled: bool = True
    log_response_body_enabled: bool = True
    log_body_max_length: int = 20000
    log_body_max_items: int = 20
    profile: str = "default"
    config_path: Path = DEFAULT_CONFIG_PATH
    profile_config_path: Path | None = None


def _section(payload: Mapping[str, Any], name: str) -> Mapping[str, Any]:
    value = payload.get(name, {})
    if not isinstance(value, Mapping):
        raise ValueError(f"配置项 {name} 必须是对象")
    return value


def _load_yaml(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {}
    loaded = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    if not isinstance(loaded, Mapping):
        raise ValueError(f"{path} 顶层必须是对象")
    return dict(loaded)


def _merge(base: dict[str, Any], override: Mapping[str, Any]) -> dict[str, Any]:
    """递归合并配置，profile 中的值覆盖公共配置。"""
    merged = dict(base)
    for key, value in override.items():
        current = merged.get(key)
        if isinstance(current, Mapping) and isinstance(value, Mapping):
            merged[key] = _merge(dict(current), value)
        else:
            merged[key] = value
    return merged


def _as_bool(value: Any) -> bool:
    if isinstance(value, bool):
        return value
    normalized = str(value).strip().lower()
    if normalized in {"1", "true", "yes", "on"}:
        return True
    if normalized in {"0", "false", "no", "off"}:
        return False
    raise ValueError(f"无法识别的布尔配置值: {value}")


def _resolve_path(value: str | Path, config_path: Path) -> Path:
    configured = Path(value)
    if configured.is_absolute():
        return configured
    config_dir = config_path.parent
    base_dir = config_dir.parent if config_dir.name == "config" else config_dir
    return (base_dir / configured).resolve()


def load_settings(
    config_path: str | Path | None = None,
    environ: Mapping[str, str] | None = None,
) -> Settings:
    """加载 YAML 配置，并用 GAS_MODEL_* 环境变量覆盖。"""
    env = environ if environ is not None else os.environ
    path = Path(
        config_path or env.get("GAS_MODEL_CONFIG", DEFAULT_CONFIG_PATH)
    ).resolve()
    payload = _load_yaml(path)
    profiles = _section(payload, "profiles")
    profile = env.get("GAS_MODEL_PROFILE", str(profiles.get("active", "default")))
    if not re.fullmatch(r"[A-Za-z0-9_-]+", profile):
        raise ValueError("GAS_MODEL_PROFILE 只能包含字母、数字、下划线和中划线")

    profile_path = path.with_name(f"{path.stem}-{profile}{path.suffix}")
    if profile != "default":
        payload = _merge(payload, _load_yaml(profile_path))

    app = _section(payload, "app")
    server = _section(payload, "server")
    storage = _section(payload, "storage")
    logging_config = _section(payload, "logging")

    return Settings(
        app_name=env.get("GAS_MODEL_APP_NAME", str(app.get("name", "gas-model-platform"))),
        app_version=env.get("GAS_MODEL_APP_VERSION", str(app.get("version", "0.1.0"))),
        server_host=env.get("GAS_MODEL_SERVER_HOST", str(server.get("host", "0.0.0.0"))),
        server_port=int(
            env.get("GAS_MODEL_SERVER_PORT", str(server.get("port", 8090)))
        ),
        server_reload=_as_bool(
            env.get("GAS_MODEL_SERVER_RELOAD", server.get("reload", False))
        ),
        artifact_root=_resolve_path(
            env.get("GAS_MODEL_ARTIFACT_ROOT", str(storage.get("artifact_root", "artifacts"))),
            path,
        ),
        log_dir=_resolve_path(
            env.get("GAS_MODEL_LOG_DIR", str(logging_config.get("directory", "logs"))),
            path,
        ),
        log_level=env.get(
            "GAS_MODEL_LOG_LEVEL",
            str(logging_config.get("level", "INFO")),
        ).upper(),
        log_retention_days=int(
            env.get(
                "GAS_MODEL_LOG_RETENTION_DAYS",
                str(logging_config.get("retention_days", 30)),
            )
        ),
        log_console_enabled=_as_bool(
            env.get(
                "GAS_MODEL_LOG_CONSOLE_ENABLED",
                logging_config.get("console_enabled", True),
            )
        ),
        log_file_enabled=_as_bool(
            env.get(
                "GAS_MODEL_LOG_FILE_ENABLED",
                logging_config.get("file_enabled", True),
            )
        ),
        log_request_body_enabled=_as_bool(
            env.get(
                "GAS_MODEL_LOG_REQUEST_BODY_ENABLED",
                logging_config.get("request_body_enabled", True),
            )
        ),
        log_response_body_enabled=_as_bool(
            env.get(
                "GAS_MODEL_LOG_RESPONSE_BODY_ENABLED",
                logging_config.get("response_body_enabled", True),
            )
        ),
        log_body_max_length=int(
            env.get(
                "GAS_MODEL_LOG_BODY_MAX_LENGTH",
                str(logging_config.get("body_max_length", 20000)),
            )
        ),
        log_body_max_items=int(
            env.get(
                "GAS_MODEL_LOG_BODY_MAX_ITEMS",
                str(logging_config.get("body_max_items", 20)),
            )
        ),
        profile=profile,
        config_path=path,
        profile_config_path=profile_path if profile_path.exists() else None,
    )


settings = load_settings()
