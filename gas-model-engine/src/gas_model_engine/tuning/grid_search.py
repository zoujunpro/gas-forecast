from __future__ import annotations

from copy import deepcopy
from itertools import product
from typing import Any


def expand_parameter_grid(
    base_params: dict[str, Any], search_space: dict[str, tuple[Any, ...] | list[Any]]
) -> tuple[dict[str, Any], ...]:
    """展开小型确定性参数网格，支持 ``prophet_params.xxx`` 形式的嵌套路径。"""
    if not search_space:
        return (deepcopy(base_params),)
    paths = tuple(search_space)
    choices = []
    for path in paths:
        values = tuple(search_space[path])
        if not values:
            raise ValueError(f"参数搜索空间不能为空: {path}")
        choices.append(values)
    variants = []
    for values in product(*choices):
        params = deepcopy(base_params)
        for path, value in zip(paths, values):
            _set_nested(params, path, value)
        variants.append(params)
    return tuple(variants)


def _set_nested(params: dict[str, Any], path: str, value: Any) -> None:
    parts = path.split(".")
    if not all(parts):
        raise ValueError(f"无效的参数路径: {path}")
    current = params
    for part in parts[:-1]:
        child = current.setdefault(part, {})
        if not isinstance(child, dict):
            raise ValueError(f"参数路径与现有值冲突: {path}")
        current = child
    current[parts[-1]] = value
