"""应用日志配置，作用类似 Java Logback。"""

import logging
import logging.config
from contextvars import ContextVar
from pathlib import Path

from gas_model_platform.core.config import settings

request_id_context: ContextVar[str] = ContextVar("request_id", default="-")
client_context: ContextVar[str] = ContextVar("client", default="-")


class RequestContextFilter(logging.Filter):
    """向每条日志补充类似 Java MDC 的请求上下文字段。"""

    def filter(self, record: logging.LogRecord) -> bool:
        record.request_id = request_id_context.get()
        record.client = client_context.get()
        return True


def configure_logging(
    log_dir: Path | None = None,
    level: str | None = None,
    retention_days: int | None = None,
    console_enabled: bool | None = None,
    file_enabled: bool | None = None,
) -> Path | None:
    """按环境配置控制台日志和按天滚动的文件日志。"""
    target_dir = log_dir or settings.log_dir
    log_file = target_dir / f"{settings.app_name}.log"
    log_level = (level or settings.log_level).upper()
    backup_count = retention_days if retention_days is not None else settings.log_retention_days
    use_console = settings.log_console_enabled if console_enabled is None else console_enabled
    use_file = settings.log_file_enabled if file_enabled is None else file_enabled
    if not use_console and not use_file:
        raise ValueError("控制台日志和文件日志不能同时关闭")

    config = {
        "version": 1,
        "disable_existing_loggers": False,
        "formatters": {
            "standard": {
                "format": (
                    "%(asctime)s.%(msecs)03d %(levelname)-8s "
                    "[pid:%(process)d] [thread:%(threadName)s] "
                    "[request:%(request_id)s] [client:%(client)s] "
                    "[%(name)s:%(funcName)s:%(lineno)d] %(message)s"
                ),
                "datefmt": "%Y-%m-%d %H:%M:%S",
            },
        },
        "handlers": {},
        "filters": {
            "request_context": {
                "()": "gas_model_platform.core.logging.RequestContextFilter",
            }
        },
        "root": {
            "level": log_level,
            "handlers": [],
        },
        "loggers": {
            "uvicorn": {"level": log_level, "propagate": True, "handlers": []},
            "uvicorn.error": {"level": log_level, "propagate": True, "handlers": []},
            # Prophet 每次拟合都会调用 CmdStan；只保留警告和错误，避免数百次底层日志刷屏。
            "cmdstanpy": {"level": "WARNING", "propagate": True, "handlers": []},
            # 已由 RequestResponseLoggingMiddleware 输出更完整的访问日志，避免重复两行。
            "uvicorn.access": {"level": "WARNING", "propagate": False, "handlers": []},
        },
    }
    if use_console:
        config["handlers"]["console"] = {
            "class": "logging.StreamHandler",
            "level": log_level,
            "formatter": "standard",
            "filters": ["request_context"],
            "stream": "ext://sys.stdout",
        }
        config["root"]["handlers"].append("console")
    if use_file:
        target_dir.mkdir(parents=True, exist_ok=True)
        config["handlers"]["file"] = {
            "class": "logging.handlers.TimedRotatingFileHandler",
            "level": log_level,
            "formatter": "standard",
            "filters": ["request_context"],
            "filename": str(log_file),
            "when": "midnight",
            "interval": 1,
            "backupCount": backup_count,
            "encoding": "utf-8",
            "delay": True,
        }
        config["root"]["handlers"].append("file")
    logging.config.dictConfig(config)
    return log_file if use_file else None
