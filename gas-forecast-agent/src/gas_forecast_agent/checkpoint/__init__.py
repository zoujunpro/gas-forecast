"""LangGraph持久化检查点。"""

from .mysql_single_table import SingleTableMySqlSaver


def build_checkpoint_saver(settings):
    """按配置创建单表Checkpointer；关闭时不建立数据库连接。"""
    if not settings.checkpoint_enabled:
        return None
    if not settings.checkpoint_password:
        raise ValueError("启用Checkpoint时必须配置GAS_CHECKPOINT_PASSWORD")
    return SingleTableMySqlSaver(host=settings.checkpoint_host, port=settings.checkpoint_port,
                                 database=settings.checkpoint_database, username=settings.checkpoint_username,
                                 password=settings.checkpoint_password, table=settings.checkpoint_table)


__all__ = ["SingleTableMySqlSaver", "build_checkpoint_saver"]
