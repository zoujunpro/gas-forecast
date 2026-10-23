"""理解用户请求，并在路由前校验识别出的业务实体。"""

from __future__ import annotations

import time
from typing import Any

from langgraph.runtime import Runtime

from gas_forecast_agent.schemas import (
    AgentRuntimeRequest,
    ClarificationInputType,
    ClarificationOption,
    ClarificationReason,
    InteractionType,
    ResolutionIssue,
    UserInputType,
)
from gas_forecast_agent.skills import (
    DialogueAct,
    RequestSemanticExtractor,
    RequestSemantics,
    SemanticRequest,
    SkillIntent,
    SkillSelection,
)
from gas_forecast_agent.workflow_nodes.common import elapsed_ms
from gas_forecast_agent.workflow_nodes.state import AgentGraphContext, AgentGraphState


async def understand_request(
    state: AgentGraphState,
    runtime: Runtime[AgentGraphContext],
) -> dict[str, Any]:
    """识别 Skill、解析实体/指代/时间，并校验识别出的实体真实性。

    本节点不选择业务工具、不补充业务参数、不创建或查询业务数据。实体名称
    只会调用固定的主数据校验工具；未找到或存在多个候选时，直接返回澄清问题。
    """
    started_at = time.monotonic()
    request = state["request"]
    memory = state["memory"]
    pending = _pending_interaction(request, memory)

    if request.input_type in {
        UserInputType.CONFIRMATION_REPLY,
        UserInputType.REJECTION_REPLY,
    }:
        semantics = _pending_semantics(pending).model_copy(update={
            "dialogue_act": (
                DialogueAct.CONFIRM_PENDING
                if request.input_type == UserInputType.CONFIRMATION_REPLY
                else DialogueAct.REJECT_PENDING
            ),
        })
        resolved_entities: dict[str, Any] = {}
        entity_issues: list[ResolutionIssue] = []
    else:
        extracted = await RequestSemanticExtractor(runtime.context.settings).extract(
            message=request.message,
            pending_action=None,
            pending_interaction=(
                pending if request.input_type == UserInputType.CLARIFICATION_REPLY else None
            ),
            input_type=request.input_type.value,
            answers=request.answers,
            available_skills=[skill.summary() for skill in runtime.context.skills.list_skills()],
            conversation_context=_conversation_context(request, memory),
        )
        selected_values = _selected_entity_parameters(pending, request.answers)
        semantics = (
            _merge_clarification_reply(_pending_semantics(pending), extracted, selected_values)
            if request.input_type == UserInputType.CLARIFICATION_REPLY
            else extracted
        )
        resolved_entities, entity_issues = await _validate_entities(
            semantics,
            runtime.context.internal_client,
            selected_values,
        )

    skill, selection, intent_issues = _select_skill(semantics, runtime)

    return {
        "semantics": semantics,
        "skill": skill,
        "selection": selection,
        "clarification_parameters": {},
        "resolved_entity_parameters": resolved_entities,
        "preflight_issues": [*entity_issues, *intent_issues],
        "semantics_ms": elapsed_ms(started_at),
        "routing_ms": 0,
    }


def _select_skill(
    semantics: RequestSemantics,
    runtime: Runtime[AgentGraphContext],
) -> tuple[Any, SkillSelection, list[ResolutionIssue]]:
    """语义识别完成后立即确定 Skill；意图不明确则只生成澄清。"""
    intent = semantics.intent
    if intent is not None:
        skill = runtime.context.skills.get_by_intent(intent)
        return skill, SkillSelection(
            skill_name=skill.name,
            confidence=0.8,
            reason="由意图识别结果直接确定。",
        ), []
    skill = runtime.context.skills.get_by_intent(SkillIntent.CHAT)
    options = []
    candidate_map: dict[str, dict[str, str]] = {}
    labels = {
        SkillIntent.CHAT: "普通聊天",
        SkillIntent.QUERY: "查询业务信息",
        SkillIntent.ANALYZE: "分析预测结果",
        SkillIntent.FORECAST: "发起用气预测",
        SkillIntent.TRAIN: "发起模型训练",
    }
    for index, candidate in enumerate(labels, start=1):
        token = f"intent-{index}"
        candidate_map[token] = {"_intent": candidate.value}
        options.append(ClarificationOption(value=token, label=labels[candidate]))
    issue = ResolutionIssue(
        reason=ClarificationReason.INTENT_AMBIGUOUS,
        field="intent",
        message="我还不能确定你的目标，请选择或补充说明。",
        input_type=ClarificationInputType.SELECT,
        options=options,
        details={"candidateMap": candidate_map},
    )
    return skill, SkillSelection(
        skill_name=skill.name,
        confidence=0,
        reason="意图不明确，等待用户澄清。",
    ), [issue]


