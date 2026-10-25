"""理解本轮请求：恢复待处理操作、理解语义、校验实体、累积可信参数。"""

from __future__ import annotations

import re
import time
from typing import Any

from langgraph.runtime import Runtime

from gas_forecast_agent.entity_models import (
    EntityLookupResponse, EntityMatchStatus, EntityResolution, EntityResolutionItem,
)
from gas_forecast_agent.operation_models import (
    EntityParameters, OperationParameters, ReferenceMentions, SemanticParameters,
)
from gas_forecast_agent.schemas import (
    AgentRuntimeRequest, ClarificationInputType, ClarificationOption,
    ClarificationReason, ConversationMemory, InteractionType, IssueDetails,
    ParameterCandidate, PendingOperation, ResolutionIssue, UserInputType,
)
from gas_forecast_agent.skills import (
    DialogueAct, RequestSemanticExtractor, RequestSemantics, SemanticRequest,
    SkillDefinition, SkillIntent, SkillSelection, TargetSelection,
)
from gas_forecast_agent.skills.semantics import RequestEntities
from gas_forecast_agent.workflow_nodes.common import elapsed_ms
from gas_forecast_agent.workflow_nodes.state import (
    AgentGraphContext, AgentGraphState, RequestUnderstanding,
)


async def understand_request(
    state: AgentGraphState,
    runtime: Runtime[AgentGraphContext],
) -> dict[str, object]:
    """只理解和校验请求；本节点不会创建任务或调用预测接口。"""
    started_at = time.monotonic()
    request = state.request
    # 1. 普通新消息从空操作开始；交互回复必须匹配服务端保存的轮次和编号。
    pending = _pending_interaction(request, request.memory)
    # 2. 确认/拒绝直接恢复原语义；澄清回复只补入本轮有效字段。
    semantics = await _understand_turn(request, request.memory, pending, runtime)
    # 3. 将名称/候选编号解析成主数据编码，未确认的名称不能成为执行参数。
    resolution = await _resolve_turn_entities(
        request, pending, semantics, runtime.context.internal_client,
    )
    # 4. 选择原有 Skill，并合并已经确认的参数；缺失项由原执行器继续校验。
    skill, selection, intent_issues = _select_skill(semantics, runtime)
    parameters = _collect_clarification_parameters(pending, semantics, resolution.parameters)
    return {
        "understanding": RequestUnderstanding(
            semantics=semantics,
            skill=skill,
            selection=selection,
            parameters=parameters,
            entity_resolution=resolution,
            issues=[*resolution.issues, *intent_issues],
            continuing_operation=pending is not None,
        ),
        "timing": state.timing.model_copy(update={
            "semantics_ms": elapsed_ms(started_at), "routing_ms": 0,
        }),
    }


async def _understand_turn(
    request: AgentRuntimeRequest,
    memory: ConversationMemory,
    pending: PendingOperation | None,
    runtime: Runtime[AgentGraphContext],
) -> RequestSemantics:
    if request.input_type == UserInputType.INTERACTION_REPLY:
        explicit_act = _explicit_interaction_act(request.message)
        if explicit_act is not None:
            return _pending_semantics(pending).model_copy(update={"dialogue_act": explicit_act})
        # 模糊回复不由前端或Java猜测。把完整待确认操作交给模型，由模型判断是
        # 确认、取消、修改条件，还是仍然无法理解。
        extracted = await RequestSemanticExtractor(runtime.context.settings).extract(
            message=request.message,
            pending_operation=(pending.model_dump(mode="json", by_alias=True) if pending else None),
            input_type=request.input_type.value,
            available_skills=[skill.summary() for skill in runtime.context.skills.list_skills()],
            conversation_context=_conversation_context(request, memory),
        )
        return _merge_clarification_reply(_pending_semantics(pending), extracted)
    extracted = await RequestSemanticExtractor(runtime.context.settings).extract(
        message=request.message,
        pending_operation=(pending.model_dump(mode="json", by_alias=True) if pending else None),
        input_type=request.input_type.value,
        available_skills=[skill.summary() for skill in runtime.context.skills.list_skills()],
        conversation_context=_conversation_context(request, memory),
    )
    return _merge_clarification_reply(_pending_semantics(pending), extracted) if pending else extracted


