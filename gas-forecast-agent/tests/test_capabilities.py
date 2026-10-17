from pathlib import Path

import pytest

from gas_forecast_agent.capabilities import CapabilityRegistry, CapabilityStatus


def test_loads_business_capability_catalog() -> None:
    registry = CapabilityRegistry()
    summary = registry.summary()

    assert summary["total"] == 26
    assert summary["available"] == 20
    assert set(summary["scenarios"]) == {"COMMON", "SHORT_TERM", "MONTHLY_SALES", "WINTER_SUPPLY"}


def test_resolves_available_short_term_forecast() -> None:
    capability = CapabilityRegistry().resolve(
        domain="FORECAST",
        action="START",
        resource="CUSTOMER_FORECAST",
        scenario="SHORT_TERM",
    )

    assert capability.code == "SHORT_TERM_FORECAST_START"
    assert capability.status == CapabilityStatus.AVAILABLE
    assert "forecast_start" in capability.tools


def test_planned_capability_cannot_be_resolved_for_execution() -> None:
    registry = CapabilityRegistry()

    with pytest.raises(KeyError):
        registry.resolve("FORECAST", "RESULT", "SUPPLY_FORECAST", "WINTER_SUPPLY")

    assert registry.get("WINTER_SUPPLY_RESULT").status == CapabilityStatus.PLANNED


def test_rejects_available_capability_with_missing_tool(tmp_path: Path) -> None:
    definition = tmp_path / "invalid.yml"
    definition.write_text(
        """
schemaVersion: 1
scenario: TEST
scenarioName: 测试
description: 测试配置
capabilities:
  - code: INVALID
    name: 无效能力
    description: 不允许已上线能力缺少工具
    status: AVAILABLE
    read_only: true
    workflow: query
    intent: {domain: TEST, action: LIST, resource: TEST}
    missing_tools: [missing_tool]
""".strip(),
        encoding="utf-8",
    )

    with pytest.raises(ValueError):
        CapabilityRegistry(tmp_path)
