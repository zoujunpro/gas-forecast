"""只访问一张MySQL表的LangGraph Checkpointer。"""

from __future__ import annotations

import asyncio
import hashlib
from collections.abc import AsyncIterator, Iterator, Sequence
from typing import Any

import pymysql
from langchain_core.runnables import RunnableConfig
from langgraph.checkpoint.base import BaseCheckpointSaver, Checkpoint, CheckpointMetadata, CheckpointTuple


class SingleTableMySqlSaver(BaseCheckpointSaver):
    """将checkpoint、metadata和pending writes保存在同一张表。

    数据库账号必须只拥有目标表的SELECT、INSERT、UPDATE、DELETE权限。本类没有
    访问其他业务表的SQL入口。
    """

    def __init__(self, *, host: str, port: int, database: str, username: str, password: str,
                 table: str = "agent_graph_checkpoint_tb", connect_timeout: int = 5) -> None:
        super().__init__()
        if not table.replace("_", "").isalnum():
            raise ValueError("checkpoint表名只能包含字母、数字和下划线")
        self._connection_args = {
            "host": host, "port": port, "user": username, "password": password,
            "database": database, "charset": "utf8mb4", "autocommit": False,
            "connect_timeout": connect_timeout, "cursorclass": pymysql.cursors.DictCursor,
        }
        self._table = table

    def _connect(self):
        return pymysql.connect(**self._connection_args)

    @staticmethod
    def _keys(config: RunnableConfig) -> tuple[str, str, str | None]:
        values = config.get("configurable", {})
        thread_id = values.get("thread_id")
        if not thread_id:
            raise ValueError("LangGraph checkpoint必须提供thread_id")
        checkpoint_id = values.get("checkpoint_id")
        return str(thread_id), str(values.get("checkpoint_ns") or ""), str(checkpoint_id) if checkpoint_id else None

    def get_tuple(self, config: RunnableConfig) -> CheckpointTuple | None:
        thread_id, namespace, checkpoint_id = self._keys(config)
        where, params = "thread_id=%s and checkpoint_ns=%s", [thread_id, namespace]
        if checkpoint_id:
            where += " and checkpoint_id=%s"
            params.append(checkpoint_id)
        where += " and checkpoint_blob is not null"
        sql = f"select * from `{self._table}` where {where} order by checkpoint_id desc limit 1"
        with self._connect() as connection, connection.cursor() as cursor:
            cursor.execute(sql, params)
            row = cursor.fetchone()
        return self._tuple(row) if row else None

    def list(self, config: RunnableConfig | None, *, filter: dict[str, Any] | None = None,
             before: RunnableConfig | None = None, limit: int | None = None) -> Iterator[CheckpointTuple]:
        clauses: list[str] = []
        params: list[Any] = []
        if config:
            thread_id, namespace, checkpoint_id = self._keys(config)
            clauses.extend(["thread_id=%s", "checkpoint_ns=%s"])
            params.extend([thread_id, namespace])
            if checkpoint_id:
                clauses.append("checkpoint_id=%s")
                params.append(checkpoint_id)
        if before:
            _, _, before_id = self._keys(before)
            if before_id:
                clauses.append("checkpoint_id<%s")
                params.append(before_id)
        sql = f"select * from `{self._table}`"
        clauses.append("checkpoint_blob is not null")
        if clauses:
            sql += " where " + " and ".join(clauses)
        sql += " order by checkpoint_id desc"
        if limit is not None:
            sql += " limit %s"
            params.append(limit)
        with self._connect() as connection, connection.cursor() as cursor:
            cursor.execute(sql, params)
            rows = cursor.fetchall()
        for row in rows:
            item = self._tuple(row)
            if not filter or all(item.metadata.get(key) == value for key, value in filter.items()):
                yield item

    def put(self, config: RunnableConfig, checkpoint: Checkpoint, metadata: CheckpointMetadata,
            new_versions: dict[str, Any]) -> RunnableConfig:
        thread_id, namespace, parent_id = self._keys(config)
        checkpoint_id = checkpoint["id"]
        checkpoint_type, checkpoint_blob = self.serde.dumps_typed(checkpoint)
        metadata_type, metadata_blob = self.serde.dumps_typed(metadata)
        sql = f"""insert into `{self._table}`
            (thread_id, checkpoint_ns, checkpoint_id, parent_checkpoint_id, checkpoint_type, checkpoint_blob, metadata_type, metadata_blob)
            values (%s,%s,%s,%s,%s,%s,%s,%s)
            on duplicate key update parent_checkpoint_id=values(parent_checkpoint_id), checkpoint_type=values(checkpoint_type),
            checkpoint_blob=values(checkpoint_blob), metadata_type=values(metadata_type), metadata_blob=values(metadata_blob)"""
        with self._connect() as connection, connection.cursor() as cursor:
            cursor.execute(sql, (thread_id, namespace, checkpoint_id, parent_id, checkpoint_type,
                                 checkpoint_blob, metadata_type, metadata_blob))
            connection.commit()
        return {"configurable": {"thread_id": thread_id, "checkpoint_ns": namespace, "checkpoint_id": checkpoint_id}}

    def put_writes(self, config: RunnableConfig, writes: Sequence[tuple[str, Any]], task_id: str, task_path: str = "") -> None:
        thread_id, namespace, checkpoint_id = self._keys(config)
        if not checkpoint_id:
            raise ValueError("pending writes必须提供checkpoint_id")
        with self._connect() as connection, connection.cursor() as cursor:
            lock_name = "lgcp:" + hashlib.sha256(f"{thread_id}:{namespace}:{checkpoint_id}".encode()).hexdigest()[:56]
            cursor.execute("select get_lock(%s, 10) as acquired", (lock_name,))
            if cursor.fetchone()["acquired"] != 1:
                raise TimeoutError("获取checkpoint pending writes锁超时")
            try:
                cursor.execute(f"select pending_writes_type,pending_writes_blob from `{self._table}` where thread_id=%s and checkpoint_ns=%s and checkpoint_id=%s",
                               (thread_id, namespace, checkpoint_id))
                row = cursor.fetchone()
                existing = self.serde.loads_typed((row["pending_writes_type"], bytes(row["pending_writes_blob"]))) if row and row["pending_writes_blob"] else []
                indexed = {(item[0], item[1]): item for item in existing}
                for channel, value in writes:
                    indexed[(task_id, channel)] = (task_id, channel, value, task_path)
                value_type, value_blob = self.serde.dumps_typed(list(indexed.values()))
                cursor.execute(f"""insert into `{self._table}`
                    (thread_id,checkpoint_ns,checkpoint_id,pending_writes_type,pending_writes_blob)
                    values (%s,%s,%s,%s,%s)
                    on duplicate key update pending_writes_type=values(pending_writes_type),pending_writes_blob=values(pending_writes_blob)""",
                               (thread_id, namespace, checkpoint_id, value_type, value_blob))
                connection.commit()
            finally:
                cursor.execute("select release_lock(%s)", (lock_name,))

    def delete_thread(self, thread_id: str) -> None:
        with self._connect() as connection, connection.cursor() as cursor:
            cursor.execute(f"delete from `{self._table}` where thread_id=%s", (thread_id,))
            connection.commit()

    def _tuple(self, row: dict[str, Any]) -> CheckpointTuple:
        configurable = {"thread_id": row["thread_id"], "checkpoint_ns": row["checkpoint_ns"], "checkpoint_id": row["checkpoint_id"]}
        config = {"configurable": configurable}
        parent = ({"configurable": {**configurable, "checkpoint_id": row["parent_checkpoint_id"]}}
                  if row["parent_checkpoint_id"] else None)
        checkpoint = self.serde.loads_typed((row["checkpoint_type"], bytes(row["checkpoint_blob"])))
        metadata = self.serde.loads_typed((row["metadata_type"], bytes(row["metadata_blob"])))
        raw_writes = self.serde.loads_typed((row["pending_writes_type"], bytes(row["pending_writes_blob"]))) if row["pending_writes_blob"] else []
        pending = [(item[0], item[1], item[2]) for item in raw_writes]
        return CheckpointTuple(config, checkpoint, metadata, parent, pending)

    async def aget_tuple(self, config: RunnableConfig) -> CheckpointTuple | None:
        return await asyncio.to_thread(self.get_tuple, config)

    async def alist(self, config: RunnableConfig | None, *, filter: dict[str, Any] | None = None,
                    before: RunnableConfig | None = None, limit: int | None = None) -> AsyncIterator[CheckpointTuple]:
        items = await asyncio.to_thread(lambda: list(self.list(config, filter=filter, before=before, limit=limit)))
        for item in items:
            yield item

    async def aput(self, config: RunnableConfig, checkpoint: Checkpoint, metadata: CheckpointMetadata,
                   new_versions: dict[str, Any]) -> RunnableConfig:
        return await asyncio.to_thread(self.put, config, checkpoint, metadata, new_versions)

    async def aput_writes(self, config: RunnableConfig, writes: Sequence[tuple[str, Any]], task_id: str, task_path: str = "") -> None:
        await asyncio.to_thread(self.put_writes, config, writes, task_id, task_path)

    async def adelete_thread(self, thread_id: str) -> None:
        await asyncio.to_thread(self.delete_thread, thread_id)
