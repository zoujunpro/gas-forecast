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
    /**
     * 智能体编码。
     */
    @JsonProperty("agent_code")
    private String agentCode;

    /**
     * 模型编码。
     */
    @JsonProperty("model_code")
    private String modelCode;

    /**
     * 训练批次号。
     */
    @JsonProperty("train_batch_no")
    private String trainBatchNo;

    /**
     * 评估指标。
     */
    private JsonNode metrics;

    /**
     * 特征名称列表。
     */
    @JsonProperty("feature_names")
    private List<String> featureNames;

    /**
     * 选中模型名称。
     */
    @JsonProperty("selected_model_name")
    private String selectedModelName;

    /**
     * 模型选择原因。
     */
    @JsonProperty("selection_reason")
    private String selectionReason;

    /**
     * 请求候选模型数量。
     */
    @JsonProperty("requested_candidate_count")
    private Integer requestedCandidateCount;

    /**
     * 成功候选模型数量。
     */
    @JsonProperty("successful_candidate_count")
    private Integer successfulCandidateCount;

    /**
     * 参与排名的候选模型数量。
     */
    @JsonProperty("ranked_candidate_count")
    private Integer rankedCandidateCount;

    /**
     * 选中模型参数。
     */
    @JsonProperty("selected_model_params")
    private JsonNode selectedModelParams;

    /**
     * 候选模型评估结果。
     */
    @JsonProperty("candidate_evaluations")
    private JsonNode candidateEvaluations;

    /**
     * 滚动回测结果。
     */
    @JsonProperty("rolling_backtest_results")
    private JsonNode rollingBacktestResults;

    /**
     * 滚动回测分折指标。
     */
    @JsonProperty("rolling_backtest_fold_metrics")
    private JsonNode rollingBacktestFoldMetrics;

    /**
     * 问题列表。
     */
    private JsonNode issues;

    /**
     * 元数据。
     */
    private JsonNode metadata;

    /**
     * 创建时间。
     */
    @JsonProperty("created_at")
    private String createdAt;
}
