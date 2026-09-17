import logging

from gas_model_platform.core.logging import configure_logging
from gas_model_platform.core.config import settings
from gas_model_platform.core.request_logging import _render_body


def test_logging_writes_console_and_rotating_file(tmp_path) -> None:
    log_file = configure_logging(
        log_dir=tmp_path,
        level="INFO",
        retention_days=7,
        console_enabled=False,
        file_enabled=True,
    )
    logger = logging.getLogger("gas_model_platform.test")

    logger.info("logging configuration test")
    for handler in logging.getLogger().handlers:
        handler.flush()

    assert log_file.exists()
    content = log_file.read_text(encoding="utf-8")
    assert "logging configuration test" in content
    assert "[thread:MainThread]" in content
    assert "[request:-] [client:-]" in content
    assert "[gas_model_platform.test:test_logging_writes_console_and_rotating_file:" in content


def test_http_body_log_masks_secrets_and_summarizes_long_lists() -> None:
    item_count = settings.log_body_max_items + 4
    body = (
        '{"token":"do-not-log","dataset":['
        + ",".join(f'{{"index":{index}}}' for index in range(item_count))
        + "]}"
    ).encode()

    rendered = _render_body(body, "application/json", enabled=True)

    assert "do-not-log" not in rendered
    assert '"token":"***"' in rendered
    assert f'"count":{item_count}' in rendered
    assert '"truncated_items":4' in rendered
