package com.gas.forecast.business.component.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/** 模型平台预测请求。 */
@Data
public class ModelPredictApiRequest {
    @JsonProperty("model_code")
    private String modelCode;
    @JsonProperty("train_batch_no")
    private String trainBatchNo;
    @JsonProperty("forecast_batch_no")
    private String forecastBatchNo;
    @JsonProperty("forecast_horizon")
    private Integer forecastHorizon;
    @JsonProperty("forecast_unit")
    private String forecastUnit;
    @JsonProperty("region_code")
    private String regionCode;
    private JsonNode params;
    private JsonNode dataset;
}