def _pending_interaction(
    request: AgentRuntimeRequest,
    memory: dict[str, Any],
) -> dict[str, Any] | None:
    """仅校验当前交互；不兼容旧的 pendingAction 快照。"""
    if request.input_type == UserInputType.TEXT:
        return None
    pending = memory.get("pendingInteraction")
    if not isinstance(pending, dict):
        raise ValueError("当前会话没有等待处理的交互")
    if request.interaction_id != pending.get("interactionId"):
        raise ValueError("交互已过期，请根据最新问题重新操作")
    if request.reply_to_turn_id != pending.get("sourceTurnId"):
        raise ValueError("交互回复对应的轮次不一致")
    expected = (
        InteractionType.CLARIFICATION.value
        if request.input_type == UserInputType.CLARIFICATION_REPLY
        else InteractionType.CONFIRMATION.value
    )
    if pending.get("interactionType") != expected:
        raise ValueError("交互类型不匹配")
    return pending


def _pending_semantics(pending: dict[str, Any] | None) -> RequestSemantics:
    """当前交互必须携带原请求语义；缺失即视为无效，不再猜测旧格式。"""
    raw = pending.get("semantics") if isinstance(pending, dict) else None
    if not isinstance(raw, dict):
        raise ValueError("交互缺少原请求语义，请重新发起请求")
    return RequestSemantics.model_validate(raw)


async def _validate_entities(
    semantics: RequestSemantics,
    internal_client: Any,
    selected_values: dict[str, Any],
) -> tuple[dict[str, Any], list[ResolutionIssue]]:
    """校验语义层识别出的实体，或使用前轮澄清中已选择的可信候选。"""
    if selected_values:
        return selected_values, []
    semantic_request = semantics.primary_request
    if semantic_request is None:
        return {}, []
    arguments = {
        key: values
        for key, values in (
            ("customerNames", semantic_request.entities.customers),
            ("regionNames", semantic_request.entities.regions),
            ("industryNames", semantic_request.entities.industries),
        )
        if values
    }
    if not arguments:
        return {}, []
    data = await internal_client.resolve_entities(arguments)
    if str(data.get("status") or "").upper() == "UNIQUE":
        values = data.get("proposedParameters")
        if not isinstance(values, dict):
            raise RuntimeError("实体校验未返回标准实体参数")
        return {key: value for key, value in values.items() if value not in (None, "", [])}, []
    return {}, _entity_issues(data, str(data.get("message") or "实体校验失败"))


