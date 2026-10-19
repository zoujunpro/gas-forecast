package com.gas.forecast.business.ai.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.forecast.business.ai.dto.AiChatResponse.ExecutionProgress;
import com.gas.forecast.business.ai.dto.AiChatResponse.ForecastResult;
import com.gas.forecast.business.ai.dto.AiConversationResponse;
import com.gas.forecast.business.service.ForecastConversationService;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.security.context.SecurityContextHolder;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.AiConversationMessageTb;
import com.gas.forecast.dao.domain.AiConversationTb;
import com.gas.forecast.dao.mapper.AiConversationMessageTbMapper;
import com.gas.forecast.dao.mapper.AiConversationTbMapper;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 智能体会话持久化服务。
 * <p>只负责当前用户的会话和消息落库，不调用 Agent，也不理解用户语义。</p>
 */
@Service
@RequiredArgsConstructor
public class AiConversationService {
    private final AiConversationTbMapper conversationMapper;
    private final AiConversationMessageTbMapper messageMapper;
    private final ForecastConversationService forecastConversationService;
    private final ObjectMapper objectMapper;

    /** 为 Python Agent 准备当前轮次，并归档用户消息。 */
    @Transactional
    public Map<String, Object> prepareTurn(String conversationNo, String forecastBatchNo, String turnId, String message,
                                           String agentType, Map<String, Object> requestMetadata) {
        requireTurnId(turnId);
        AiConversationTb conversation = requireConversation(conversationNo, forecastBatchNo, message);
        List<Map<String, String>> history = recentHistory(conversation.getId(), 12, turnId);
        if (findTurnMessage(conversation.getId(), turnId, "user") == null) {
            try { saveMessage(conversation.getId(), turnId, "user", message, null, null); }
            catch (DuplicateKeyException ignored) { /* 并发重试已由唯一键保存。 */ }
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (requestMetadata != null) metadata.putAll(requestMetadata);
        putIfText(metadata, "scene", conversation.getScene());
        putIfText(metadata, "businessType", conversation.getBusinessType());
        putIfText(metadata, "businessNo", conversation.getBusinessNo());
        putIfText(metadata, "forecastBatchNo", forecastBatchNo);
        putIfText(metadata, "lastTrainBatchNo", conversation.getLastTrainBatchNo());
        putIfText(metadata, "lastForecastBatchNo", conversation.getLastForecastBatchNo());
        putIfText(metadata, "agentType", agentType);
        metadata.put("turnId", turnId);
        return Map.of("conversationNo", conversation.getConversationNo(), "history", history, "metadata", metadata);
    }

    /** 归档 Agent 回复，并同步会话的确定性状态和批次引用。 */
    @Transactional
    public Map<String, Object> completeTurn(String conversationNo, String turnId, String status, String answer, String skillName,
                                            Map<String, Object> executionResult, Map<String, Object> payload) {
        requireTurnId(turnId);
        AiConversationTb conversation = findOwned(conversationNo);
        // 同一轮已归档时直接返回原会话引用，不能让迟到的重复请求覆盖最终状态。
        if (findTurnMessage(conversation.getId(), turnId, "assistant") != null) return turnResult(conversation);
        var execution = objectMapper.valueToTree(executionResult == null ? Map.of() : executionResult);
        conversation.setLastTrainBatchNo(firstText(execution, "trainBatchNo", conversation.getLastTrainBatchNo()));
        conversation.setLastForecastBatchNo(firstText(execution, "forecastBatchNo", conversation.getLastForecastBatchNo()));
        conversation.setStatus(status);
        conversation.setUpdatedAt(new Date());
        conversationMapper.updateById(conversation);
        if (findTurnMessage(conversation.getId(), turnId, "assistant") == null) {
            Map<String, Object> storedPayload = new LinkedHashMap<>();
            if (payload != null) storedPayload.putAll(payload);
            // 查询列表等展示必须持久化真实工具结果，不能只保存一个 LIST 展示标记。
            storedPayload.put("executionResult", executionResult == null ? Map.of() : executionResult);
            try { saveMessage(conversation.getId(), turnId, "assistant", answer, skillName, storedPayload); }
            catch (DuplicateKeyException ignored) { /* 并发重试已由唯一键保存。 */ }
        }
        return turnResult(conversation);
    }

    private Map<String, Object> turnResult(AiConversationTb conversation) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("conversationNo", conversation.getConversationNo());
        result.put("lastTrainBatchNo", conversation.getLastTrainBatchNo());
        result.put("lastForecastBatchNo", conversation.getLastForecastBatchNo());
        return result;
    }

