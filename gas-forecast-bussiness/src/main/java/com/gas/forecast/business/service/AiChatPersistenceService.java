package com.gas.forecast.business.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gas.forecast.business.dto.AiChatRequest;
import com.gas.forecast.business.dto.AiChatResponse;
import com.gas.forecast.business.dto.PythonAgentChatRequest;
import com.gas.forecast.business.dto.PythonAgentChatResponse;
import com.gas.forecast.business.enums.AgentRunErrorCodeEnum;
import com.gas.forecast.business.enums.AgentRunStatusEnum;
import com.gas.forecast.business.enums.AiAgentCodeEnum;
import com.gas.forecast.business.enums.AiClarificationInputTypeEnum;
import com.gas.forecast.business.enums.AiClarificationReasonEnum;
import com.gas.forecast.business.enums.AiConversationStatusEnum;
import com.gas.forecast.business.enums.AiIntentEnum;
import com.gas.forecast.business.enums.AiInteractionTypeEnum;
import com.gas.forecast.business.enums.AiMessageReferenceRelationEnum;
import com.gas.forecast.business.enums.AiMessageReferenceTypeEnum;
import com.gas.forecast.business.enums.AiMessageRoleEnum;
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
    /** 会话只保留未完成操作和页面焦点，不再加载上一任务的参数或工具结果。 */
    private static final Set<String> MEMORY_KEYS = Set.of("pendingOperation", "currentBusinessContext");

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
        userMessage.setRole(AiMessageRoleEnum.USER.getCode());
        userMessage.setTurnId(turnId);
        userMessage.setContent(request.message().trim());
        userMessage.setCreatedAt(now);
        messageMapper.insert(userMessage);

        String runNo = "RUN-" + compactUuid();
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
                conversation.getAgentCode(),
                context,
                conversationContext);

        AgentRunTb run = new AgentRunTb();
        run.setRunNo(runNo);
        run.setConversationId(conversation.getId());
        run.setMessageId(userMessage.getId());
        run.setAgentCode(conversation.getAgentCode());
        run.setStatus(AgentRunStatusEnum.RUNNING.getCode());
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
        assistant.setRole(AiMessageRoleEnum.ASSISTANT.getCode());
        assistant.setTurnId(result.turnId());
        assistant.setContent(answer);
        assistant.setStructuredJson(writeJson(response));
        assistant.setCreatedAt(now);
        messageMapper.insert(assistant);
        saveReferences(assistant.getId(), references, now);

        run.setStatus(status);
        run.setIntentCode(trimToNull(result.intentCode()));
        run.setIntentJson(writeNullableJson(result.intentData()));
        run.setSkillCode(trimToNull(result.skillCode()));
        run.setResultJson(runResultJson(result, response));
        run.setErrorCode(AgentRunStatusEnum.FAILED.getCode().equals(status)
                ? trimToNull(result.errorCode()) : null);
        run.setErrorMessage(AgentRunStatusEnum.FAILED.getCode().equals(status)
                ? trimToNull(result.errorMessage()) : null);
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
                request.agentCode(), AgentRunStatusEnum.FAILED.getCode(), message, null, List.of(),
                AiIntentEnum.CHAT.getCode(),
                new PythonAgentChatResponse.IntentData(Map.of(), Map.of()), null,
                new PythonAgentChatResponse.ResultData(null, null, null, List.of()),
                null, null, null,
                AgentRunErrorCodeEnum.AGENT_SERVICE_ERROR.getCode(), safeError(exception));
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
            if (!AiConversationStatusEnum.ACTIVE.getCode().equalsIgnoreCase(existing.getStatus())) {
                throw new BusinessException("当前会话已归档，不能继续发送消息");
            }
            return existing;
        }

        String agentCode = TextUtils.hasText(request.agentCode())
                ? request.agentCode().trim() : AiAgentCodeEnum.GENERAL.getCode();
        if (!AiAgentCodeEnum.contains(agentCode)) {
            throw new BusinessException("不支持的智能体编码：" + agentCode);
        }
        AiConversationTb conversation = new AiConversationTb();
        conversation.setConversationNo("AICHAT-" + compactUuid());
        conversation.setTitle(abbreviate(request.message().trim(), 200));
        conversation.setAgentCode(agentCode);
        conversation.setStatus(AiConversationStatusEnum.ACTIVE.getCode());
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
        Map<String, Object> pendingOperation = objectMap(memory.get("pendingOperation"));
        Map<String, Object> interaction = objectMap(pendingOperation.get("interaction"));
        if (interaction.isEmpty()) {
            throw new BusinessException("当前会话没有等待处理的澄清或确认");
        }
        if (!Objects.equals(request.interactionId().trim(), interaction.get("interactionId"))
                || !Objects.equals(request.replyToTurnId().trim(), interaction.get("sourceTurnId"))) {
            throw new BusinessException("该澄清交互已过期，请根据最新问题重新操作");
        }
        String expectedType = request.inputType() == AiChatRequest.InputType.CLARIFICATION_REPLY
                ? AiInteractionTypeEnum.CLARIFICATION.getCode()
                : AiInteractionTypeEnum.CONFIRMATION.getCode();
        if (!Objects.equals(expectedType, interaction.get("interactionType"))) {
            throw new BusinessException("回复类型与当前待处理交互不匹配");
        }
        if (AiInteractionTypeEnum.CONFIRMATION.getCode().equals(expectedType)
                && !TextUtils.hasText(Objects.toString(pendingOperation.get("pendingTool"), ""))) {
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
        // 历史会话可能保留已废弃的引用类型。当前协议只识别执行任务和预测结果；
        // 旧引用保留在数据库审计，但不透传给 Agent。
        Map<Long, List<AiChatResponse.Reference>> references = referenceMapper.selectList(
                        Wrappers.<AiMessageRefTb>lambdaQuery()
                                .in(AiMessageRefTb::getMessageId, ids)
                                .orderByAsc(AiMessageRefTb::getId))
                .stream()
                .filter(reference -> AiMessageReferenceTypeEnum.contains(reference.getRefType()))
                .collect(Collectors.groupingBy(
                        AiMessageRefTb::getMessageId,
                        LinkedHashMap::new,
                        Collectors.mapping(this::toReference, Collectors.toList())));
        return messages.stream()
                .filter(message -> TextUtils.hasText(message.getRole())
                        && TextUtils.hasText(message.getContent()))
                .map(message -> new PythonAgentChatRequest.RecentMessage(
                        message.getId(), message.getTurnId(), message.getRole(), message.getContent(),
                        message.getCreatedAt(),
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
            // 不兼容旧的操作快照；旧记录中的历史缓存不再参与后续执行。
            memory.keySet().retainAll(MEMORY_KEYS);
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
    private void applyMemoryPatch(AiConversationTb conversation, PythonAgentChatResponse.MemoryPatch patch) {
        if (patch == null) {
            return;
        }
        Map<String, Object> memory = new LinkedHashMap<>(
                parseConversationMemory(conversation.getMemoryJson()));
        if (patch.pendingOperation() == null) {
            memory.remove("pendingOperation");
        } else {
            Map<String, Object> operation = patch.pendingOperation();
            Map<String, Object> interaction = objectMap(operation.get("interaction"));
            if (interaction.isEmpty() || objectMap(operation.get("semantics")).isEmpty()) {
                throw new IllegalStateException("pendingOperation 缺少有效的交互身份或原请求语义");
            }
            memory.put("pendingOperation", operation);
        }
        conversation.setMemoryJson(memory.isEmpty() ? null : writeJson(memory));
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
        if (!AiMessageRoleEnum.USER.getCode().equalsIgnoreCase(user.getRole())) {
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
            if (value == null || !AiMessageReferenceTypeEnum.contains(value.referenceType())
                    || !AiMessageReferenceRelationEnum.contains(value.relationType())
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
        if (!AgentRunStatusEnum.WAITING.getCode().equals(status)) {
            return null;
        }
        if (value == null || !TextUtils.hasText(value.interactionId())
                || !AiInteractionTypeEnum.contains(value.interactionType())
                || !Objects.equals(turnId, value.replyToTurnId())
                || !AiClarificationReasonEnum.contains(value.reason())
                || !TextUtils.hasText(value.resumeFrom())
                || !TextUtils.hasText(value.prompt())
                || value.fields() == null || value.fields().isEmpty()) {
            throw new IllegalStateException("Python Agent 返回了不完整的待处理交互");
        }
        for (AiChatResponse.ClarificationField field : value.fields()) {
            if (field == null || !TextUtils.hasText(field.parameterCode())
                    || !TextUtils.hasText(field.parameterName())
                    || !AiClarificationReasonEnum.contains(field.reason())
                    || !AiClarificationInputTypeEnum.contains(field.inputType())) {
                throw new IllegalStateException("Python Agent 返回了无效的澄清字段");
            }
            if ((AiClarificationInputTypeEnum.SELECT.getCode().equals(field.inputType())
                    || AiClarificationInputTypeEnum.MULTI_SELECT.getCode().equals(field.inputType())
                    || AiClarificationInputTypeEnum.CONFIRM.getCode().equals(field.inputType()))
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
        stored.set("agentResult", objectMapper.valueToTree(
                result.resultData() == null ? Map.of() : result.resultData()));
        stored.set("response", objectMapper.valueToTree(response));
        return writeJson(stored);
    }

    private String writeNullableJson(Object value) {
        return value == null ? null : writeJson(value);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("会话结构化数据序列化失败", exception);
        }
    }

    private String normalizeRunStatus(String status) {
        return AgentRunStatusEnum.normalize(status);
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
