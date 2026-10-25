"""澄清与确认交互的公共组装逻辑。"""

from __future__ import annotations

import hashlib

from gas_forecast_agent.schemas import (
    AgentRuntimeRequest,
    ClarificationInputType,
    ClarificationOption,
    ClarificationReason,
    InteractionType,
    PendingInteraction,
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
) -> PendingInteraction | None:
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
    # 所有澄清均允许用户放弃当前操作。该选项是对话回复值，不依赖页面按钮；
    # 后端会优先将“取消”等撤销表达识别为 REJECT_PENDING。
    if status != "NEED_CONFIRMATION" and not any(
        option.value == "cancel" for option in primary_issue.options
    ):
        primary_issue.options.append(
            ClarificationOption(value="cancel", label="取消本次操作")
        )
    # 页面只有一个普通输入框；每个待处理问题都必须告诉用户该输入什么，并给出
    # 可直接照着回复的示例，不能依赖下拉框、按钮或字段名。
    result.message = _conversation_prompt(result.message, primary_issue)
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
    return PendingInteraction(
        interaction_id="INT-" + hashlib.sha256(raw_id.encode("utf-8")).hexdigest()[:24],
        interaction_type=interaction_type,
        source_turn_id=request.turn_id,
        request_id=primary_request.request_id if primary_request else None,
        reason=primary_issue.reason,
        resume_from=resume_from,
    )


def _conversation_prompt(message: str, issue: ResolutionIssue) -> str:
    """为普通聊天输入补上明确、可执行的回复指引。"""
    if issue.reason == ClarificationReason.WRITE_CONFIRMATION_REQUIRED:
        guidance = "请在输入框回复 **确认** 继续执行；回复 **拒绝** 或 **取消** 放弃本次操作。"
    elif issue.reason == ClarificationReason.ENTITY_AMBIGUOUS and issue.options:
        # 页面不再展示下拉框；候选项必须直接写入对话，用户只需输入其中一个完整名称。
        candidates = issue.options[:10]
        names = "\n".join(
            f"{index}. **{option.label}**" for index, option in enumerate(candidates, start=1)
        )
        more = "\n（候选较多，仅展示前 10 个，请补充更完整的名称以缩小范围。）" \
            if len(issue.options) > len(candidates) else ""
        guidance = f"可回复序号（如 **1**）、候选完整名称，或重新输入“地区 + 行业 + 客户”。\n{names}{more}"
    else:
        examples = {
            "forecastHorizon": "请直接回复预测天数，例如 **30天**。",
            "customerName": "请直接回复客户名称，例如 **东台新奥**。",
            "customerNames": "请直接回复客户名称，例如 **东台新奥**。",
            "regionName": "请直接回复地区名称，例如 **江苏**。",
            "regionNames": "请直接回复地区名称，例如 **江苏**。",
            "industryName": "请直接回复行业名称，例如 **城燃**。",
            "industryNames": "请直接回复行业名称，例如 **城燃**。",
            "historyStartDate": "请直接回复开始日期，例如 **2026-01-01**。",
            "historyEndDate": "请直接回复结束日期，例如 **2026-06-30**。",
            "businessScope": "请直接说明是否使用当前范围，例如 **使用当前范围**，或重新说明客户、地区和行业。",
        }
        guidance = examples.get(
            issue.field.split(":", 1)[0],
            "请直接在输入框补充所需信息，例如客户名称、地区、时间或预测周期。",
        )
        guidance = f"{guidance} 如不继续本次操作，请回复 **取消**。"
    content = str(message or "请补充必要信息。").strip()
    return content if guidance in content else f"{content}\n\n{guidance}"
