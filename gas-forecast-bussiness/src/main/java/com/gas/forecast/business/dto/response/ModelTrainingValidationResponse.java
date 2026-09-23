package com.gas.forecast.business.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import lombok.Data;

/** 模型平台训练数据校验结果。 */
@Data
public class ModelTrainingValidationResponse {
    /**
     * 智能体编码。
     */
    @JsonProperty("agent_code")
    private String agentCode;

    /**
     * 模型编码。
     */
    @JsonProperty("model_code")
    private String modelCode;

    /**
     * 校验是否通过。
     */
    private Boolean valid;
    /**
     * 摘要信息。
     */
    private Map<String, Object> summary;
    /**
     * 错误列表。
     */
    private List<ValidationIssue> errors;
    /**
     * 警告列表。
     */
    private List<ValidationIssue> warnings;

    @Data
    public static class ValidationIssue {
        /**
         * 编码。
         */
        private String code;
        /**
         * 应答消息。
         */
        private String message;
        /**
         * 期望值。
         */
        private Object expected;
        /**
         * 实际值。
         */
        private Object actual;
    }
}
