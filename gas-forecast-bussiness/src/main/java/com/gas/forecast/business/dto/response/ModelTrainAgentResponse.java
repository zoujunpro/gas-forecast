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
    /**
     * 编码。
     */
    private Integer code;

    /**
     * 应答消息。
     */
    private String message;

    /**
     * 应答数据。
     */
    private ModelTrainAgentTrainResultDTO data;
}
