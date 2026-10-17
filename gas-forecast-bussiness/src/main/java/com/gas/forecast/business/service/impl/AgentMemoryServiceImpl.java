package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.forecast.business.service.AgentMemoryService;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.security.context.SecurityContextHolder;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.AiConversationTb;
import com.gas.forecast.dao.mapper.AiConversationTbMapper;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 基于现有ai_conversation_tb实现用户隔离的会话记忆。 */
@Service
@RequiredArgsConstructor
public class AgentMemoryServiceImpl implements AgentMemoryService {
    private final AiConversationTbMapper conversationMapper;
    private final ObjectMapper objectMapper;

    @Override
    public Map<String, Object> get(String conversationNo) {
        AiConversationTb conversation = findOwned(conversationNo);
        Map<String, Object> memory = parse(conversation.getMemoryJson());
        memory.put("conversationNo", conversation.getConversationNo());
        memory.put("agentCode", conversation.getAgentCode());
        memory.put("status", conversation.getStatus());
        memory.put("lastTrainBatchNo", conversation.getLastTrainBatchNo());
        memory.put("lastForecastBatchNo", conversation.getLastForecastBatchNo());
        // Python Agent维护的pendingAction优先；pendingIntentJson仅供旧Java流程兼容，不能反向覆盖新状态。
        if (!memory.containsKey("pendingAction") && TextUtils.hasText(conversation.getPendingIntentJson()))
            memory.put("pendingAction", parseValue(conversation.getPendingIntentJson()));
        return memory;
    }

    @Override
    public Map<String, Object> update(String conversationNo, String operation, Map<String, Object> values) {
        AiConversationTb conversation = findOwned(conversationNo);
        Map<String, Object> memory = parse(conversation.getMemoryJson());
        String normalized = TextUtils.hasText(operation) ? operation.trim().toUpperCase() : "UPSERT";
        switch (normalized) {
            case "CLEAR" -> memory.clear();
            case "DELETE" -> {
                if (values != null)
                    values.keySet().forEach(memory::remove);
            }
            case "UPSERT" -> {
                if (values != null)
                    memory.putAll(values);
            }
            default -> throw new BusinessException("operation只支持UPSERT、DELETE或CLEAR");
        }
        try {
            conversation.setMemoryJson(objectMapper.writeValueAsString(memory));
        } catch (Exception exception) {
            throw new BusinessException("记忆内容无法序列化");
        }
        conversation.setUpdatedAt(new Date());
        conversationMapper.updateById(conversation);
        return get(conversationNo);
    }

    private AiConversationTb findOwned(String conversationNo) {
        if (!TextUtils.hasText(conversationNo))
            throw new BusinessException("会话编号不能为空");
        String userId = String.valueOf(SecurityContextHolder.getUserId());
        AiConversationTb row = conversationMapper.selectOne(Wrappers.<AiConversationTb>lambdaQuery().eq(AiConversationTb::getConversationNo, conversationNo)
                .eq(AiConversationTb::getCreatedBy, userId).last("limit 1"));
        if (row == null)
            throw new BusinessException("会话不存在或无权访问");
        return row;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String json) {
        if (!TextUtils.hasText(json))
            return new HashMap<>();
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception exception) {
            return new HashMap<>();
        }
    }

    private Object parseValue(String json) {
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception exception) {
            return null;
        }
    }
}