    public List<AiConversationResponse> list() {
        return conversationMapper.selectList(Wrappers.<AiConversationTb>lambdaQuery().eq(AiConversationTb::getCreatedBy, currentUserId())
                .and(query -> query.eq(AiConversationTb::getScene, "GENERAL").or().isNull(AiConversationTb::getScene))
                .orderByDesc(AiConversationTb::getUpdatedAt).last("limit 50")).stream().map(item -> toResponse(item, false)).toList();
    }

    public AiConversationResponse detail(String conversationNo) { return toResponse(findOwned(conversationNo), true); }

    public AiConversationResponse forecastDetail(String forecastBatchNo) {
        return toResponse(forecastConversationService.require(forecastBatchNo, null), true);
    }

    public void rename(String conversationNo, String title) {
        AiConversationTb conversation = findOwned(conversationNo);
        conversation.setTitle(title.trim()); conversation.setUpdatedAt(new Date()); conversationMapper.updateById(conversation);
    }

    public void delete(String conversationNo) {
        AiConversationTb conversation = findOwned(conversationNo);
        messageMapper.delete(Wrappers.<AiConversationMessageTb>lambdaQuery().eq(AiConversationMessageTb::getConversationId, conversation.getId()));
        conversationMapper.deleteById(conversation.getId());
    }

    private AiConversationTb requireConversation(String conversationNo, String forecastBatchNo, String firstMessage) {
        if (TextUtils.hasText(forecastBatchNo)) return forecastConversationService.require(forecastBatchNo, conversationNo);
        if (TextUtils.hasText(conversationNo)) return findOwned(conversationNo);
        return createConversation(firstMessage);
    }

    private AiConversationTb createConversation(String firstMessage) {
        AiConversationTb value = baseConversation();
        String title = TextUtils.hasText(firstMessage) ? firstMessage : "新会话";
        value.setTitle(title.length() > 30 ? title.substring(0, 30) : title); value.setScene("GENERAL"); conversationMapper.insert(value);
        return value;
    }

    private AiConversationTb baseConversation() {
        Date now = new Date(); AiConversationTb value = new AiConversationTb();
        value.setConversationNo("AICHAT-" + UUID.randomUUID().toString().replace("-", "")); value.setStatus("ACTIVE");
        value.setCreatedBy(currentUserId()); value.setCreatedAt(now); value.setUpdatedAt(now); return value;
    }

    private AiConversationTb findOwned(String conversationNo) {
        AiConversationTb value = conversationMapper.selectOne(Wrappers.<AiConversationTb>lambdaQuery()
                .eq(AiConversationTb::getConversationNo, conversationNo).eq(AiConversationTb::getCreatedBy, currentUserId()).last("limit 1"));
        if (value == null) throw new BusinessException("会话不存在或无权访问"); return value;
    }

    private AiConversationResponse toResponse(AiConversationTb value, boolean includeMessages) {
        List<AiConversationResponse.Message> items = includeMessages ? messages(value.getId()).stream().map(message -> {
            StoredMessagePayload payload = readPayload(message.getStructuredJson());
            return new AiConversationResponse.Message(message.getId(), message.getRole(), message.getContent(), message.getCreatedAt(),
                    payload.progress(), payload.forecastResult(), payload.analysis(), payload.suggestedQuestions(), payload.presentation(), payload.executionResult());
        }).toList() : List.of();
        return new AiConversationResponse(value.getConversationNo(), value.getTitle(), value.getStatus(), value.getScene(), value.getBusinessType(), value.getBusinessNo(),
                value.getLastTrainBatchNo(), value.getLastForecastBatchNo(), value.getUpdatedAt(), items);
    }

