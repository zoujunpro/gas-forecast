package com.gas.forecast.business.ai.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gas.forecast.business.ai.dto.AiChatRequest;
import com.gas.forecast.business.ai.dto.AiChatResponse;
import com.gas.forecast.business.ai.dto.PythonAgentChatRequest;
import com.gas.forecast.business.ai.dto.PythonAgentChatResponse;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.security.context.SecurityContextHolder;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.AgentRunTb;
import com.gas.forecast.dao.domain.AiConversationMessageTb;
import com.gas.forecast.dao.domain.AiConversationTb;
import com.gas.forecast.dao.domain.AiMessageRefTb;
import com.gas.forecast.dao.mapper.AgentRunTbMapper;
import com.gas.forecast.dao.mapper.AiConversationMessageTbMapper;
import com.gas.forecast.dao.mapper.AiConversationTbMapper;
import com.gas.forecast.dao.mapper.AiMessageRefTbMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Java 会话入口的数据库事务服务；远程 Python 调用不在数据库事务中执行。 */
@Service
@RequiredArgsConstructor
public class AiChatPersistenceService {
    private static final Set<String> AGENT_CODES = Set.of(
            "general", "short-term", "monthly-sales", "winter-supply");
    private static final Set<String> RUN_STATUSES = Set.of(
            "PENDING", "RUNNING", "WAITING", "SUCCESS", "FAILED", "CANCELLED");
    private static final Set<String> REFERENCE_TYPES = Set.of(
            "EXECUTION_BATCH", "EXECUTION_TASK", "TRAIN_BATCH", "FORECAST_BATCH");
    private static final Set<String> RELATION_TYPES = Set.of(
            "CREATED", "REFERENCED", "DISPLAYED");
    private static final Set<String> INTERACTION_TYPES = Set.of("CLARIFICATION", "CONFIRMATION");
    private static final Set<String> CLARIFICATION_REASONS = Set.of(
            "INTENT_AMBIGUOUS", "ENTITY_MISSING",
            "ENTITY_AMBIGUOUS", "ENTITY_NOT_FOUND", "PARAMETER_MISSING",
            "PARAMETER_CONFLICT", "SCOPE_AMBIGUOUS", "REFERENCE_AMBIGUOUS",
            "WRITE_CONFIRMATION_REQUIRED");
    private static final Set<String> CLARIFICATION_INPUT_TYPES = Set.of(
            "TEXT", "NUMBER", "DATE", "SELECT", "MULTI_SELECT", "CONFIRM");
    /** Python 仅能通过补丁维护这些工作记忆字段，不能任意改写会话数据。 */
    private static final Set<String> MEMORY_PATCH_KEYS = Set.of(
            "currentSkill", "currentParameters", "lastTool", "lastExecution",
            "pendingInteraction", "pendingAction", "currentBusinessContext");

    private final AiConversationTbMapper conversationMapper;
    private final AiConversationMessageTbMapper messageMapper;
    private final AgentRunTbMapper runMapper;
    private final AiMessageRefTbMapper referenceMapper;
    private final ObjectMapper objectMapper;

