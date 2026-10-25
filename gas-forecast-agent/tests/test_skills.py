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
        "model-training-management",
    }

    assert registry.get_by_intent(SkillIntent.TRAIN).name == "model-training-management"
    assert registry.get_by_intent(SkillIntent.FORECAST).name == "customer-gas-forecast"
    assert registry.get_by_intent(SkillIntent.QUERY).name == "business-data-query"
    assert registry.get_by_intent(SkillIntent.ANALYZE).name == "forecast-analysis"
    assert registry.get_by_intent(SkillIntent.CHAT).name == "general-chat"

    forecast = registry.get("customer-gas-forecast")
    training = registry.get("model-training-management")
    query = registry.get("business-data-query")
    analysis = registry.get("forecast-analysis")
    chat = registry.get("general-chat")
    assert forecast.allowed_tools == ["forecast_start"]
    assert training.allowed_tools == ["forecast_start"]
    assert "training_start" not in {tool for skill in registry.list_skills() for tool in skill.allowed_tools}
    assert forecast.read_only is False
    assert training.read_only is False
    assert query.read_only is True
    assert set(query.allowed_tools) == {
        "region_list", "industry_list", "customer_list", "forecast_result_query",
        "task_status_get", "task_steps_get",
    }
    assert analysis.allowed_tools == ["forecast_result_by_no", "forecast_result_query"]
    assert chat.allowed_tools == []
    assert {tool for skill in registry.list_skills() for tool in skill.allowed_tools} == {
        "region_list", "industry_list", "customer_list", "forecast_start",
        "forecast_result_query", "forecast_result_by_no", "task_status_get", "task_steps_get",
    }


def test_rejects_skill_without_allowed_tools(tmp_path: Path) -> None:
    folder = tmp_path / "invalid-skill"
    folder.mkdir()
    (folder / "SKILL.md").write_text(
        "---\nname: invalid-skill\ndescription: 无效 Skill\n---\n说明",
        encoding="utf-8",
    )

    with pytest.raises(ValueError):
        SkillRegistry(tmp_path)


def test_registry_requires_all_current_intents(tmp_path: Path) -> None:
    folder = tmp_path / "general-chat"
    folder.mkdir()
    (folder / "SKILL.md").write_text(
        """---
name: general-chat
intent: CHAT
description: 普通聊天
allowed-tools: []
read-only: true
---
回答普通聊天。
""",
        encoding="utf-8",
    )

    with pytest.raises(ValueError, match="缺少意图"):
        SkillRegistry(tmp_path)
