package com.gas.forecast.business.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型平台统一训练入参。不同模型的差异放在 dataset 行和 params 中。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelTrainAgentTrainRequest {
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

    /** 训练数据总条数；详情预览被截断时用于展示真实数量。 */
    @JsonProperty("dataset_total")
    private Integer datasetTotal;

    /** 训练数据是否仅返回部分预览。 */
    @JsonProperty("dataset_truncated")
    private Boolean datasetTruncated;

    /** 训练数据预览最大条数。 */
    @JsonProperty("dataset_preview_limit")
    private Integer datasetPreviewLimit;

    public String agentCode() {
        return agentCode;
    }

    public String modelCode() {
        return modelCode;
    }

    public String trainBatchNo() {
        return trainBatchNo;
    }

    public String regionCode() {
        return regionCode;
    }

    public String regionName() {
        return regionName;
    }

    public String industryCode() {
        return industryCode;
    }

    public String industryName() {
        return industryName;
    }

    public String customerCode() {
        return customerCode;
    }

    public String customerName() {
        return customerName;
    }

    public JsonNode params() {
        return params;
    }

    public List<JsonNode> dataset() {
        return dataset;
    }
}
