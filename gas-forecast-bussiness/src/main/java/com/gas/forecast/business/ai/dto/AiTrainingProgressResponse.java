package com.gas.forecast.business.ai.dto;

public record AiTrainingProgressResponse(String batchNo, String status, String message, String taskNo, boolean continuationExpected, String forecastBatchNo) {
}
