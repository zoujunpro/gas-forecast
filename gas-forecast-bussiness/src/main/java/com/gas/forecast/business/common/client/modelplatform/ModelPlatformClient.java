package com.gas.forecast.business.common.client.modelplatform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.forecast.business.common.client.modelplatform.dto.ModelInfoApiResponse;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.util.HttpUtil;
import com.gas.forecast.common.util.TextUtils;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

/** 模型平台 HTTP 客户端，统一负责协议对象转换、响应校验和错误解析。 */
@Component
public class ModelPlatformClient {
    private final ObjectMapper objectMapper;

    public ModelPlatformClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Value("${gas.agent.models-url:http://127.0.0.1:8090/api/v1/models}")
    private String modelsUrl;
    @Value("${gas.agent.execute-url:http://127.0.0.1:8090/api/v1/execute}")
    private String executeUrl;
    @Value("${gas.agent.connect-timeout-ms:5000}")
    private int connectTimeoutMs;
    @Value("${gas.agent.training-timeout-ms:1800000}")
    private int trainingTimeoutMs;

    public List<ModelInfoApiResponse> listModels() {
        JsonNode data = successfulData(getJson(modelsUrl, "模型列表查询失败"), "模型列表查询失败");
        if (!data.isArray()) {
            throw new BusinessException("模型平台返回的模型列表格式不正确");
        }
        return Arrays.asList(objectMapper.convertValue(data, ModelInfoApiResponse[].class));
    }

    /**
     * 一次完成训练、回测与未来预测的统一接口。
     * 调用方传入 forecast_no、history_dataset 和 future_dataset，返回同一 forecast_no 下的完整结果。
     */
    public JsonNode executeForecast(JsonNode request) {
        return successfulData(postJson(executeUrl, request, "统一模型预测执行失败", trainingTimeoutMs), "统一模型预测执行失败");
    }

    /**
     * 调用模型平台时将下游 HTTP 错误转换为业务异常，确保平台返回的具体原因能够由 GlobalExceptionHandler
     * 原样返回给前端，而不是降级成笼统的“系统异常”。
     */
    private JsonNode postJson(String url, JsonNode request, String fallbackMessage) {
        return postJson(url, request, fallbackMessage, 300000);
    }

    private JsonNode postJson(String url, JsonNode request, String fallbackMessage, int readTimeoutMs) {
        try {
            return HttpUtil.postJson(url, request, connectTimeoutMs, readTimeoutMs);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(extractErrorMessage(exception, fallbackMessage));
        }
    }

    private JsonNode getJson(String url, String fallbackMessage) {
        try {
            return HttpUtil.getJson(url);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(extractErrorMessage(exception, fallbackMessage));
        }
    }

    public String extractErrorMessage(RuntimeException exception, String fallbackMessage) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof RestClientResponseException responseException) {
                try {
                    String message = objectMapper.readTree(responseException.getResponseBodyAsString()).path("message").asText("").trim();
                    if (TextUtils.hasText(message)) {
                        return message;
                    }
                } catch (Exception ignored) {
                    // 非 JSON 错误响应使用异常消息。
                }
            }
            current = current.getCause();
        }
        return TextUtils.hasText(exception.getMessage()) ? exception.getMessage() : fallbackMessage;
    }

    private JsonNode successfulData(JsonNode response, String fallbackMessage) {
        ensureSuccess(response, fallbackMessage);
        return response.path("data");
    }

    private void ensureSuccess(JsonNode response, String fallbackMessage) {
        if (response == null || response.isNull()) {
            throw new BusinessException(fallbackMessage + "：模型平台未返回数据");
        }
        if (response.path("code").asInt(-1) != 0) {
            throw new BusinessException(response.path("message").asText(fallbackMessage));
        }
    }
}
