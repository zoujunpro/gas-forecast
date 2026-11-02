from __future__ import annotations

from collections.abc import Iterable

from .base import BaseAlgorithm
from .contracts import AlgorithmMetadata


class AlgorithmRegistry:
    def __init__(self, algorithms: Iterable[BaseAlgorithm] = ()) -> None:
        self._algorithms: dict[str, BaseAlgorithm] = {}
        for algorithm in algorithms:
            self.register(algorithm)

    def register(self, algorithm: BaseAlgorithm) -> None:
        code = algorithm.metadata.code
        if not code:
            raise ValueError("算法编码不能为空")
        if code in self._algorithms:
            raise ValueError(f"算法已注册: {code}")
        self._algorithms[code] = algorithm

    def get(self, code: str) -> BaseAlgorithm:
        try:
            return self._algorithms[code]
        except KeyError as exc:
            supported = ", ".join(sorted(self._algorithms))
            raise KeyError(f"算法不存在: {code}; supported: {supported}") from exc

    def list_metadata(self) -> list[AlgorithmMetadata]:
        return [algorithm.metadata for algorithm in self._algorithms.values()]

    def validate_dependencies(self) -> None:
        graph = {code: algorithm.dependencies() for code, algorithm in self._algorithms.items()}
        for code, dependencies in graph.items():
            missing = [item for item in dependencies if item not in graph]
            if missing:
                raise ValueError(f"算法{code}依赖未注册算法: {', '.join(missing)}")

        visiting: set[str] = set()
        visited: set[str] = set()

        def visit(code: str) -> None:
            if code in visiting:
                raise ValueError(f"算法依赖存在循环: {code}")
            if code in visited:
                return
            visiting.add(code)
            for dependency in graph[code]:
                visit(dependency)
            visiting.remove(code)
            visited.add(code)

        for code in graph:
            visit(code)
