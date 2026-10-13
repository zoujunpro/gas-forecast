package com.gas.forecast.business.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.forecast.business.component.dto.ModelInfoApiResponse;
import com.gas.forecast.business.component.dto.ModelPredictApiRequest;
import com.gas.forecast.business.component.dto.ModelPredictApiResponse;
import com.gas.forecast.business.component.dto.ModelTrainApiRequest;
import com.gas.forecast.business.component.dto.ModelTrainApiResponse;
import com.gas.forecast.business.component.dto.ModelTrainingValidationApiResponse;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.util.HttpUtil;
import com.gas.forecast.common.util.TextUtils;
import java.util.ArrayList;
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
    @Value("${gas.agent.validate-training-url:http://127.0.0.1:8090/api/v1/models/validate-training-data}")
    private String validateTrainingUrl;
    @Value("${gas.agent.train-url:http://127.0.0.1:8090/api/v1/train}")
    private String trainUrl;
    @Value("${gas.agent.predict-url:http://127.0.0.1:8090/api/v1/predict}")
    private String predictUrl;
    @Value("${gas.agent.training-capacity-url:http://127.0.0.1:8090/api/v1/training/capacity}")
    private String trainingCapacityUrl;
    @Value("${gas.agent.training-task-url:http://127.0.0.1:8090/api/v1/training/tasks}")
    private String trainingTaskUrl;
    @Value("${gas.agent.connect-timeout-ms:5000}")
    private int connectTimeoutMs;
    @Value("${gas.agent.validation-timeout-ms:30000}")
    private int validationTimeoutMs;
    @Value("${gas.agent.training-timeout-ms:1800000}")
    private int trainingTimeoutMs;

    public List<ModelInfoApiResponse> listModels() {
        JsonNode data = successfulData(getJson(modelsUrl, "模型列表查询失败"), "模型列表查询失败");
        if (!data.isArray()) {
            throw new BusinessException("模型平台返回的模型列表格式不正确");
        }
        return Arrays.asList(objectMapper.convertValue(data, ModelInfoApiResponse[].class));
    }

    public ModelTrainingValidationApiResponse validateTrainingData(ModelTrainApiRequest request) {
        JsonNode data = successfulData(postJson(validateTrainingUrl, objectMapper.valueToTree(request), "训练数据校验失败", validationTimeoutMs), "训练数据校验失败");
        ModelTrainingValidationApiResponse response = objectMapper.convertValue(data, ModelTrainingValidationApiResponse.class);
        if (!Boolean.TRUE.equals(response.getValid())) {
            List<String> messages = new ArrayList<>();
            if (response.getErrors() != null) {
                response.getErrors().stream().map(ModelTrainingValidationApiResponse.ValidationIssueApiResponse::getMessage).filter(TextUtils::hasText).forEach(messages::add);
            }
            throw new BusinessException(messages.isEmpty() ? "训练数据不满足当前模型要求" : "训练数据校验未通过：" + String.join("；", messages));
        }
        return response;
    }

    public boolean isTrainingEnabled() {
        return TextUtils.hasText(trainUrl);
    }

    /** 模型平台当前还能接收多少个训练；查询失败时抛错，由派发器本轮停止领取。 */
    public int availableTrainingSlots() {
        JsonNode data = successfulData(getJson(trainingCapacityUrl, "模型平台训练容量查询失败"), "模型平台训练容量查询失败");
        return Math.max(0, data.path("available_slots").asInt(0));
    }

    /** 查询批次是否仍登记在当前模型平台进程中。 */
    public boolean isTrainingRunning(String batchNo) {
        JsonNode data = successfulData(getJson(trainingTaskUrl + "/" + batchNo, "模型平台训练任务查询失败"), "模型平台训练任务查询失败");
        return data.path("running").asBoolean(false);
    }

    public ModelTrainApiResponse train(ModelTrainApiRequest request) {
        if (!isTrainingEnabled()) {
            return null;
        }
        JsonNode response = postJson(trainUrl, objectMapper.valueToTree(request), "模型训练失败", trainingTimeoutMs);
        ensureSuccess(response, "模型训练失败");
        return objectMapper.convertValue(response, ModelTrainApiResponse.class);
    }

    public ModelPredictApiResponse predict(ModelPredictApiRequest request) {
        JsonNode data = successfulData(postJson(predictUrl, objectMapper.valueToTree(request), "模型预测失败"), "模型预测失败");
        return objectMapper.convertValue(data, ModelPredictApiResponse.class);
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
