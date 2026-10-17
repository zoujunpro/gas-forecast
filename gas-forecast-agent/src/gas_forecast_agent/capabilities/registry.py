"""加载并查询业务能力配置。"""

from __future__ import annotations

from importlib.resources import files
from pathlib import Path
from typing import Iterable

import yaml

from .models import CapabilityDefinition, CapabilityFile, CapabilityStatus


class CapabilityRegistry:
    """业务能力注册表。

    默认读取包内definitions目录。运行时只路由AVAILABLE能力；PLANNED和
    NEEDS_CONFIRMATION仍会展示在管理接口中，便于识别工具和业务缺口。
    """

    def __init__(self, definition_dir: Path | None = None) -> None:
        root = definition_dir or Path(str(files("gas_forecast_agent.capabilities").joinpath("definitions")))
        self._files, self._capabilities = self._load(root)

    def list(self, include_unavailable: bool = True) -> list[CapabilityDefinition]:
        capabilities = list(self._capabilities.values())
        if not include_unavailable:
            capabilities = [item for item in capabilities if item.status == CapabilityStatus.AVAILABLE]
        return capabilities

    def get(self, code: str, require_available: bool = False) -> CapabilityDefinition:
        capability = self._capabilities.get(code)
        if capability is None:
            raise KeyError(f"未知业务能力：{code}")
        if require_available and capability.status != CapabilityStatus.AVAILABLE:
            raise ValueError(f"业务能力{code}当前状态为{capability.status}")
        return capability

    def resolve(self, domain: str, action: str, resource: str, scenario: str | None = None) -> CapabilityDefinition:
        matches = [
            item
            for item in self.list(include_unavailable=False)
            if item.intent.domain == domain
            and item.intent.action == action
            and item.intent.resource == resource
            and item.intent.scenario == scenario
        ]
        if not matches:
            raise KeyError(f"没有可用能力匹配：{domain}/{action}/{resource}/{scenario or 'COMMON'}")
        if len(matches) > 1:
            raise ValueError(f"意图匹配到多个业务能力：{[item.code for item in matches]}")
        return matches[0]

    def summary(self) -> dict[str, object]:
        grouped: dict[str, list[dict[str, object]]] = {}
        for configured in self._files:
            grouped[configured.scenario] = [
                item.model_dump(mode="json", by_alias=True) for item in configured.capabilities
            ]
        return {
            "total": len(self._capabilities),
            "available": sum(item.status == CapabilityStatus.AVAILABLE for item in self._capabilities.values()),
            "planned": sum(item.status == CapabilityStatus.PLANNED for item in self._capabilities.values()),
            "needsConfirmation": sum(
                item.status == CapabilityStatus.NEEDS_CONFIRMATION for item in self._capabilities.values()
            ),
            "scenarios": grouped,
        }

    def _load(self, root: Path) -> tuple[list[CapabilityFile], dict[str, CapabilityDefinition]]:
        paths = sorted(root.glob("*.yml"))
        if not paths:
            raise ValueError(f"业务能力配置目录为空：{root}")
        configured: list[CapabilityFile] = []
        capabilities: dict[str, CapabilityDefinition] = {}
        for path in paths:
            raw = yaml.safe_load(path.read_text(encoding="utf-8"))
            item = CapabilityFile.model_validate(raw)
            configured.append(item)
            self._register(capabilities, item.capabilities, path)
        return configured, capabilities

    def _register(
        self,
        target: dict[str, CapabilityDefinition],
        capabilities: Iterable[CapabilityDefinition],
        source: Path,
    ) -> None:
        for capability in capabilities:
            if capability.code in target:
                raise ValueError(f"业务能力编码重复：{capability.code}，来源={source}")
            target[capability.code] = capability
