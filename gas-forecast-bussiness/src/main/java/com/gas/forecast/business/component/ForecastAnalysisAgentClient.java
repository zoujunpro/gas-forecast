package com.gas.forecast.business.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gas.forecast.common.util.HttpUtil;
import com.gas.forecast.common.util.TextUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 调用Python Agent完成预测结果的大模型解析；Java不再直接调用模型接口。 */
@Component
public class ForecastAnalysisAgentClient {
    private final ObjectMapper objectMapper;

    @Value("${gas.agent.forecast-analysis-url:http://127.0.0.1:8091/api/v1/forecast-analysis}")
    private String analysisUrl;
    @Value("${gas.agent.connect-timeout-ms:5000}")
    private int connectTimeoutMs;
    @Value("${gas.analysis.timeout-seconds:120}")
    private int timeoutSeconds;

    public ForecastAnalysisAgentClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AnalysisResult analyze(String forecastBatchNo, String agentCode, String prompt) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("forecastBatchNo", forecastBatchNo);
        request.put("agentCode", agentCode);
        request.put("prompt", prompt);
        JsonNode response = HttpUtil.postJson(analysisUrl, request, connectTimeoutMs, Math.max(timeoutSeconds, 10) * 1000);
        String content = response.path("analysisContent").asText("").trim();
        String model = response.path("llmModel").asText("").trim();
        if (!TextUtils.hasText(content)) throw new IllegalStateException("Python Agent未返回预测分析内容");
        return new AnalysisResult(content, model);
    }

    public record AnalysisResult(String content, String model) {}
}
