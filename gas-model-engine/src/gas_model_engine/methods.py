from __future__ import annotations

from dataclasses import dataclass
from typing import Generic, Protocol, TypeVar


@dataclass(frozen=True)
class MethodMetadata:
    """特征、回测、组合和选择方法共用的稳定标识。"""

    code: str
    name: str
    version: str = "1.0.0"
    description: str = ""


class RegisteredMethod(Protocol):
    metadata: MethodMetadata


T = TypeVar("T", bound=RegisteredMethod)


class MethodRegistry(Generic[T]):
    """按稳定方法编码显式注册实现，拒绝重复编码。"""

    def __init__(self, methods=()) -> None:
        self._methods: dict[str, T] = {}
        for method in methods:
            self.register(method)

    def register(self, method: T) -> None:
        code = method.metadata.code
        if not code:
            raise ValueError("方法编码不能为空")
        if code in self._methods:
            raise ValueError(f"方法已注册: {code}")
        self._methods[code] = method

    def get(self, code: str) -> T:
        try:
            return self._methods[code]
        except KeyError as exc:
            supported = ", ".join(sorted(self._methods))
            raise KeyError(f"方法不存在: {code}; supported: {supported}") from exc

    def list_metadata(self) -> list[MethodMetadata]:
        return [method.metadata for method in self._methods.values()]
