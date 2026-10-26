package com.gas.forecast.business.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.forecast.business.dto.AiChatRequest;
import com.gas.forecast.business.dto.AiChatResponse;
import com.gas.forecast.business.dto.AiConversationResponse;
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
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 查询和维护当前用户的会话；聊天写入由 {@link AiChatPersistenceService} 负责。 */
@Service
@RequiredArgsConstructor
public class AiConversationService {
    private final AiConversationTbMapper conversationMapper;
    private final AiConversationMessageTbMapper messageMapper;
    private final AiMessageRefTbMapper referenceMapper;
    private final AgentRunTbMapper runMapper;
    private final ObjectMapper objectMapper;

    /** 最近会话列表，不加载消息正文。 */
    public List<AiConversationResponse> list() {
        return conversationMapper.selectList(Wrappers.<AiConversationTb>lambdaQuery()
                        .eq(AiConversationTb::getCreatedBy, currentUserId())
                        .eq(AiConversationTb::getDeletedFlag, 0)
                        .orderByDesc(AiConversationTb::getLastMessageAt)
                        .orderByDesc(AiConversationTb::getUpdatedAt)
                        .last("limit 50"))
                .stream()
                .map(item -> toResponse(item, null, 0))
                .toList();
    }

    /** 按会话编号向前分页加载消息；首次请求不传游标，返回最新消息。 */
    public AiConversationResponse detail(String conversationNo, Long beforeMessageId, int pageSize) {
        return toResponse(findOwned(conversationNo), beforeMessageId, Math.min(Math.max(pageSize, 1), 100));
    }

    public void rename(String conversationNo, String title) {
        AiConversationTb conversation = findOwned(conversationNo);
        conversation.setTitle(title.trim());
        conversation.setUpdatedAt(new Date());
        conversationMapper.updateById(conversation);
    }

    /**
     * 逻辑删除会话；保留消息、消息引用和 Agent Run，用于审计和排障。
     * 后台训练预测业务数据不受影响。
     */
    @Transactional
    public void delete(String conversationNo) {
        AiConversationTb conversation = findOwned(conversationNo);
        conversation.setDeletedFlag(1);
        conversation.setDeletedAt(new Date());
        conversation.setUpdatedAt(new Date());
        conversationMapper.updateById(conversation);
    }

    private AiConversationResponse toResponse(AiConversationTb conversation, Long beforeMessageId, int pageSize) {
        MessagePage page = pageSize > 0
                ? messageResponses(conversation.getId(), beforeMessageId, pageSize)
                : new MessagePage(List.of(), false);
        return new AiConversationResponse(
                conversation.getConversationNo(),
                conversation.getTitle(),
                conversation.getAgentCode(),
                currentBusinessContext(conversation.getMemoryJson()),
                conversation.getStatus(),
                conversation.getLastMessageAt(),
                conversation.getUpdatedAt(),
                page.messages(),
                page.hasMore());
    }

    /** 从可变会话记忆中读取最近一次已经确认或实际使用的业务焦点。 */
    private AiChatRequest.BusinessContext currentBusinessContext(String memoryJson) {
        if (!TextUtils.hasText(memoryJson)) {
            return null;
        }
        try {
            var value = objectMapper.readTree(memoryJson).get("currentBusinessContext");
            if (value == null || !value.isObject()) {
                return null;
            }
            return objectMapper.treeToValue(value, AiChatRequest.BusinessContext.class);
        } catch (Exception exception) {
            throw new IllegalStateException("会话当前业务焦点JSON格式错误", exception);
        }
    }

