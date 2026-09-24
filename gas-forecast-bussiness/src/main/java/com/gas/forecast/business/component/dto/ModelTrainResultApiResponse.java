package com.gas.forecast.business.component.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import lombok.Data;

/** 模型平台训练结果。 */
@Data
public class ModelTrainResultApiResponse {
    @JsonProperty("agent_code")
    private String agentCode;
    @JsonProperty("model_code")
    private String modelCode;
    @JsonProperty("train_batch_no")
    private String trainBatchNo;
    private JsonNode metrics;
    @JsonProperty("feature_names")
    private List<String> featureNames;
    @JsonProperty("selected_model_name")
    private String selectedModelName;
    @JsonProperty("selection_reason")
    private String selectionReason;
    @JsonProperty("selected_model_params")
    private JsonNode selectedModelParams;
    @JsonProperty("candidate_evaluations")
    private JsonNode candidateEvaluations;
    @JsonProperty("rolling_backtest_results")
    private JsonNode rollingBacktestResults;
    @JsonProperty("rolling_backtest_fold_metrics")
    private JsonNode rollingBacktestFoldMetrics;
    private JsonNode issues;
    private JsonNode metadata;
    @JsonProperty("created_at")
    private String createdAt;
}
