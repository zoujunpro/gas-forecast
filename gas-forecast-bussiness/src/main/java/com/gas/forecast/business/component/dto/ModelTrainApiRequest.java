package com.gas.forecast.business.component.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import lombok.Data;

/** 模型平台训练及训练数据校验请求。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModelTrainApiRequest {
    @JsonProperty("agent_code")
    private String agentCode;
    @JsonProperty("model_code")
    private String modelCode;
    @JsonProperty("train_batch_no")
    private String trainBatchNo;
    @JsonProperty("region_code")
    private String regionCode;
    @JsonProperty("region_name")
    private String regionName;
    @JsonProperty("industry_code")
    private String industryCode;
    @JsonProperty("industry_name")
    private String industryName;
    @JsonProperty("customer_code")
    private String customerCode;
    @JsonProperty("customer_name")
    private String customerName;
    private JsonNode params;
    private List<JsonNode> dataset;
    @JsonProperty("dataset_total")
    private Integer datasetTotal;
    @JsonProperty("dataset_truncated")
    private Boolean datasetTruncated;
    @JsonProperty("dataset_preview_limit")
    private Integer datasetPreviewLimit;
}