    private List<Map<String, String>> recentHistory(Long conversationId, int limit, String excludedTurnId) {
        List<AiConversationMessageTb> all = messages(conversationId).stream()
                .filter(item -> !java.util.Objects.equals(excludedTurnId, item.getTurnId())).toList();
        int end = all.size(); int start = Math.max(0, end - limit);
        return all.subList(start, end).stream().filter(item -> TextUtils.hasText(item.getRole()) && TextUtils.hasText(item.getContent()))
                .map(item -> Map.of("role", item.getRole(), "content", item.getContent())).toList();
    }

    private AiConversationMessageTb findTurnMessage(Long conversationId, String turnId, String role) {
        return messageMapper.selectOne(Wrappers.<AiConversationMessageTb>lambdaQuery()
                .eq(AiConversationMessageTb::getConversationId, conversationId)
                .eq(AiConversationMessageTb::getTurnId, turnId)
                .eq(AiConversationMessageTb::getRole, role).last("limit 1"));
    }

    private List<AiConversationMessageTb> messages(Long conversationId) {
        return messageMapper.selectList(Wrappers.<AiConversationMessageTb>lambdaQuery()
                .eq(AiConversationMessageTb::getConversationId, conversationId).orderByAsc(AiConversationMessageTb::getId));
    }

    private void saveMessage(Long conversationId, String turnId, String role, String content, String code, Object structured) {
        AiConversationMessageTb value = new AiConversationMessageTb();
        value.setConversationId(conversationId); value.setTurnId(turnId); value.setRole(role); value.setContent(content); value.setMessageType(code); value.setCreatedAt(new Date());
        try { value.setStructuredJson(structured == null ? null : objectMapper.writeValueAsString(structured)); }
        catch (Exception exception) { value.setStructuredJson(null); }
        messageMapper.insert(value);
    }

    private StoredMessagePayload readPayload(String json) {
        if (!TextUtils.hasText(json)) return StoredMessagePayload.empty();
        try { return objectMapper.readValue(json, StoredMessagePayload.class); } catch (Exception exception) { return StoredMessagePayload.empty(); }
    }

    private String firstText(com.fasterxml.jackson.databind.JsonNode node, String name, String fallback) {
        String found = findText(node, name); return TextUtils.hasText(found) ? found : fallback;
    }

    private String findText(com.fasterxml.jackson.databind.JsonNode node, String name) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        com.fasterxml.jackson.databind.JsonNode direct = node.get(name);
        if (direct != null && direct.isValueNode() && TextUtils.hasText(direct.asText())) return direct.asText();
        if (node.isContainerNode()) for (com.fasterxml.jackson.databind.JsonNode child : node) { String value = findText(child, name); if (TextUtils.hasText(value)) return value; }
        return null;
    }

    private void putIfText(Map<String, Object> values, String key, String value) { if (TextUtils.hasText(value)) values.put(key, value); }
    private void requireTurnId(String turnId) {
        if (!TextUtils.hasText(turnId) || turnId.length() > 64) throw new BusinessException("turnId不能为空且长度不能超过64个字符");
    }
    private String currentUserId() { return String.valueOf(SecurityContextHolder.getUserId()); }

    private record StoredMessagePayload(ExecutionProgress progress, ForecastResult forecastResult, String analysis,
                                        List<String> suggestedQuestions, JsonNode presentation, JsonNode executionResult) {
        private StoredMessagePayload { suggestedQuestions = suggestedQuestions == null ? List.of() : suggestedQuestions; }
        private static StoredMessagePayload empty() { return new StoredMessagePayload(null, null, null, List.of(), null, null); }
    }
}
