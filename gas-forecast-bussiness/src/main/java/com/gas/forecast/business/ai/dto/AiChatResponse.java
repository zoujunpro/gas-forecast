package com.gas.forecast.business.ai.dto;

import java.math.BigDecimal;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;

public record AiChatResponse(String conversationNo, String status, String answer, String trainBatchNo, String forecastBatchNo,
        ExecutionProgress progress, ForecastResult forecastResult, String analysis, List<String> suggestedQuestions, JsonNode presentation) {
    public record ExecutionProgress(String title, int currentStep, int totalSteps, List<Step> steps) {
    }

    public record Step(int index, String name, String status, String detail) {
    }

    public record ForecastResult(String batchNo, String customerName, BigDecimal average, BigDecimal minimum, BigDecimal maximum, TrainingResult training, List<ForecastPoint> points,
            List<ForecastPoint> historyPoints, List<ForecastPoint> lastYearPoints, List<BacktestPoint> backtestPoints) {
    }

    public record TrainingResult(String batchNo, String trainStartDate, String trainEndDate, String bestModel, BigDecimal mape, BigDecimal wmape, BigDecimal smape, BigDecimal mae, BigDecimal rmse,
            BigDecimal r2) {
    }

    public record ForecastPoint(String date, BigDecimal value) {
    }

    public record BacktestPoint(String date, BigDecimal actualValue, BigDecimal predictedValue) {
    }
}
