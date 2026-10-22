from pathlib import Path

import pytest

from gas_forecast_agent.skills import SkillIntent, SkillRegistry


def test_loads_skill_documents_and_tool_allowlists() -> None:
    registry = SkillRegistry()

    assert {item.name for item in registry.list_skills()} == {
        "business-data-query",
        "customer-gas-forecast",
        "forecast-analysis",
        "general-chat",
        "knowledge-qa",
        "model-training-management",
        "unknown-request",
    }

    assert registry.get_by_intent(SkillIntent.TRAIN).name == "model-training-management"
    assert registry.get_by_intent(SkillIntent.FORECAST).name == "customer-gas-forecast"
    assert registry.get_by_intent(SkillIntent.QUERY).name == "business-data-query"
    assert registry.get_by_intent(SkillIntent.ANALYZE).name == "forecast-analysis"
    assert registry.get_by_intent(SkillIntent.KNOWLEDGE_QA).name == "knowledge-qa"
    assert registry.get_by_intent(SkillIntent.CHAT).name == "general-chat"
    assert registry.get_by_intent(SkillIntent.UNKNOWN).name == "unknown-request"

    forecast = registry.get("customer-gas-forecast")
    query = registry.get("business-data-query")
    knowledge = registry.get("knowledge-qa")
    assert "forecast_start" in forecast.allowed_tools
    assert "forecast_result_query" not in forecast.allowed_tools
    assert forecast.read_only is False
    assert query.read_only is True
    assert "forecast_result_query" in query.allowed_tools
    assert knowledge.allowed_tools == []
    assert "只调用一次" in forecast.instructions


def test_rejects_skill_without_allowed_tools(tmp_path: Path) -> None:
    folder = tmp_path / "invalid-skill"
    folder.mkdir()
    (folder / "SKILL.md").write_text(
        "---\nname: invalid-skill\ndescription: 无效 Skill\n---\n说明",
        encoding="utf-8",
    )

    with pytest.raises(ValueError):
        SkillRegistry(tmp_path)


def test_registry_requires_all_intents(tmp_path: Path) -> None:
    folder = tmp_path / "knowledge-qa"
    folder.mkdir()
    (folder / "SKILL.md").write_text(
        """---
name: knowledge-qa
intent: KNOWLEDGE_QA
description: 知识问答
allowed-tools: []
read-only: true
---
回答概念问题。
""",
        encoding="utf-8",
    )

    with pytest.raises(ValueError, match="缺少意图"):
        SkillRegistry(tmp_path)
