package com.gas.forecast.business.dto.response;

import java.util.Date;

/** 页面展示的预测分析结果。 */
public record ModelForecastAnalysisResponse(Long id, String forecastBatchNo, String analysisContent, String llmModel, String analysisStatus, String errorMessage,
        Date createTime, Date updateTime) {
}