async def _resolve_turn_entities(
    request: AgentRuntimeRequest,
    pending: PendingOperation | None,
    semantics: RequestSemantics,
    internal_client: Any,
) -> EntityResolution:
    if request.input_type == UserInputType.INTERACTION_REPLY and semantics.dialogue_act in {
        DialogueAct.CONFIRM_PENDING, DialogueAct.REJECT_PENDING,
    }:
        return EntityResolution()
    selected = _numbered_candidate_resolution(request.message, pending)
    if selected is not None:
        # 一次编号回答只消费当前排在最前面的实体问题；其余问题继续保存在
        # pendingOperation 中，下一轮仍会被询问，不能因为本轮选了一个客户而丢掉。
        return selected
    # 当前只补天数时，不重复查询之前已经确认的客户名称。
    if pending and not _pending_requires_entity_resolution(pending):
        return EntityResolution()
    resolution = await _validate_entities(semantics, internal_client)
    if not pending or not _pending_requires_entity_resolution(pending) or _allows_explicit_batch_scope(semantics):
        return resolution
    # 实体服务一次可能只确认其中一项（例如客户已唯一、地区仍有多个候选）。
    # 把本轮已确认的实体合并进参数，同时只保留还未确认的旧问题。
    return resolution.model_copy(update={
        "issues": _merge_pending_entity_issues(pending, resolution),
    })


def _numbered_candidate_resolution(message: str, pending: PendingOperation | None) -> EntityResolution | None:
    """编号只查找服务端生成的候选答案，不把编号直接当成客户编码。"""
    if pending is None:
        return None
    text = (message or "").strip()
    # 不能扫描所有数字：“预测30天”里的 30 是周期，不是第 30 个客户。
    # 支持单独输入“2”、自然表达“选 2 / 第2个”，以及“2，预测30天”。
    numbers: set[str] = set()
    if re.fullmatch(r"\d{1,2}", text):
        numbers.add(text)
    numbers.update(re.findall(r"(?:选择|选|第)\s*(\d{1,2})(?:\s*(?:个|号|项))?", text))
    leading = re.match(r"^(\d{1,2})(?=\s*[,，、;；。.])", text)
    if leading:
        numbers.add(leading.group(1))
    tokens = {f"candidate-{int(number)}" for number in numbers}
    for index, issue in enumerate(_restore_pending_entity_issues(pending)):
        for candidate in issue.details.candidates:
            if candidate.value in tokens:
                parameters = EntityParameters.model_validate(candidate.parameters.model_dump(
                    include=set(EntityParameters.model_fields), exclude_none=True, exclude_defaults=True,
                ))
                remaining = _restore_pending_entity_issues(pending)
                del remaining[index]
                return EntityResolution(parameters=parameters, issues=remaining)
    return None


def _pending_requires_entity_resolution(pending: PendingOperation | None) -> bool:
    return bool(_restore_pending_entity_issues(pending))


def _has_resolved_pending_entities(parameters: EntityParameters, pending: PendingOperation | None) -> bool:
    for issue in _restore_pending_entity_issues(pending):
        field = issue.field.split(":", 1)[0].lower()
        for entity in ("customer", "region", "industry"):
            if field.startswith(entity) and not parameters.has_codes(entity):
                return False
    return True


def _restore_pending_entity_issues(pending: PendingOperation | None) -> list[ResolutionIssue]:
    return [
        issue for issue in (pending.issues if pending else [])
        if issue.field.split(":", 1)[0].lower().startswith(("customer", "region", "industry", "entities"))
    ]


def _merge_pending_entity_issues(
    pending: PendingOperation,
    resolution: EntityResolution,
) -> list[ResolutionIssue]:
    """保留旧问题中尚未被本轮标准编码确认的部分，并去掉重复问题。"""
    retained = [
        issue for issue in _restore_pending_entity_issues(pending)
        if not _issue_satisfied_by_parameters(issue, resolution.parameters)
    ]
    return _unique_issues([*resolution.issues, *retained])


