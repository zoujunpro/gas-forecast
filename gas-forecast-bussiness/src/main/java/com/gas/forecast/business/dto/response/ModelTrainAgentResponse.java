package com.gas.forecast.business.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型平台统一响应外壳。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelTrainAgentResponse {
    private Integer code;

    private String message;

    private ModelTrainAgentTrainResultDTO data;

    public Integer code() {
        return code;
    }

    public String message() {
        return message;
    }

    public ModelTrainAgentTrainResultDTO data() {
        return data;
    }
}
