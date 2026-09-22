package com.gas.forecast.business.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型平台统一训练结果。复杂扩展内容保留 JsonNode，避免各模型结果差异破坏兼容性。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelTrainAgentTrainResultDTO {
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

    @JsonProperty("requested_candidate_count")
    private Integer requestedCandidateCount;

    @JsonProperty("successful_candidate_count")
    private Integer successfulCandidateCount;

    @JsonProperty("ranked_candidate_count")
    private Integer rankedCandidateCount;

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

    public String agentCode() {
        return agentCode;
    }

    public String modelCode() {
        return modelCode;
    }

    public String trainBatchNo() {
        return trainBatchNo;
    }

    public JsonNode metrics() {
        return metrics;
    }

    public List<String> featureNames() {
        return featureNames;
    }

    public String selectedModelName() {
        return selectedModelName;
    }

    public String selectionReason() {
        return selectionReason;
    }

    public Integer requestedCandidateCount() {
        return requestedCandidateCount;
    }

    public Integer successfulCandidateCount() {
        return successfulCandidateCount;
    }

    public Integer rankedCandidateCount() {
        return rankedCandidateCount;
    }

    public JsonNode selectedModelParams() {
        return selectedModelParams;
    }

    public JsonNode candidateEvaluations() {
        return candidateEvaluations;
    }

    public JsonNode rollingBacktestResults() {
        return rollingBacktestResults;
    }

    public JsonNode rollingBacktestFoldMetrics() {
        return rollingBacktestFoldMetrics;
    }

    public JsonNode issues() {
        return issues;
    }

    public JsonNode metadata() {
        return metadata;
    }

    public String createdAt() {
        return createdAt;
    }
}