def _entity_issues(data: dict[str, Any], fallback_message: str) -> list[ResolutionIssue]:
    """把主数据校验返回的候选项转换为前端澄清问题。"""
    issues: list[ResolutionIssue] = []
    counters: dict[str, int] = {}
    for item in data.get("items") or []:
        if not isinstance(item, dict) or str(item.get("status") or "").upper() == "UNIQUE":
            continue
        entity_type = str(item.get("entityType") or "").upper()
        name_key, code_key = {
            "CUSTOMER": ("customerNames", "customerCodes"),
            "REGION": ("regionNames", "regionCodes"),
            "INDUSTRY": ("industryNames", "industryCodes"),
        }.get(entity_type, ("entities", "entityCodes"))
        counters[name_key] = counters.get(name_key, 0) + 1
        field = f"{name_key}:{counters[name_key]}"
        candidate_map: dict[str, dict[str, list[str]]] = {}
        options: list[ClarificationOption] = []
        for index, candidate in enumerate(item.get("candidates") or [], start=1):
            if not isinstance(candidate, dict) or not candidate.get("code") or not candidate.get("name"):
                continue
            token = f"candidate-{index}"
            candidate_map[token] = {
                code_key: [str(candidate["code"])],
                name_key: [str(candidate["name"])],
            }
            detail = str(candidate.get("description") or "").strip()
            options.append(ClarificationOption(
                value=token,
                label=f"{candidate['name']}（{detail}）" if detail else str(candidate["name"]),
            ))
        multiple = str(item.get("status") or "").upper() == "MULTIPLE"
        issues.append(ResolutionIssue(
            reason=(ClarificationReason.ENTITY_AMBIGUOUS if multiple else ClarificationReason.ENTITY_NOT_FOUND),
            field=field,
            message=str(item.get("message") or fallback_message),
            input_type=(ClarificationInputType.SELECT if options else ClarificationInputType.TEXT),
            options=options,
            details={"candidateMap": candidate_map, "entityType": entity_type},
        ))
    return issues or [ResolutionIssue(
        reason=ClarificationReason.ENTITY_NOT_FOUND,
        field="entities",
        message=fallback_message,
    )]


def _selected_entity_parameters(
    pending: dict[str, Any] | None,
    answers: dict[str, Any],
) -> dict[str, Any]:
    """仅恢复用户选择的服务端候选 token；自由文本仍会重新做实体校验。"""
    if not isinstance(pending, dict):
        return {}
    resolved: dict[str, Any] = {}
    for issue in pending.get("issues") or []:
        if not isinstance(issue, dict):
            continue
        field = str(issue.get("field") or "")
        details = issue.get("details") if isinstance(issue.get("details"), dict) else {}
        candidates = details.get("candidateMap") if isinstance(details.get("candidateMap"), dict) else {}
        selected = candidates.get(str(answers.get(field)))
        if isinstance(selected, dict):
            for key, value in selected.items():
                if isinstance(value, list):
                    current = resolved.setdefault(key, [])
                    for item in value:
                        if item not in current:
                            current.append(item)
                else:
                    resolved[key] = value
    return resolved


def _merge_clarification_reply(
    original: RequestSemantics,
    reply: RequestSemantics,
    selected_values: dict[str, Any],
) -> RequestSemantics:
    """保留原请求的 Skill 意图，并用澄清回复补充实体、指代和时间范围。"""
    base = original.primary_request or SemanticRequest()
    candidate = reply.primary_request or base
    selected_intent = selected_values.get("_intent")
    try:
        intent = SkillIntent(str(selected_intent)) if selected_intent else base.intent
    except ValueError:
        intent = base.intent
    request = base.model_copy(update={
        "intent": intent,
        "entities": candidate.entities if candidate.entities.model_dump(exclude_defaults=True) else base.entities,
        "references": {**base.references, **candidate.references},
        "parameters": {**base.parameters, **candidate.parameters},
    })
    return RequestSemantics(dialogue_act=DialogueAct.MODIFY_PENDING, requests=[request])


def _conversation_context(request: AgentRuntimeRequest, memory: dict[str, Any]) -> dict[str, Any]:
    """提供解析指代和时间范围所需的对话、页面上下文。"""
    context: dict[str, Any] = {"recentMessages": request.history[-8:]}
    if request.conversation_summary:
        context["summary"] = request.conversation_summary
    if isinstance(request.metadata.get("currentBusinessContext"), dict):
        context["pageContext"] = request.metadata["currentBusinessContext"]
    if isinstance(request.metadata.get("historyReferences"), list):
        context["historyReferences"] = request.metadata["historyReferences"]
    if memory.get("currentBusinessContext"):
        context["memory"] = {"currentBusinessContext": memory["currentBusinessContext"]}
    return context