def _issue_satisfied_by_parameters(issue: ResolutionIssue, parameters: EntityParameters) -> bool:
    """仅当编码能命中该问题的某个候选时，才认为这个具体问题已回答。"""
    entity = issue.details.entity_type or issue.field.split(":", 1)[0].replace("Names", "").upper()
    entity = entity.lower()
    code_values = {
        "customer": set(parameters.customer_codes + ([parameters.customer_code] if parameters.customer_code else [])),
        "region": set(parameters.region_codes + ([parameters.region_code] if parameters.region_code else [])),
        "industry": set(parameters.industry_codes + ([parameters.industry_code] if parameters.industry_code else [])),
    }
    expected = code_values.get(entity, set())
    if not expected:
        return False
    return any(
        expected.intersection(
            candidate.parameters.customer_codes + candidate.parameters.region_codes + candidate.parameters.industry_codes
            + ([candidate.parameters.customer_code] if candidate.parameters.customer_code else [])
            + ([candidate.parameters.region_code] if candidate.parameters.region_code else [])
            + ([candidate.parameters.industry_code] if candidate.parameters.industry_code else [])
        )
        for candidate in issue.details.candidates
    )


def _unique_issues(issues: list[ResolutionIssue]) -> list[ResolutionIssue]:
    unique: list[ResolutionIssue] = []
    fingerprints: set[tuple[str, tuple[str, ...]]] = set()
    for issue in issues:
        fingerprint = (issue.field, tuple(candidate.value + ":" + ",".join(
            candidate.parameters.customer_codes + candidate.parameters.region_codes + candidate.parameters.industry_codes
        ) for candidate in issue.details.candidates))
        if fingerprint not in fingerprints:
            fingerprints.add(fingerprint)
            unique.append(issue)
    return unique


def _collect_clarification_parameters(
    pending: PendingOperation | None,
    semantics: RequestSemantics,
    resolved_entities: EntityParameters,
) -> OperationParameters:
    """先恢复当前未完成操作，再覆盖本轮有效字段；空值不清除已有答案。"""
    values = pending.parameters.to_payload() if pending else {}
    entity_keys = {field.alias or name for name, field in EntityParameters.model_fields.items()}
    semantic_values = {
        key: value for key, value in semantics.parameter_overrides().items()
        if key not in entity_keys and value not in (None, "", [])
    }
    entity_values = {key: value for key, value in resolved_entities.to_payload().items() if value not in (None, "", [])}
    values.update(semantic_values)
    values.update(entity_values)
    locked = set(pending.parameters.locked_parameters if pending else [])
    locked.update(semantic_values)
    locked.update(entity_values)
    if locked:
        values["_lockedParameters"] = sorted(locked)
    return OperationParameters.model_validate(values)


def _allows_explicit_batch_scope(semantics: RequestSemantics) -> bool:
    request = semantics.primary_request
    return bool(
        request and request.target_selection == TargetSelection.ALL_MATCHED
        and (request.entities.regions or request.entities.industries)
    )


def _select_skill(
    semantics: RequestSemantics,
    runtime: Runtime[AgentGraphContext],
) -> tuple[SkillDefinition, SkillSelection, list[ResolutionIssue]]:
    if semantics.intent is not None:
        skill = runtime.context.skills.get_by_intent(semantics.intent)
        return skill, SkillSelection(skill_name=skill.name, confidence=0.8, reason="由意图识别结果直接确定。"), []
    skill = runtime.context.skills.get_by_intent(SkillIntent.CHAT)
    labels = {
        SkillIntent.CHAT: "普通聊天", SkillIntent.QUERY: "查询业务信息",
        SkillIntent.ANALYZE: "分析预测结果", SkillIntent.FORECAST: "发起用气预测",
        SkillIntent.TRAIN: "发起模型训练",
    }
    options = [ClarificationOption(value=f"intent-{index}", label=label)
               for index, label in enumerate(labels.values(), start=1)]
    issue = ResolutionIssue(
        reason=ClarificationReason.INTENT_AMBIGUOUS, field="intent",
        message="我还不能确定你的目标，请选择或补充说明。",
        input_type=ClarificationInputType.SELECT, options=options,
        details=IssueDetails(candidates=[
            ParameterCandidate(value=f"intent-{index}", intent=intent.value)
            for index, intent in enumerate(labels, start=1)
        ]),
    )
    return skill, SkillSelection(skill_name=skill.name, confidence=0, reason="意图不明确，等待用户澄清。"), [issue]


