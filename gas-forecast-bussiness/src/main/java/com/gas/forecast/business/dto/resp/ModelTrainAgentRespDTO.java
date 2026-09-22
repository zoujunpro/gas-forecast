package com.gas.forecast.business.dto.resp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 模型平台统一响应外壳。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelTrainAgentRespDTO(
        Integer code,
        String message,
        ModelTrainAgentTrainResultDTO data
) {
}