    /** 创建或加载会话，保存用户消息和 Agent Run，并生成发给 Python 的请求。 */
    @Transactional
    public PythonAgentChatRequest createTurn(AiChatRequest request) {
        Date now = new Date();
        AiConversationTb conversation = requireOrCreateConversation(request, now);
        validateConversationRequest(conversation, request);
        List<PythonAgentChatRequest.RecentMessage> recentMessages =
                loadRecentMessages(conversation.getId(), 20);
        Map<String, Object> conversationMemory = currentConversationMemory(
                conversation, request.businessContext());
        validateInteractionReply(request, conversationMemory);
        PythonAgentChatRequest.ConversationContext conversationContext =
                new PythonAgentChatRequest.ConversationContext(
                        trimToNull(conversation.getSummary()),
                        conversationMemory,
                        recentMessages);

        String turnId = "TURN-" + compactUuid();
        AiConversationMessageTb userMessage = new AiConversationMessageTb();
        userMessage.setConversationId(conversation.getId());
        userMessage.setRole("USER");
        userMessage.setTurnId(turnId);
        userMessage.setContent(request.message().trim());
        userMessage.setMessageType("USER_MESSAGE");
        userMessage.setCreatedAt(now);
        messageMapper.insert(userMessage);

        String runNo = "RUN-" + compactUuid();
        // 幂等键来自前端；用户重试同一次发送时必须复用。未升级的旧调用方才由
        // 服务端兜底生成，避免空值进入后续训练/预测任务创建。
        String requestId = TextUtils.hasText(request.clientRequestId()) ? request.clientRequestId().trim() : "REQ-" + compactUuid();
        AiChatRequest.BusinessContext context = request.businessContext() == null
                ? new AiChatRequest.BusinessContext(null, null, null, null, null, null)
                : request.businessContext();
        PythonAgentChatRequest agentRequest = new PythonAgentChatRequest(
                conversation.getConversationNo(),
                turnId,
                runNo,
                request.message().trim(),
                request.inputType().name(),
                trimToNull(request.interactionId()),
                trimToNull(request.replyToTurnId()),
                request.answers(),
                conversation.getAgentCode(),
                context,
                conversationContext);

        AgentRunTb run = new AgentRunTb();
        run.setRunNo(runNo);
        run.setRequestId(requestId);
        run.setConversationId(conversation.getId());
        run.setMessageId(userMessage.getId());
        run.setAgentCode(conversation.getAgentCode());
        run.setStatus("RUNNING");
        run.setRequestJson(agentRequestSnapshot(agentRequest));
        run.setStartedAt(now);
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        runMapper.insert(run);

        conversation.setLastMessageAt(now);
        conversation.setUpdatedAt(now);
        conversationMapper.updateById(conversation);
        return agentRequest;
    }

    /** 保存 Python 执行结果、助手消息及消息业务引用。 */
    @Transactional
    public AiChatResponse complete(PythonAgentChatResponse result) {
        AgentRunTb run = requireRun(result.runNo());
        AiConversationTb conversation = requireOwnedConversation(run.getConversationId());
        verifyResultIdentity(run, conversation, result);
        applyMemoryPatch(conversation, result.memoryPatch());

        String status = normalizeRunStatus(result.status());
        String answer = TextUtils.hasText(result.message())
                ? result.message().trim() : "AI 未返回有效内容";
        List<AiChatResponse.Reference> references = normalizeReferences(result.references());
        AiChatResponse.Clarification clarification = normalizeClarification(
                status, result.turnId(), result.clarification());
        AiChatResponse response = new AiChatResponse(
                conversation.getConversationNo(),
                result.turnId(),
                run.getRunNo(),
                conversation.getAgentCode(),
                status,
                answer,
                clarification,
                result.presentation(),
                result.executionResult(),
                references);

        Date now = new Date();
        AiConversationMessageTb assistant = new AiConversationMessageTb();
        assistant.setConversationId(conversation.getId());
        assistant.setRole("ASSISTANT");
        assistant.setTurnId(result.turnId());
        assistant.setContent(answer);
        assistant.setMessageType(TextUtils.hasText(result.skillCode())
                ? result.skillCode() : "AGENT_REPLY");
        assistant.setStructuredJson(writeJson(response));
        assistant.setCreatedAt(now);
        messageMapper.insert(assistant);
        saveReferences(assistant.getId(), references, now);

        run.setStatus(status);
        run.setIntentCode(trimToNull(result.intentCode()));
        run.setIntentJson(writeNullableJson(result.intentData()));
        run.setSkillCode(trimToNull(result.skillCode()));
        run.setResultJson(runResultJson(result, response));
        run.setErrorCode("FAILED".equals(status) ? trimToNull(result.errorCode()) : null);
        run.setErrorMessage("FAILED".equals(status) ? trimToNull(result.errorMessage()) : null);
        run.setFinishedAt(now);
        run.setUpdatedAt(now);
        runMapper.updateById(run);

        conversation.setLastMessageAt(now);
        conversation.setUpdatedAt(now);
        conversationMapper.updateById(conversation);
        return response;
    }

