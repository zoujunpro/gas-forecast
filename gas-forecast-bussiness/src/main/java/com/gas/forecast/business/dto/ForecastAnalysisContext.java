package com.gas.forecast.business.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;

/** 发送给大模型的完整、可追溯预测分析上下文。 */
public record ForecastAnalysisContext(ForecastInfo forecast, TrainingInfo training, DatasetSection historicalFeatureData,
        DatasetSection trainingData, DatasetSection futureFeatureData, List<BacktestPoint> historicalBacktest,
        List<ForecastPoint> forecastResults) {

    public record ForecastInfo(String forecastBatchNo, String agentCode, String scopeType, String regionCode, String regionName,
            String industryCode, String industryName, String customerCode, String customerName, String forecastStartDate,
            Integer forecastHorizon, String forecastFrequency) {
    }

    public record TrainingInfo(String trainBatchNo, String modelCode, String selectedModelName, String modelVersion,
            String trainStartDate, String trainEndDate, JsonNode metrics, JsonNode selectedModelParams, String selectionReason) {
    }

    /** total 是原始总数，rows 是受 Prompt 长度限制后的样本。 */
    public record DatasetSection(String source, int total, boolean truncated, JsonNode rows) {
    }

    public record BacktestPoint(String date, BigDecimal actualValue, BigDecimal predictedValue) {
    }

    public record ForecastPoint(String date, BigDecimal forecastValue) {
    }
}
