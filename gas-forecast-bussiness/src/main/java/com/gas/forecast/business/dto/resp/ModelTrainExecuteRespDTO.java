package com.gas.forecast.business.dto.resp;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 模型训练执行结果。
 */
public record ModelTrainExecuteRespDTO(
        String configCode,
        String trainBatchNo,
        String modelCode,
        Integer datasetSize,
        String status,
        JsonNode requestPayload,
        JsonNode agentResponse
) {
}
