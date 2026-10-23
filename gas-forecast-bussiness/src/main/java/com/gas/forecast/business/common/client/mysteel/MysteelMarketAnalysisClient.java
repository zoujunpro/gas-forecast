package com.gas.forecast.business.common.client.mysteel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.util.TextUtils;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 钢联市场分析接口客户端，替代 Skill 中的 Node 调用脚本。 */
@Component
public class MysteelMarketAnalysisClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${gas.mysteel.analysis-url:https://mcp.mysteel.com/mcp/info/chat-robot/rag/answer}")
    private String analysisUrl;

    @Value("${gas.mysteel.api-key:}")
    private String apiKey;

    @Value("${gas.mysteel.timeout-seconds:90}")
    private long timeoutSeconds;

    @Autowired
    public MysteelMarketAnalysisClient(ObjectMapper objectMapper) {
        this(objectMapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    MysteelMarketAnalysisClient(ObjectMapper objectMapper, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    /** 获取 Markdown 格式市场分析报告；鉴权错误不重试，其他错误最多重试一次。 */
    public String analyze(String query) {
        if (!TextUtils.hasText(query)) {
            throw new BusinessException("钢联市场分析问题不能为空");
        }
        if (!TextUtils.hasText(apiKey)) {
            throw new BusinessException("未配置钢联API密钥 MYSTEEL_API_KEY");
        }
        RuntimeException firstFailure;
        try {
            return request(query.trim());
        } catch (MysteelAuthenticationException exception) {
            throw new BusinessException(exception.getMessage());
        } catch (RuntimeException exception) {
            firstFailure = exception;
        }
        try {
            return request(query.trim());
        } catch (MysteelAuthenticationException exception) {
            throw new BusinessException(exception.getMessage());
        } catch (RuntimeException exception) {
            exception.addSuppressed(firstFailure);
            throw new BusinessException("钢联市场分析请求失败：" + exception.getMessage());
        }
    }

    private String request(String query) {
        try {
            ObjectNode payload = objectMapper.createObjectNode().put("query", query);
            HttpRequest request = HttpRequest.newBuilder(URI.create(analysisUrl)).timeout(Duration.ofSeconds(Math.max(timeoutSeconds, 10))).header("Content-Type", "application/json")
                    .header("token", apiKey.trim()).POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload))).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("接口返回HTTP " + response.statusCode() + "：" + abbreviate(response.body(), 200));
            }
            JsonNode result = objectMapper.readTree(response.body());
            if (!result.path("success").asBoolean(false)) {
                String code = result.path("code").asText("N/A");
                String message = result.path("message").asText("未知错误");
                if ("400".equals(code)) {
                    throw new MysteelAuthenticationException("钢联API密钥校验失败：" + message);
                }
                throw new IllegalStateException("钢联接口错误[" + code + "]：" + message);
            }
            String report = result.path("data").asText("").trim();
            if (!TextUtils.hasText(report)) {
                throw new IllegalStateException("钢联接口未返回分析报告");
            }
            return report;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("钢联市场分析请求已中断", exception);
        } catch (MysteelAuthenticationException exception) {
            throw exception;
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException stateException) {
                throw stateException;
            }
            throw new IllegalStateException(exception.getMessage(), exception);
        }
    }

    private String abbreviate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private static final class MysteelAuthenticationException extends RuntimeException {
        private MysteelAuthenticationException(String message) {
            super(message);
        }
    }
}