    /** Python 不可用或内部接口调用失败时，可靠地结束 Run 并保存可见失败消息。 */
    @Transactional
    public AiChatResponse fail(PythonAgentChatRequest request, RuntimeException exception) {
        String message = "智能体服务暂时不可用，本轮未确认创建新的业务任务，请稍后重试。";
        PythonAgentChatResponse failure = new PythonAgentChatResponse(
                request.conversationNo(), request.turnId(), request.runNo(),
                request.agentCode(), "FAILED", message, null, List.of(), "CHAT",
                objectMapper.createObjectNode(), null, objectMapper.createObjectNode(),
                objectMapper.createObjectNode(), null, null,
                "AGENT_SERVICE_ERROR", safeError(exception));
        return complete(failure);
    }

    private AiConversationTb requireOrCreateConversation(AiChatRequest request, Date now) {
        if (TextUtils.hasText(request.conversationNo())) {
            AiConversationTb existing = conversationMapper.selectOne(
                    Wrappers.<AiConversationTb>lambdaQuery()
                            .eq(AiConversationTb::getConversationNo, request.conversationNo().trim())
                            .eq(AiConversationTb::getCreatedBy, currentUserId())
                            .last("limit 1"));
            if (existing == null) {
                throw new BusinessException("会话不存在或无权访问");
            }
            if (!"ACTIVE".equalsIgnoreCase(existing.getStatus())) {
                throw new BusinessException("当前会话已归档，不能继续发送消息");
            }
            return existing;
        }

        String agentCode = TextUtils.hasText(request.agentCode())
                ? request.agentCode().trim() : "general";
        if (!AGENT_CODES.contains(agentCode)) {
            throw new BusinessException("不支持的智能体编码：" + agentCode);
        }
        AiConversationTb conversation = new AiConversationTb();
        conversation.setConversationNo("AICHAT-" + compactUuid());
        conversation.setTitle(abbreviate(request.message().trim(), 200));
        conversation.setAgentCode(agentCode);
        conversation.setStatus("ACTIVE");
        conversation.setLastMessageAt(now);
        conversation.setCreatedBy(currentUserId());
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        conversationMapper.insert(conversation);
        return conversation;
    }

    private void validateConversationRequest(AiConversationTb conversation, AiChatRequest request) {
        if (TextUtils.hasText(request.agentCode())
                && !Objects.equals(conversation.getAgentCode(), request.agentCode().trim())) {
            throw new BusinessException("已有会话不能切换智能体");
        }
    }

