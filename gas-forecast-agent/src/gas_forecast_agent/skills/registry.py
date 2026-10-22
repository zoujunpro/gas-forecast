"""从项目 skills 目录加载 SKILL.md。"""

from __future__ import annotations

from pathlib import Path
import sys

import yaml

from .models import SkillDefinition, SkillIntent


_PROJECT_DIR = Path(__file__).resolve().parents[3]


class SkillRegistry:
    """只负责发现和读取 Skill，不保存工具编排状态。"""

    def __init__(self, skill_dir: Path | None = None) -> None:
        self._root = skill_dir or self._default_skill_dir()
        self._skills = self._load(self._root)
        self._skills_by_intent = self._index_by_intent(self._skills)

    def list_skills(self) -> list[SkillDefinition]:
        return list(self._skills.values())

    def get(self, name: str) -> SkillDefinition:
        try:
            return self._skills[name]
        except KeyError as exc:
            raise KeyError(f"未知 Skill：{name}") from exc

    def get_by_intent(self, intent: SkillIntent) -> SkillDefinition:
        """根据统一语义意图取得唯一 Skill。"""
        try:
            return self._skills_by_intent[intent]
        except KeyError as exc:
            raise KeyError(f"意图 {intent.value} 没有对应的 Skill") from exc

    def summary(self) -> dict[str, object]:
        return {
            "total": len(self._skills),
            "skills": [
                {
                    **skill.summary(),
                    "allowed_tools": skill.allowed_tools,
                }
                for skill in self._skills.values()
            ],
        }

    @staticmethod
    def _default_skill_dir() -> Path:
        source_tree = _PROJECT_DIR / "skills"
        if source_tree.is_dir():
            return source_tree
        installed = Path(sys.prefix) / "share" / "gas-forecast-agent" / "skills"
        if installed.is_dir():
            return installed
        raise ValueError("未找到 gas-forecast-agent Skill 目录")

    @staticmethod
    def _load(root: Path) -> dict[str, SkillDefinition]:
        skills: dict[str, SkillDefinition] = {}
        for path in sorted(root.glob("*/SKILL.md")):
            metadata, instructions = SkillRegistry._parse(path)
            references = {
                reference.name: reference.read_text(encoding="utf-8").strip()
                for reference in sorted((path.parent / "references").glob("*.md"))
            }
            skill = SkillDefinition.model_validate({
                **metadata,
                "instructions": instructions,
                "references": references,
            })
            if skill.name != path.parent.name:
                raise ValueError(f"Skill 名称必须与目录一致：{path}")
            if skill.name in skills:
                raise ValueError(f"Skill 名称重复：{skill.name}")
            skills[skill.name] = skill
        if not skills:
            raise ValueError(f"Skill 目录为空：{root}")
        return skills

    @staticmethod
    def _index_by_intent(skills: dict[str, SkillDefinition]) -> dict[SkillIntent, SkillDefinition]:
        result: dict[SkillIntent, SkillDefinition] = {}
        for skill in skills.values():
            if skill.intent in result:
                raise ValueError(
                    f"一个意图只能对应一个 Skill：{skill.intent.value} 同时被 "
                    f"{result[skill.intent].name} 和 {skill.name} 声明"
                )
            result[skill.intent] = skill

        missing = set(SkillIntent) - set(result)
        if missing:
            names = "、".join(sorted(intent.value for intent in missing))
            raise ValueError(f"Skill 定义不完整，缺少意图：{names}")
        return result

    @staticmethod
    def _parse(path: Path) -> tuple[dict[str, object], str]:
        text = path.read_text(encoding="utf-8")
        if not text.startswith("---\n"):
            raise ValueError(f"Skill 缺少 YAML frontmatter：{path}")
        end = text.find("\n---\n", 4)
        if end < 0:
            raise ValueError(f"Skill frontmatter 未闭合：{path}")
        metadata = yaml.safe_load(text[4:end])
        if not isinstance(metadata, dict):
            raise ValueError(f"Skill frontmatter 格式错误：{path}")
        return metadata, text[end + 5 :].strip()
