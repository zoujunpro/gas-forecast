package com.gas.forecast.business.component.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import lombok.Data;

/** 模型平台模型元数据。 */
@Data
public class ModelInfoApiResponse {
    @JsonProperty("agent_code")
    private String agentCode;
    @JsonProperty("model_code")
    private String modelCode;
    @JsonProperty("model_version")
    private String modelVersion;
    @JsonProperty("model_name")
    private String modelName;
    private String description;
    private List<String> capabilities;
    @JsonProperty("training_data_range")
    private JsonNode trainingDataRange;
}