    private MessagePage messageResponses(Long conversationId, Long beforeMessageId, int pageSize) {
        List<AiConversationMessageTb> messages = messageMapper.selectList(Wrappers.<AiConversationMessageTb>lambdaQuery()
                .eq(AiConversationMessageTb::getConversationId, conversationId)
                .lt(beforeMessageId != null, AiConversationMessageTb::getId, beforeMessageId)
                .orderByDesc(AiConversationMessageTb::getId)
                .last("limit " + (pageSize + 1)));
        if (messages.isEmpty()) {
            return new MessagePage(List.of(), false);
        }
        boolean hasMore = messages.size() > pageSize;
        if (hasMore) messages.remove(messages.size() - 1);
        java.util.Collections.reverse(messages);
        List<Long> messageIds = messages.stream().map(AiConversationMessageTb::getId).toList();
        Map<Long, List<AiChatResponse.Reference>> references = referenceMapper.selectList(
                        Wrappers.<AiMessageRefTb>lambdaQuery()
                                .in(AiMessageRefTb::getMessageId, messageIds)
                                .orderByAsc(AiMessageRefTb::getId))
                .stream()
                .collect(Collectors.groupingBy(
                        AiMessageRefTb::getMessageId,
                        LinkedHashMap::new,
                        Collectors.mapping(this::toReference, Collectors.toList())));

        Map<String, AgentRunTb> runsByTurn = new LinkedHashMap<>();
        Map<Long, AgentRunTb> runsByMessage = runMapper.selectList(
                        Wrappers.<AgentRunTb>lambdaQuery()
                                .eq(AgentRunTb::getConversationId, conversationId)
                                .orderByAsc(AgentRunTb::getId))
                .stream()
                .collect(Collectors.toMap(
                        AgentRunTb::getMessageId,
                        item -> item,
                        (left, right) -> right,
                        LinkedHashMap::new));
        for (AiConversationMessageTb message : messages) {
            AgentRunTb run = runsByMessage.get(message.getId());
            if (run != null && TextUtils.hasText(message.getTurnId())) {
                runsByTurn.put(message.getTurnId(), run);
            }
        }

        List<AiConversationResponse.Message> items = messages.stream().map(message -> {
            AgentRunTb run = runsByTurn.get(message.getTurnId());
            AiChatResponse stored = readStoredResponse(message.getStructuredJson());
            List<AiChatResponse.Reference> messageReferences = references.getOrDefault(
                    message.getId(), List.of());
            if (stored != null && !stored.references().isEmpty()) {
                messageReferences = mergeReferenceDisplay(messageReferences, stored.references());
            }
            return new AiConversationResponse.Message(
                    message.getId(),
                    message.getTurnId(),
                    normalizeRole(message.getRole()),
                    message.getContent(),
                    run == null ? null : run.getRunNo(),
                    run == null ? null : run.getStatus(),
                    stored == null ? null : stored.clarification(),
                    stored == null ? null : stored.presentation(),
                    stored == null ? null : stored.executionResult(),
                    messageReferences,
                    message.getCreatedAt());
        }).toList();
        return new MessagePage(items, hasMore);
    }

    private List<AiChatResponse.Reference> mergeReferenceDisplay(
            List<AiChatResponse.Reference> persisted,
            List<AiChatResponse.Reference> stored) {
        Map<String, AiChatResponse.Reference> display = stored.stream().collect(Collectors.toMap(
                item -> referenceKey(item.referenceType(), item.referenceNo(), item.relationType()),
                item -> item,
                (left, right) -> right));
        return persisted.stream().map(item -> {
            AiChatResponse.Reference value = display.get(referenceKey(
                    item.referenceType(), item.referenceNo(), item.relationType()));
            return value == null ? item : value;
        }).toList();
    }

    private String referenceKey(String type, String number, String relation) {
        return type + "\u0000" + number + "\u0000" + relation;
    }

    private AiChatResponse.Reference toReference(AiMessageRefTb value) {
        return new AiChatResponse.Reference(
                value.getRefType(), value.getRefNo(), value.getRelationType(), null, null);
    }

    private AiChatResponse readStoredResponse(String json) {
        if (!TextUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, AiChatResponse.class);
        } catch (Exception ignored) {
            // 历史消息可能仍是旧 structured_json 格式；正文仍可正常展示。
            return null;
        }
    }

    private String normalizeRole(String role) {
        return TextUtils.hasText(role) ? role.trim().toLowerCase() : role;
    }

    private List<AiConversationMessageTb> messages(Long conversationId) {
        return messageMapper.selectList(Wrappers.<AiConversationMessageTb>lambdaQuery()
                .eq(AiConversationMessageTb::getConversationId, conversationId)
                .orderByAsc(AiConversationMessageTb::getId));
    }

    private record MessagePage(List<AiConversationResponse.Message> messages, boolean hasMore) { }

    private AiConversationTb findOwned(String conversationNo) {
        if (!TextUtils.hasText(conversationNo)) {
            throw new BusinessException("会话编号不能为空");
        }
        AiConversationTb conversation = conversationMapper.selectOne(
                Wrappers.<AiConversationTb>lambdaQuery()
                        .eq(AiConversationTb::getConversationNo, conversationNo.trim())
                        .eq(AiConversationTb::getCreatedBy, currentUserId())
                        .eq(AiConversationTb::getDeletedFlag, 0)
                        .last("limit 1"));
        if (conversation == null) {
            throw new BusinessException("会话不存在或无权访问");
        }
        return conversation;
    }

    private AiConversationTb requireOwned(Long conversationId) {
        AiConversationTb conversation = conversationMapper.selectOne(
                Wrappers.<AiConversationTb>lambdaQuery()
                        .eq(AiConversationTb::getId, conversationId)
                        .eq(AiConversationTb::getCreatedBy, currentUserId())
                        .eq(AiConversationTb::getDeletedFlag, 0)
                        .last("limit 1"));
        if (conversation == null) {
            throw new BusinessException("会话不存在或无权访问");
        }
        return conversation;
    }

    private String currentUserId() {
        Long userId = SecurityContextHolder.getUserId();
        if (userId == null) {
            throw new BusinessException("登录状态已失效");
        }
        return String.valueOf(userId);
    }
}
