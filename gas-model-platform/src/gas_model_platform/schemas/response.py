from __future__ import annotations

from typing import Generic, TypeVar

from pydantic import BaseModel

DataT = TypeVar("DataT")


class ApiResponse(BaseModel, Generic[DataT]):
    """所有 HTTP 接口统一使用的响应结构。"""

    code: int = 0
    message: str = "success"
    data: DataT | None = None

    @classmethod
    def success(cls, data: DataT, message: str = "success") -> "ApiResponse[DataT]":
        return cls(code=0, message=message, data=data)

    @classmethod
    def failure(
        cls,
        code: int,
        message: str,
        data: DataT | None = None,
    ) -> "ApiResponse[DataT]":
        return cls(code=code, message=message, data=data)
