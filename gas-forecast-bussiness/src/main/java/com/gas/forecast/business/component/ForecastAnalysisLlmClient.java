package com.gas.forecast.business.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gas.forecast.common.util.TextUtils;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** OpenAI 兼容的大模型分析客户端。 */
@Component
public class ForecastAnalysisLlmClient {
    private final ObjectMapper objectMapper;
    private final ForecastPromptProvider promptProvider;
    // Python Agent 由 Uvicorn 提供 HTTP/1.1 服务。JDK HttpClient 默认会在明文连接上
    // 尝试 h2c 升级，Uvicorn 会把该请求判定为无效 HTTP 请求，因此这里显式固定协议版本。
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${gas.analysis.mode:api}")
    private String mode;
    @Value("${gas.analysis.local-base-url:http://192.168.0.105:9997/v1}")
    private String localBaseUrl;
    @Value("${gas.analysis.local-model:qwen3.6-27b}")
    private String localModel;
    @Value("${gas.analysis.local-api-key:local}")
    private String localApiKey;
    @Value("${gas.analysis.api-base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String apiBaseUrl;
    @Value("${gas.analysis.api-model:qwen-plus}")
    private String apiModel;
    @Value("${gas.analysis.api-key:}")
    private String remoteApiKey;
    @Value("${gas.analysis.timeout-seconds:120}")
    private long timeoutSeconds;
    @Value("${gas.agent.llm-chat-url:http://127.0.0.1:8091/api/v1/llm/chat-completions}")
    private String agentChatUrl;

    public ForecastAnalysisLlmClient(ObjectMapper objectMapper, ForecastPromptProvider promptProvider) {
        this.objectMapper = objectMapper;
        this.promptProvider = promptProvider;
    }

    public String model() {
        return localMode() ? localModel : apiModel;
    }

    public String analyze(String agentCode, String prompt) {
        validateConfig();
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model());
            body.put("temperature", 0.7);
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", promptProvider.systemPrompt(agentCode));
            messages.addObject().put("role", "user").put("content", prompt);
            return execute(body);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("大模型分析任务已中断", exception);
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException stateException)
                throw stateException;
            throw new IllegalStateException("大模型分析请求失败：" + exception.getMessage(), exception);
        }
    }

    /** 使用调用方提供的系统提示执行一次非流式结构化任务。 */
    public String complete(String systemPrompt, String userPrompt, double temperature) {
        validateConfig();
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model());
            body.put("temperature", temperature);
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemPrompt);
            messages.addObject().put("role", "user").put("content", userPrompt);
            return execute(body);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("大模型结构化任务已中断", exception);
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException stateException)
                throw stateException;
            throw new IllegalStateException("大模型结构化任务失败：" + exception.getMessage(), exception);
        }
    }

    public String chat(String agentCode, String message, JsonNode context, JsonNode conversationHistory) {
        if (!TextUtils.hasText(message))
            throw new IllegalArgumentException("对话内容不能为空");
        validateConfig();
        try {
            return execute(chatBody(agentCode, message, context, conversationHistory));
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException stateException)
                throw stateException;
            throw new IllegalStateException("大模型对话请求失败：" + exception.getMessage(), exception);
        }
    }

    /** 将上游 OpenAI 兼容接口返回的增量文本实时转发给调用方。 */
    public String chatStream(String agentCode, String message, JsonNode context, JsonNode conversationHistory, StreamConsumer consumer) {
        if (!TextUtils.hasText(message))
            throw new IllegalArgumentException("对话内容不能为空");
        validateConfig();
        try {
            ObjectNode body = chatBody(agentCode, message, context, conversationHistory);
            body.put("stream", true);
            return executeStream(body, consumer);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("大模型对话任务已中断", exception);
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException stateException)
                throw stateException;
            throw new IllegalStateException("大模型对话请求失败：" + exception.getMessage(), exception);
        }
    }

    private ObjectNode chatBody(String agentCode, String message, JsonNode context, JsonNode conversationHistory) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model());
        body.put("temperature", 0.7);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", promptProvider.systemPrompt(agentCode));
        if (context != null && !context.isNull() && !context.isMissingNode()) {
            String contextText = objectMapper.writeValueAsString(context);
            messages.addObject().put("role", "system").put("content", "当前预测上下文：" + abbreviate(contextText, 30000));
        }
        if (conversationHistory != null && conversationHistory.isArray()) {
            int start = Math.max(0, conversationHistory.size() - 8);
            for (int i = start; i < conversationHistory.size(); i++) {
                JsonNode item = conversationHistory.get(i);
                String role = item.path("role").asText("");
                String content = item.path("content").asText("").trim();
                if (("user".equals(role) || "assistant".equals(role)) && TextUtils.hasText(content))
                    messages.addObject().put("role", role).put("content", abbreviate(content, 8000));
            }
        }
        messages.addObject().put("role", "user").put("content", message.trim());
        return body;
    }

    private String execute(ObjectNode body) throws Exception {
        HttpResponse<String> response = httpClient.send(request(body), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IllegalStateException("大模型接口返回 HTTP " + response.statusCode() + "：" + abbreviate(response.body(), 300));
        JsonNode json = objectMapper.readTree(response.body());
        String content = json.path("choices").path(0).path("message").path("content").asText("").trim();
        if (!TextUtils.hasText(content))
            throw new IllegalStateException("大模型接口未返回内容");
        return content;
    }

    private String executeStream(ObjectNode body, StreamConsumer consumer) throws Exception {
        // Python Agent统一持有模型连接；当前以完整回答回调一次，外层SSE协议保持不变。
        body.remove("stream");
        String content = execute(body);
        consumer.accept(content);
        return content;
    }

    private HttpRequest request(ObjectNode body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(chatCompletionsUrl())).timeout(Duration.ofSeconds(Math.max(timeoutSeconds, 10))).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        return request.build();
    }

    @FunctionalInterface
    public interface StreamConsumer {
        void accept(String content) throws Exception;
    }

    private void validateConfig() {
        if (!TextUtils.hasText(agentChatUrl))
            throw new IllegalStateException("未配置Python Agent模型网关地址");
    }

    private boolean localMode() {
        String value = mode == null ? "" : mode.trim().toLowerCase();
        return "local".equals(value) || "native".equals(value) || "私有化".equals(value);
    }

    private String baseUrl() {
        return localMode() ? localBaseUrl : apiBaseUrl;
    }

    private String apiKey() {
        return localMode() ? localApiKey : remoteApiKey;
    }

    private String chatCompletionsUrl() {
        return agentChatUrl;
    }

    private String abbreviate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength)
            return text;
        return text.substring(0, maxLength);
    }
}
