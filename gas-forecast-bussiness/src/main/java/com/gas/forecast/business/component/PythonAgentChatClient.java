package com.gas.forecast.business.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.forecast.business.ai.dto.PythonAgentChatRequest;
import com.gas.forecast.business.ai.dto.PythonAgentChatResponse;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.common.util.ThreadLocalUtil;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Java 会话服务调用 Python FastAPI Agent 的内部 HTTP 客户端。 */
@Component
public class PythonAgentChatClient {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${gas.agent.chat-url:http://127.0.0.1:8091/api/v1/internal/chat}")
    private String chatUrl;

    @Value("${gas.agent.chat-timeout-seconds:120}")
    private long timeoutSeconds;

    public PythonAgentChatClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * 执行一次 Agent 编排请求。
     *
     * <p>当前登录令牌只用于 Python 后续调用 Java MCP 工具时继续执行用户权限校验，
     * 不会写入请求正文或 Agent Run 的 request_json。</p>
     */
    public PythonAgentChatResponse chat(PythonAgentChatRequest requestBody) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(chatUrl))
                    .timeout(Duration.ofSeconds(Math.max(timeoutSeconds, 10)))
                    .header("Content-Type", "application/json")
                    .header("x-traceId", requestBody.runNo())
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(requestBody),
                            StandardCharsets.UTF_8));
            String token = ThreadLocalUtil.getToken();
            if (TextUtils.hasText(token)) {
                builder.header("X-Access-Token", token);
            }
            HttpResponse<String> response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Python Agent 返回 HTTP " + response.statusCode()
                        + "：" + abbreviate(response.body()));
            }
            return objectMapper.readValue(response.body(), PythonAgentChatResponse.class);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Python Agent 请求已中断", exception);
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException stateException) {
                throw stateException;
            }
            throw new IllegalStateException("Python Agent 请求失败：" + exception.getMessage(), exception);
        }
    }

    private String abbreviate(String value) {
        if (!TextUtils.hasText(value)) {
            return "无响应内容";
        }
        String normalized = value.trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500) + "…";
    }
}
