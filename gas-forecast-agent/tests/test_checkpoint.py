import pytest

from gas_forecast_agent.checkpoint import SingleTableMySqlSaver, build_checkpoint_saver
from gas_forecast_agent.config import Settings


def test_checkpoint_is_disabled_without_database_connection() -> None:
    assert build_checkpoint_saver(Settings(checkpoint_enabled=False)) is None


def test_checkpoint_requires_password_when_enabled() -> None:
    with pytest.raises(ValueError, match="GAS_CHECKPOINT_PASSWORD"):
        build_checkpoint_saver(Settings(checkpoint_enabled=True, checkpoint_password=None))


def test_checkpoint_rejects_unsafe_table_name() -> None:
    with pytest.raises(ValueError, match="表名"):
        SingleTableMySqlSaver(host="127.0.0.1", port=3306, database="gas_data", username="root",
                              password="secret", table="checkpoint; drop table customer")
