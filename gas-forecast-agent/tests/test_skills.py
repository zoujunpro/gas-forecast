from pathlib import Path

import pytest

from gas_forecast_agent.skills import SkillRegistry


def test_loads_skill_documents_and_tool_allowlists() -> None:
    registry = SkillRegistry()

    assert {item.name for item in registry.list_skills()} == {
        "business-data-query",
        "customer-gas-forecast",
        "model-training-management",
    }
    forecast = registry.get("customer-gas-forecast")
    assert "forecast_start" in forecast.allowed_tools
    assert "返回任务或批次编号后结束本轮" in forecast.instructions
    assert set(forecast.references) == {"decision-flow.md", "parameters.md", "status-handling.md"}
    assert "禁止模型编造" in forecast.model_instructions()


def test_rejects_skill_without_allowed_tools(tmp_path: Path) -> None:
    folder = tmp_path / "invalid-skill"
    folder.mkdir()
    (folder / "SKILL.md").write_text(
        "---\nname: invalid-skill\ndescription: 无效 Skill\n---\n说明",
        encoding="utf-8",
    )

    with pytest.raises(ValueError):
        SkillRegistry(tmp_path)
