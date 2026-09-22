package com.gas.forecast.business.dto.resp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * 模型平台统一训练结果。复杂扩展内容保留 JsonNode，避免各模型结果差异破坏兼容性。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelTrainAgentTrainResultDTO(
        @JsonProperty("agent_code")
        String agentCode,

        @JsonProperty("model_code")
        String modelCode,

        @JsonProperty("train_batch_no")
        String trainBatchNo,

        JsonNode metrics,

        @JsonProperty("feature_names")
        List<String> featureNames,

        @JsonProperty("selected_model_name")
        String selectedModelName,

        @JsonProperty("selection_reason")
        String selectionReason,

        @JsonProperty("requested_candidate_count")
        Integer requestedCandidateCount,

        @JsonProperty("successful_candidate_count")
        Integer successfulCandidateCount,

        @JsonProperty("ranked_candidate_count")
        Integer rankedCandidateCount,

        @JsonProperty("selected_model_params")
        JsonNode selectedModelParams,

        @JsonProperty("candidate_evaluations")
        JsonNode candidateEvaluations,

        @JsonProperty("rolling_backtest_results")
        JsonNode rollingBacktestResults,

        @JsonProperty("rolling_backtest_fold_metrics")
        JsonNode rollingBacktestFoldMetrics,

        JsonNode issues,

        JsonNode metadata,

        @JsonProperty("created_at")
        String createdAt
) {
}
