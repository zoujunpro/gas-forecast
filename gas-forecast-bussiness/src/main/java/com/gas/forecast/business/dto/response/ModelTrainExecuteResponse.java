package com.gas.forecast.business.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型训练执行结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelTrainExecuteResponse {
    private String trainCode;

    private String trainBatchNo;

    private String modelCode;

    private Integer datasetSize;

    private String status;

    private JsonNode requestPayload;

    private JsonNode agentResponse;

    public String trainCode() {
        return trainCode;
    }

    public String trainBatchNo() {
        return trainBatchNo;
    }

    public String modelCode() {
        return modelCode;
    }

    public Integer datasetSize() {
        return datasetSize;
    }

    public String status() {
        return status;
    }

    public JsonNode requestPayload() {
        return requestPayload;
    }

    public JsonNode agentResponse() {
        return agentResponse;
    }
}
