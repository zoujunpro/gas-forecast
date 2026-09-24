package com.gas.forecast.business.component.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/** 模型平台预测结果。 */
@Data
public class ModelPredictApiResponse {
    @JsonProperty("agent_code")
    private String agentCode;
    @JsonProperty("model_code")
    private String modelCode;
    @JsonProperty("forecast_batch_no")
    private String forecastBatchNo;
    private JsonNode points;
    private JsonNode metadata;
    @JsonProperty("created_at")
    private String createdAt;
}