    /** 校验结构化回复只消费当前会话最新、类型匹配的待处理交互。 */
    private void validateInteractionReply(AiChatRequest request, Map<String, Object> memory) {
        if (request.inputType() == AiChatRequest.InputType.TEXT) {
            return;
        }
        if (!TextUtils.hasText(request.conversationNo())) {
            throw new BusinessException("结构化回复必须属于一个已有会话");
        }
        if (!TextUtils.hasText(request.interactionId())
                || !TextUtils.hasText(request.replyToTurnId())) {
            throw new BusinessException("结构化回复必须提供interactionId和replyToTurnId");
        }
        Map<String, Object> interaction = objectMap(memory.get("pendingInteraction"));
        if (interaction.isEmpty()) {
            throw new BusinessException("当前会话没有等待处理的澄清或确认");
        }
        if (!Objects.equals(request.interactionId().trim(), interaction.get("interactionId"))
                || !Objects.equals(request.replyToTurnId().trim(), interaction.get("sourceTurnId"))) {
            throw new BusinessException("该澄清交互已过期，请根据最新问题重新操作");
        }
        String expectedType = request.inputType() == AiChatRequest.InputType.CLARIFICATION_REPLY
                ? "CLARIFICATION" : "CONFIRMATION";
        if (!Objects.equals(expectedType, interaction.get("interactionType"))) {
            throw new BusinessException("回复类型与当前待处理交互不匹配");
        }
        if ("CONFIRMATION".equals(expectedType)
                && objectMap(memory.get("pendingAction")).isEmpty()) {
            throw new BusinessException("当前没有可确认或取消的待执行动作");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(Object value) {
        return value instanceof Map<?, ?>
                ? (Map<String, Object>) value
                : Map.of();
    }

    /** 加载最近的原始消息；较早消息后续由会话摘要承载。 */
    private List<PythonAgentChatRequest.RecentMessage> loadRecentMessages(
            Long conversationId, int limit) {
        List<AiConversationMessageTb> messages = new ArrayList<>(messageMapper.selectList(
                Wrappers.<AiConversationMessageTb>lambdaQuery()
                        .eq(AiConversationMessageTb::getConversationId, conversationId)
                        .orderByDesc(AiConversationMessageTb::getId)
                        .last("limit " + limit)));
        Collections.reverse(messages);
        if (messages.isEmpty()) {
            return List.of();
        }
        List<Long> ids = messages.stream().map(AiConversationMessageTb::getId).toList();
        Map<Long, List<AiChatResponse.Reference>> references = referenceMapper.selectList(
                        Wrappers.<AiMessageRefTb>lambdaQuery()
                                .in(AiMessageRefTb::getMessageId, ids)
                                .orderByAsc(AiMessageRefTb::getId))
                .stream()
                .collect(Collectors.groupingBy(
                        AiMessageRefTb::getMessageId,
                        LinkedHashMap::new,
                        Collectors.mapping(this::toReference, Collectors.toList())));
        return messages.stream()
                .filter(message -> TextUtils.hasText(message.getRole())
                        && TextUtils.hasText(message.getContent()))
                .map(message -> new PythonAgentChatRequest.RecentMessage(
                        message.getId(), message.getTurnId(), message.getRole(), message.getContent(),
                        message.getMessageType(), message.getCreatedAt(),
                        references.getOrDefault(message.getId(), List.of())))
                .toList();
    }

    /**
     * 将会话表中的结构化长期记忆转换成内部接口对象。
     *
     * <p>memory_json 必须是 JSON 对象；空值表示当前会话还没有长期记忆。</p>
     */
    private Map<String, Object> parseConversationMemory(String memoryJson) {
        if (!TextUtils.hasText(memoryJson)) {
            return Map.of();
        }
        try {
            Map<String, Object> memory = objectMapper.readValue(
                    memoryJson, new TypeReference<Map<String, Object>>() { });
            if (memory == null) {
                throw new IllegalStateException("会话长期记忆必须是 JSON 对象");
            }
            return memory;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("会话长期记忆 JSON 格式错误", exception);
        }
    }

    /**
     * 将本轮页面焦点写入可变会话记忆。
     *
     * <p>businessContext为null表示调用方没有提供页面焦点，保留原值；传入空对象表示
     * 当前没有业务焦点，此时清除currentBusinessContext。该字段只用于后续指代消解，
     * 不参与会话查找，也不会替代训练预测任务自己的业务范围快照。</p>
     */
    private Map<String, Object> currentConversationMemory(
            AiConversationTb conversation, AiChatRequest.BusinessContext businessContext) {
        Map<String, Object> memory = new LinkedHashMap<>(
                parseConversationMemory(conversation.getMemoryJson()));
        if (businessContext == null) {
            return memory;
        }

        Map<String, Object> current = new LinkedHashMap<>();
        putIfText(current, "regionCode", businessContext.regionCode());
        putIfText(current, "regionName", businessContext.regionName());
        putIfText(current, "industryCode", businessContext.industryCode());
        putIfText(current, "industryName", businessContext.industryName());
        putIfText(current, "customerCode", businessContext.customerCode());
        putIfText(current, "customerName", businessContext.customerName());
        if (current.isEmpty()) {
            memory.remove("currentBusinessContext");
        } else {
            memory.put("currentBusinessContext", current);
        }
        conversation.setMemoryJson(memory.isEmpty() ? null : writeJson(memory));
        return memory;
    }

    /**
     * 将 Python 提供的受限记忆补丁合并到会话中。
     *
     * <p>Python 只能描述业务编排产生的工作状态；实际数据库读取、字段白名单、
     * JSON 序列化和事务提交全部由 Java 负责。非法补丁直接拒绝，避免 Agent
     * 响应意外污染会话记忆。</p>
     */
    private void applyMemoryPatch(AiConversationTb conversation, JsonNode patch) {
        if (patch == null || patch.isNull()) {
            return;
        }
        if (!patch.isObject()) {
            throw new IllegalStateException("Python 返回的 memoryPatch 必须是对象");
        }
        JsonNode set = patch.path("set");
        JsonNode remove = patch.path("remove");
        if (!set.isMissingNode() && !set.isObject()) {
            throw new IllegalStateException("memoryPatch.set 必须是对象");
        }
        if (!remove.isMissingNode() && !remove.isArray()) {
            throw new IllegalStateException("memoryPatch.remove 必须是数组");
        }

        Map<String, Object> memory = new LinkedHashMap<>(
                parseConversationMemory(conversation.getMemoryJson()));
        if (set.isObject()) {
            var fields = set.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                assertPatchKey(field.getKey());
                memory.put(field.getKey(), objectMapper.convertValue(field.getValue(), Object.class));
            }
        }
        if (remove.isArray()) {
            for (JsonNode key : remove) {
                if (!key.isTextual()) {
                    throw new IllegalStateException("memoryPatch.remove 只能包含字段名");
                }
                assertPatchKey(key.asText());
                memory.remove(key.asText());
            }
        }
        conversation.setMemoryJson(memory.isEmpty() ? null : writeJson(memory));
    }

    private void assertPatchKey(String key) {
        if (!MEMORY_PATCH_KEYS.contains(key)) {
            throw new IllegalStateException("memoryPatch 包含不允许的字段：" + key);
        }
    }

    private void putIfText(Map<String, Object> target, String key, String value) {
        if (TextUtils.hasText(value)) {
            target.put(key, value.trim());
        }
    }

    /**
     * 校验 Python 响应是否确实属于 Java 本次发出的 Agent 请求。
     *
     * <p>conversationNo 和 turnId 必须与 runNo 对应的数据库记录一致，防止 Python
     * 响应被保存到其他会话或其他轮次。</p>
     *
     * @param run Java 调用 Python 前创建的 Agent Run
     * @param conversation Agent Run 所属会话
     * @param result Python 返回的内部 Agent 执行结果
     * @throws IllegalStateException Python 响应标识与 Java 原请求不一致时抛出
     */
    private void verifyResultIdentity(AgentRunTb run, AiConversationTb conversation,
                                      PythonAgentChatResponse result) {
        AiConversationMessageTb user = loadAndValidateRunUserMessage(run);
        boolean valid = Objects.equals(conversation.getConversationNo(), result.conversationNo())
                && Objects.equals(user.getTurnId(), result.turnId());
        if (!valid) {
            throw new IllegalStateException("Python Agent 响应标识与 Java 请求不一致");
        }
    }

    /**
     * 获取触发 Agent Run 的用户消息，并校验 Run 与消息之间的数据库关联完整性。
     *
     * <p>消息不存在、消息属于其他会话或消息角色不是 USER，均说明数据库关联错误。</p>
     *
     * @param run 需要读取触发消息的 Agent Run
     * @return 与 run.messageId 对应且已通过关联校验的用户消息
     * @throws IllegalStateException Run 与用户消息的数据库关系无效时抛出
     */
    private AiConversationMessageTb loadAndValidateRunUserMessage(AgentRunTb run) {
        // run.messageId 保存的是创建 Run 前已经落库的用户消息主键。
        AiConversationMessageTb user = messageMapper.selectById(run.getMessageId());
        if (user == null) {
            throw new IllegalStateException(
                    "Agent Run 关联的用户消息不存在，runNo=" + run.getRunNo());
        }

        // 防止 messageId 错误地指向了另一个会话中的消息。
        if (!Objects.equals(user.getConversationId(), run.getConversationId())) {
            throw new IllegalStateException(
                    "Agent Run 与用户消息所属会话不一致，runNo=" + run.getRunNo());
        }

        // Agent Run 只能由 USER 消息触发，不能关联助手、系统或工具消息。
        if (!"USER".equalsIgnoreCase(user.getRole())) {
            throw new IllegalStateException(
                    "Agent Run 关联的消息不是用户消息，runNo=" + run.getRunNo());
        }
        return user;
    }

    private void saveReferences(Long messageId, List<AiChatResponse.Reference> references, Date now) {
        for (AiChatResponse.Reference reference : references) {
            AiMessageRefTb entity = new AiMessageRefTb();
            entity.setMessageId(messageId);
            entity.setRefType(reference.referenceType());
            entity.setRefNo(reference.referenceNo());
            entity.setRelationType(reference.relationType());
            entity.setCreatedAt(now);
            referenceMapper.insert(entity);
        }
    }

    private List<AiChatResponse.Reference> normalizeReferences(List<AiChatResponse.Reference> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, AiChatResponse.Reference> unique = new LinkedHashMap<>();
        for (AiChatResponse.Reference value : values) {
            if (value == null || !REFERENCE_TYPES.contains(value.referenceType())
                    || !RELATION_TYPES.contains(value.relationType())
                    || !TextUtils.hasText(value.referenceNo())) {
                throw new IllegalStateException("Python Agent 返回了无效的业务对象引用");
            }
            String number = value.referenceNo().trim();
            if (number.length() > 64) {
                throw new IllegalStateException("Python Agent 返回的业务对象编号过长");
            }
            AiChatResponse.Reference normalized = new AiChatResponse.Reference(
                    value.referenceType(), number, value.relationType(),
                    trimToNull(value.title()), trimToNull(value.status()));
            unique.put(value.referenceType() + "\u0000" + number + "\u0000" + value.relationType(), normalized);
        }
        return List.copyOf(unique.values());
    }

    /** WAITING 响应必须携带完整、可回传且与当前轮次绑定的结构化交互。 */
    private AiChatResponse.Clarification normalizeClarification(
            String status, String turnId, AiChatResponse.Clarification value) {
        if (!"WAITING".equals(status)) {
            return null;
        }
        if (value == null || !TextUtils.hasText(value.interactionId())
                || !INTERACTION_TYPES.contains(value.interactionType())
                || !Objects.equals(turnId, value.replyToTurnId())
                || !CLARIFICATION_REASONS.contains(value.reason())
                || !TextUtils.hasText(value.resumeFrom())
                || !TextUtils.hasText(value.prompt())
                || value.fields() == null || value.fields().isEmpty()) {
            throw new IllegalStateException("Python Agent 返回了不完整的待处理交互");
        }
        for (AiChatResponse.ClarificationField field : value.fields()) {
            if (field == null || !TextUtils.hasText(field.parameterCode())
                    || !TextUtils.hasText(field.parameterName())
                    || !CLARIFICATION_REASONS.contains(field.reason())
                    || !CLARIFICATION_INPUT_TYPES.contains(field.inputType())) {
                throw new IllegalStateException("Python Agent 返回了无效的澄清字段");
            }
            if (("SELECT".equals(field.inputType())
                    || "MULTI_SELECT".equals(field.inputType())
                    || "CONFIRM".equals(field.inputType()))
                    && (field.options() == null || field.options().isEmpty())) {
                throw new IllegalStateException("选择类澄清字段必须提供候选项");
            }
            for (AiChatResponse.ClarificationOption option : field.options()) {
                if (option == null || !TextUtils.hasText(option.value())
                        || !TextUtils.hasText(option.label())) {
                    throw new IllegalStateException("Python Agent 返回了无效的澄清候选项");
                }
            }
        }
        return value;
    }

    private AgentRunTb requireRun(String runNo) {
        AgentRunTb run = runMapper.selectOne(Wrappers.<AgentRunTb>lambdaQuery()
                .eq(AgentRunTb::getRunNo, runNo).last("limit 1"));
        if (run == null) {
            throw new IllegalStateException("Agent Run 不存在：" + runNo);
        }
        return run;
    }

    private AiConversationTb requireOwnedConversation(Long conversationId) {
        AiConversationTb conversation = conversationMapper.selectOne(
                Wrappers.<AiConversationTb>lambdaQuery()
                        .eq(AiConversationTb::getId, conversationId)
                        .eq(AiConversationTb::getCreatedBy, currentUserId())
                        .last("limit 1"));
        if (conversation == null) {
            throw new BusinessException("会话不存在或无权访问");
        }
        return conversation;
    }

    private AiChatResponse.Reference toReference(AiMessageRefTb value) {
        return new AiChatResponse.Reference(
                value.getRefType(), value.getRefNo(), value.getRelationType(), null, null);
    }

    private String agentRequestSnapshot(PythonAgentChatRequest request) {
        ObjectNode value = objectMapper.createObjectNode();
        value.put("conversationNo", request.conversationNo());
        value.put("turnId", request.turnId());
        value.put("runNo", request.runNo());
        value.put("message", request.message());
        value.put("inputType", request.inputType());
        value.put("interactionId", request.interactionId());
        value.put("replyToTurnId", request.replyToTurnId());
        value.set("answers", objectMapper.valueToTree(request.answers()));
        value.put("agentCode", request.agentCode());
        value.set("businessContext", objectMapper.valueToTree(request.businessContext()));
        ObjectNode context = value.putObject("conversationContext");
        context.put("summary", request.conversationContext().summary());
        context.set("memory", objectMapper.valueToTree(request.conversationContext().memory()));
        context.put("recentMessageCount", request.conversationContext().recentMessages().size());
        return writeJson(value);
    }

    private String runResultJson(PythonAgentChatResponse result, AiChatResponse response) {
        ObjectNode stored = objectMapper.createObjectNode();
        stored.set("agentResult", result.resultData() == null
                ? objectMapper.createObjectNode() : result.resultData());
        stored.set("response", objectMapper.valueToTree(response));
        return writeJson(stored);
    }

    private String writeNullableJson(JsonNode value) {
        return value == null || value.isNull() ? null : writeJson(value);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("会话结构化数据序列化失败", exception);
        }
    }

    private String normalizeRunStatus(String status) {
        String normalized = TextUtils.hasText(status) ? status.trim().toUpperCase() : "FAILED";
        return RUN_STATUSES.contains(normalized) ? normalized : "FAILED";
    }

    private String currentUserId() {
        Long userId = SecurityContextHolder.getUserId();
        if (userId == null) {
            throw new BusinessException("登录状态已失效");
        }
        return String.valueOf(userId);
    }

    private String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String trimToNull(String value) {
        return TextUtils.hasText(value) ? value.trim() : null;
    }

    private String abbreviate(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit);
    }

    private String safeError(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (!TextUtils.hasText(message)) {
            message = current.getClass().getSimpleName();
        }
        return abbreviate(message.trim(), 1000);
    }

}
