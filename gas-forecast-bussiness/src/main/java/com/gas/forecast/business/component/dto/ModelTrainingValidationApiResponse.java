package com.gas.forecast.business.component.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import lombok.Data;

/** 模型平台训练数据校验结果。 */
@Data
public class ModelTrainingValidationApiResponse {
    @JsonProperty("agent_code")
    private String agentCode;
    @JsonProperty("model_code")
    private String modelCode;
    private Boolean valid;
    private Map<String, Object> summary;
    private List<ValidationIssueApiResponse> errors;
    private List<ValidationIssueApiResponse> warnings;

    @Data
    public static class ValidationIssueApiResponse {
        private String code;
        private String message;
        private Object expected;
        private Object actual;
    }
}
