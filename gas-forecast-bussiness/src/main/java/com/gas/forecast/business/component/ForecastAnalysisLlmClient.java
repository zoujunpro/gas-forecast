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
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

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
        HttpResponse<InputStream> response = httpClient.send(request(body), HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            try (InputStream input = response.body()) {
                String error = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                throw new IllegalStateException("大模型接口返回 HTTP " + response.statusCode() + "：" + abbreviate(error, 300));
            }
        }
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:"))
                    continue;
                String data = line.substring(5).trim();
                if (data.isEmpty() || "[DONE]".equals(data))
                    continue;
                JsonNode event = objectMapper.readTree(data);
                String chunk = event.path("choices").path(0).path("delta").path("content").asText("");
                if (!chunk.isEmpty()) {
                    content.append(chunk);
                    consumer.accept(chunk);
                }
            }
        }
        if (content.isEmpty())
            throw new IllegalStateException("大模型接口未返回内容");
        return content.toString();
    }

    private HttpRequest request(ObjectNode body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(chatCompletionsUrl())).timeout(Duration.ofSeconds(Math.max(timeoutSeconds, 10))).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        if (TextUtils.hasText(apiKey()))
            request.header("Authorization", "Bearer " + apiKey());
        return request.build();
    }

    @FunctionalInterface
    public interface StreamConsumer {
        void accept(String content) throws Exception;
    }

    private void validateConfig() {
        if (!TextUtils.hasText(baseUrl()))
            throw new IllegalStateException("未配置大模型 Base URL");
        if (!TextUtils.hasText(model()))
            throw new IllegalStateException("未配置大模型名称");
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
        String value = baseUrl().replaceAll("/+$", "");
        return value.endsWith("/chat/completions") ? value : value + "/chat/completions";
    }

    private String abbreviate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength)
            return text;
        return text.substring(0, maxLength);
    }
}