def _pending_interaction(request: AgentRuntimeRequest, memory: ConversationMemory) -> PendingOperation | None:
    if request.input_type == UserInputType.TEXT:
        return None
    pending = memory.pending_operation
    if pending is None:
        raise ValueError("当前会话没有等待处理的交互")
    interaction = pending.interaction
    if request.interaction_id != interaction.interaction_id:
        raise ValueError("交互已过期，请根据最新问题重新操作")
    if request.reply_to_turn_id != interaction.source_turn_id:
        raise ValueError("交互回复对应的轮次不一致")
    expected = InteractionType.CLARIFICATION if request.input_type == UserInputType.CLARIFICATION_REPLY else InteractionType.CONFIRMATION
    if interaction.interaction_type != expected:
        raise ValueError("交互类型不匹配")
    return pending


def _explicit_interaction_act(message: str) -> DialogueAct | None:
    """只处理无歧义短答；其余交给模型结合待确认操作判断。"""
    normalized = re.sub(r"[\s，。！？!,.；;]", "", message or "").lower()
    if normalized in {"确认", "确认执行", "同意", "继续", "继续执行", "执行", "开始", "开始执行", "好", "好的", "可以", "是", "yes", "y", "ok"}:
        return DialogueAct.CONFIRM_PENDING
    if normalized in {"拒绝", "取消", "不执行", "不要", "停止", "否", "no", "n"}:
        return DialogueAct.REJECT_PENDING
    return None


def _pending_semantics(pending: PendingOperation | None) -> RequestSemantics:
    if pending is None:
        raise ValueError("交互缺少原请求语义，请重新发起请求")
    return pending.semantics


async def _validate_entities(semantics: RequestSemantics, internal_client: Any) -> EntityResolution:
    request = semantics.primary_request
    if request is None:
        return EntityResolution()
    arguments = {key: values for key, values in (
        ("customerNames", request.entities.customers),
        ("regionNames", request.entities.regions),
        ("industryNames", request.entities.industries),
    ) if values}
    if not arguments:
        return EntityResolution()
    # HTTP 字典只在边界解析一次；后续读取明确的响应对象。
    lookup = EntityLookupResponse.model_validate(await internal_client.resolve_entities(arguments))
    # Java 只有所有名称都唯一时才填 proposedParameters；混合结果中，items 里
    # 仍保留每个已经唯一的候选。它们必须先写入当前操作，不能等待其他实体消歧。
    resolved = lookup.proposed_parameters or _parameters_from_unique_items(lookup.items)
    if lookup.status == EntityMatchStatus.UNIQUE:
        if not resolved.model_fields_set:
            raise RuntimeError("实体校验未返回标准实体参数")
        return EntityResolution(parameters=resolved, items=lookup.items)
    return EntityResolution(
        parameters=resolved,
        items=lookup.items,
        issues=_entity_issues(lookup, lookup.message or "实体校验失败"),
    )


