package com.gas.forecast.business.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gas.forecast.common.util.HttpUtil;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.common.util.ThreadLocalUtil;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 调用 Python Agent 主流程处理无需预测结果上下文的普通会话。 */
@Component
public class AgentConversationClient {
    private final ObjectMapper objectMapper;

    @Value("${gas.agent.conversation-base-url:http://127.0.0.1:8091/api/v1/conversations}")
    private String conversationBaseUrl;
    @Value("${gas.agent.connect-timeout-ms:5000}")
    private int connectTimeoutMs;
    @Value("${gas.analysis.timeout-seconds:120}")
    private int timeoutSeconds;

    public AgentConversationClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 由 Agent 完成能力识别和回答；Java 仅保留会话与消息持久化。 */
    public AgentReply chat(String conversationNo, String message) {
        return chat(conversationNo, message, List.of());
    }

    public AgentReply chat(String conversationNo, String message, List<Map<String, String>> history) {
        return chat(conversationNo, message, history, null, null);
    }

    public AgentReply chat(String conversationNo, String message, List<Map<String, String>> history,
                           String agentType, Map<String, Object> metadata) {
        if (!TextUtils.hasText(conversationNo) || !TextUtils.hasText(message))
            throw new IllegalArgumentException("会话编号和消息不能为空");
        ObjectNode request = objectMapper.createObjectNode();
        request.put("message", message);
        request.set("history", objectMapper.valueToTree(history == null ? List.of() : history));
        if (TextUtils.hasText(agentType))
            request.put("agent_type", agentType);
        request.set("metadata", objectMapper.valueToTree(metadata == null ? Map.of() : metadata));
        Map<String, String> headers = new LinkedHashMap<>();
        Long userId = ThreadLocalUtil.getUserId();
        String token = ThreadLocalUtil.getToken();
        String traceId = MDC.get("x-traceId");
        if (!TextUtils.hasText(traceId))
            traceId = UUID.randomUUID().toString().replace("-", "");
        if (userId != null)
            headers.put("x-user", String.valueOf(userId));
        headers.put("x-traceId", traceId);
        if (TextUtils.hasText(token))
            headers.put("Authorization", "Bearer " + token);
        JsonNode response = HttpUtil.postJson(
                conversationBaseUrl + "/" + conversationNo + "/messages",
                request,
                headers,
                connectTimeoutMs,
                Math.max(timeoutSeconds, 10) * 1000);
        String answer = response.path("message").asText("").trim();
        if (!TextUtils.hasText(answer))
            throw new IllegalStateException("Python Agent未返回会话回答");
        return new AgentReply(
                response.path("status").asText("COMPLETED"),
                answer,
                response.path("capability_code").asText(null),
                response.path("parameters"),
                response.path("execution_result"),
                response.path("presentation"),
                response.path("progress"),
                response.path("forecast_result"));
    }

    public record AgentReply(String status, String answer, String capabilityCode, JsonNode parameters, JsonNode executionResult, JsonNode presentation, JsonNode progress, JsonNode forecastResult) {
    }
}
