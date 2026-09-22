package com.gas.forecast.business.dto.req;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * 模型平台统一训练入参。不同模型的差异放在 dataset 行和 params 中。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ModelTrainAgentTrainReqDTO(
        @JsonProperty("agent_code")
        String agentCode,

        @JsonProperty("model_code")
        String modelCode,

        @JsonProperty("train_batch_no")
        String trainBatchNo,

        @JsonProperty("region_code")
        String regionCode,

        @JsonProperty("region_name")
        String regionName,

        @JsonProperty("industry_code")
        String industryCode,

        @JsonProperty("industry_name")
        String industryName,

        @JsonProperty("customer_code")
        String customerCode,

        @JsonProperty("customer_name")
        String customerName,

        JsonNode params,

        List<JsonNode> dataset
) {
}
