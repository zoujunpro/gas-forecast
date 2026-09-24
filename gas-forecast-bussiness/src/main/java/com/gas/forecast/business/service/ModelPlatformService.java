package com.gas.forecast.business.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.forecast.business.dto.response.ModelTrainingValidationResponse;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.util.HttpUtil;
import com.gas.forecast.common.util.TextUtils;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 模型平台元数据查询与训练数据预校验。 */
@Service
public class ModelPlatformService {

    private final ObjectMapper objectMapper;

    public ModelPlatformService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Value("${gas.agent.models-url:http://127.0.0.1:8090/api/v1/models}")
    private String modelsUrl;

    @Value("${gas.agent.validate-training-url:http://127.0.0.1:8090/api/v1/models/validate-training-data}")
    private String validateTrainingUrl;

    public JsonNode listModels() {
        JsonNode response = HttpUtil.getJson(modelsUrl);
        ensureSuccess(response, "模型列表查询失败");
        JsonNode data = response.path("data");
        if (!data.isArray()) {
            throw new BusinessException("模型平台返回的模型列表格式不正确");
        }
        return data;
    }

    public ModelTrainingValidationResponse validateTrainingData(JsonNode request) {
        JsonNode response = HttpUtil.postJson(validateTrainingUrl, request);
        ensureSuccess(response, "训练数据校验失败");
        JsonNode data = response.path("data");
        if (!data.isObject() || !data.has("valid")) {
            throw new BusinessException("模型平台返回的训练数据校验结果格式不正确");
        }
        if (!data.path("valid").asBoolean(false)) {
            List<String> messages = new ArrayList<>();
            JsonNode errors = data.path("errors");
            if (errors.isArray()) {
                errors.forEach(error -> {
                    String message = error.path("message").asText("").trim();
                    if (TextUtils.hasText(message))
                        messages.add(message);
                });
            }
            throw new BusinessException(messages.isEmpty() ? "训练数据不满足当前模型要求" : "训练数据校验未通过：" + String.join("；", messages));
        }
        return objectMapper.convertValue(data, ModelTrainingValidationResponse.class);
    }

    private void ensureSuccess(JsonNode response, String fallbackMessage) {
        if (response == null || response.isNull()) {
            throw new BusinessException(fallbackMessage + "：模型平台未返回数据");
        }
        if (response.path("code").asInt(-1) != 0) {
            String message = response.path("message").asText(fallbackMessage);
            throw new BusinessException(message);
        }
    }
}