def _parameters_from_unique_items(items: list[EntityResolutionItem]) -> EntityParameters:
    """从批量解析的逐项结果恢复已唯一的实体，支持“部分唯一、部分歧义”。"""
    values: dict[str, list[str]] = {
        "customerCodes": [], "customerNames": [],
        "regionCodes": [], "regionNames": [],
        "industryCodes": [], "industryNames": [],
    }
    for item in items:
        if item.status != EntityMatchStatus.UNIQUE or not item.candidates:
            continue
        candidate = item.candidates[0]
        parameters = candidate.parameters(item.entity_type).to_payload()
        for key, value in parameters.items():
            if isinstance(value, list):
                values[key].extend(value)
    return EntityParameters.model_validate({
        key: list(dict.fromkeys(value)) for key, value in values.items() if value
    })


def _entity_issues(lookup: EntityLookupResponse, fallback_message: str) -> list[ResolutionIssue]:
    issues: list[ResolutionIssue] = []
    counters: dict[str, int] = {}
    for item in lookup.items:
        if item.status == EntityMatchStatus.UNIQUE:
            continue
        name_key = {"CUSTOMER": "customerNames", "REGION": "regionNames", "INDUSTRY": "industryNames"}[item.entity_type]
        counters[name_key] = counters.get(name_key, 0) + 1
        candidates = [ParameterCandidate(
            value=f"candidate-{index}",
            parameters=OperationParameters.model_validate(candidate.parameters(item.entity_type).to_payload()),
        ) for index, candidate in enumerate(item.candidates, start=1)]
        options = [ClarificationOption(value=f"candidate-{index}", label=candidate.display_name)
                   for index, candidate in enumerate(item.candidates, start=1)]
        issues.append(ResolutionIssue(
            reason=(ClarificationReason.ENTITY_AMBIGUOUS if item.status == EntityMatchStatus.MULTIPLE else ClarificationReason.ENTITY_NOT_FOUND),
            field=f"{name_key}:{counters[name_key]}", message=item.message or fallback_message,
            input_type=ClarificationInputType.SELECT if options else ClarificationInputType.TEXT,
            options=options, details=IssueDetails(candidates=candidates, entity_type=item.entity_type.value),
        ))
    return issues or [ResolutionIssue(reason=ClarificationReason.ENTITY_NOT_FOUND, field="entities", message=fallback_message)]


def _merge_clarification_reply(original: RequestSemantics, reply: RequestSemantics) -> RequestSemantics:
    base = original.primary_request or SemanticRequest()
    candidate = reply.primary_request or base
    # 分别合并客户、地区、行业，不能用一组新实体替换整个实体对象。
    entities = RequestEntities(
        customers=candidate.entities.customers or base.entities.customers,
        regions=candidate.entities.regions or base.entities.regions,
        industries=candidate.entities.industries or base.entities.industries,
    )
    parameters = {**base.parameters.to_payload(), **{
        key: value for key, value in candidate.parameters.to_payload().items() if value not in (None, "", [])
    }}
    references = {**base.references.to_payload(), **candidate.references.to_payload()}
    request = base.model_copy(update={
        "intent": base.intent or candidate.intent,
        "entities": entities,
        "references": ReferenceMentions.model_validate(references),
        "parameters": SemanticParameters.model_validate(parameters),
        "target_selection": candidate.target_selection if candidate.target_selection != TargetSelection.UNSPECIFIED else base.target_selection,
    })
    return RequestSemantics(dialogue_act=DialogueAct.MODIFY_PENDING, requests=[request])


def _conversation_context(request: AgentRuntimeRequest, memory: ConversationMemory) -> dict[str, object]:
    """仅在模型输入边界序列化；历史用于理解指代，不自动注入执行参数。"""
    context: dict[str, object] = {"recentMessages": [message.model_dump(by_alias=True) for message in request.history[-8:]]}
    if request.conversation_summary:
        context["summary"] = request.conversation_summary
    if request.metadata.current_business_context is not None:
        context["pageContext"] = request.metadata.current_business_context.model_dump(by_alias=True, exclude_none=True)
    if request.metadata.history_references:
        context["historyReferences"] = [item.model_dump(by_alias=True, exclude_none=True) for item in request.metadata.history_references]
    if memory.current_business_context is not None:
        context["memory"] = {"currentBusinessContext": memory.current_business_context.model_dump(by_alias=True, exclude_none=True)}
    return context
