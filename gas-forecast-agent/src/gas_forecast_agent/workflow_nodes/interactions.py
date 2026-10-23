"""澄清与确认交互的公共组装逻辑。"""

from __future__ import annotations

import hashlib
from typing import Any

from gas_forecast_agent.schemas import (
    AgentRuntimeRequest,
    ClarificationInputType,
    ClarificationOption,
    ClarificationReason,
    InteractionType,
    ResolutionIssue,
)
from gas_forecast_agent.skills import RequestSemantics, SkillRunResult


WAITING_RESULT_STATUSES = {
    "NEED_INFORMATION",
    "NEED_SELECTION",
    "NEED_CONFIRMATION",
    "NEED_CLARIFICATION",
}


def _interaction_details(
    request: AgentRuntimeRequest,
    result: SkillRunResult,
    semantics: RequestSemantics,
) -> dict[str, Any] | None:
    """为每个等待状态生成可验证、可恢复的稳定交互标识和恢复位置。"""
    status = result.status.upper()
    if status not in WAITING_RESULT_STATUSES:
        return None
    if not result.resolution_issues:
        fallback_reason = {
            "NEED_INFORMATION": ClarificationReason.PARAMETER_MISSING,
            "NEED_SELECTION": ClarificationReason.SCOPE_AMBIGUOUS,
            "NEED_CONFIRMATION": ClarificationReason.WRITE_CONFIRMATION_REQUIRED,
            "NEED_CLARIFICATION": ClarificationReason.SCOPE_AMBIGUOUS,
        }[status]
        result.resolution_issues.append(ResolutionIssue(
            reason=fallback_reason,
            field="request",
            message=result.message,
            input_type=(
                ClarificationInputType.CONFIRM
                if status == "NEED_CONFIRMATION"
                else ClarificationInputType.TEXT
            ),
            options=(
                [
                    ClarificationOption(value="confirm", label="确认执行"),
                    ClarificationOption(value="reject", label="取消"),
                ]
                if status == "NEED_CONFIRMATION"
                else []
            ),
        ))
    primary_issue = result.resolution_issues[0]
    interaction_type = (
        InteractionType.CONFIRMATION
        if status == "NEED_CONFIRMATION"
        else InteractionType.CLARIFICATION
    )
    resume_from = {
        ClarificationReason.INTENT_AMBIGUOUS: "SemanticExtractor",
        ClarificationReason.ENTITY_MISSING: "RequestResolver",
        ClarificationReason.ENTITY_AMBIGUOUS: "RequestResolver",
        ClarificationReason.ENTITY_NOT_FOUND: "RequestResolver",
        ClarificationReason.REFERENCE_AMBIGUOUS: "RequestResolver",
        ClarificationReason.PARAMETER_MISSING: "PlanValidator",
        ClarificationReason.PARAMETER_CONFLICT: "PlanValidator",
        ClarificationReason.SCOPE_AMBIGUOUS: "RequestResolver",
        ClarificationReason.WRITE_CONFIRMATION_REQUIRED: "ToolPlanner",
    }[primary_issue.reason]
    raw_id = ":".join((
        request.conversation_no,
        request.turn_id,
        interaction_type.value,
        primary_issue.reason.value,
    ))
    primary_request = semantics.primary_request
    return {
        "interactionId": "INT-" + hashlib.sha256(raw_id.encode("utf-8")).hexdigest()[:24],
        "interactionType": interaction_type.value,
        "sourceTurnId": request.turn_id,
        "requestId": primary_request.request_id if primary_request else None,
        "reason": primary_issue.reason.value,
        "resumeFrom": resume_from,
    }

