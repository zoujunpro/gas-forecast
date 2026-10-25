"""Skill 文档加载。"""

from .models import SkillDefinition, SkillIntent
from .registry import SkillRegistry
from .router import SkillRouter, SkillSelection
from .semantics import (
    BusinessScenario,
    DialogueAct,
    RequestEntities,
    SemanticExtractionError,
    RequestSemanticExtractor,
    RequestSemantics,
    SemanticRequest,
    TargetSelection,
    TrainingPolicy,
)

__all__ = [
    "ActionExecutor", "BusinessScenario", "DialogueAct", "RequestEntities", "SemanticExtractionError",
    "RequestSemanticExtractor",
    "RequestSemantics", "SemanticRequest", "SkillDefinition", "SkillIntent", "SkillRegistry", "SkillRouter",
    "SkillRunResult", "SkillSelection", "TargetSelection", "TrainingPolicy",
]


def __getattr__(name: str):
    # 请求/记忆模型引用语义类型时无需加载执行器，避免 schemas ↔ agent 循环依赖。
    if name in {"ActionExecutor", "SkillRunResult"}:
        from .agent import ActionExecutor, SkillRunResult
        return {"ActionExecutor": ActionExecutor, "SkillRunResult": SkillRunResult}[name]
    raise AttributeError(name)
