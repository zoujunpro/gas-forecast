package com.gas.forecast.business.ai.dto;

import com.gas.forecast.business.ai.dto.AiChatResponse.ExecutionProgress;
import com.gas.forecast.business.ai.dto.AiChatResponse.ForecastResult;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Date;
import java.util.List;

public record AiConversationResponse(String conversationNo, String title, String status, String scene, String businessType, String businessNo,
        String lastTrainBatchNo, String lastForecastBatchNo, Date updatedAt, List<Message> messages) {
    public record Message(Long id, String role, String content, Date createdAt, ExecutionProgress progress, ForecastResult forecastResult, String analysis,
            List<String> suggestedQuestions, JsonNode presentation, JsonNode executionResult) {
    }
}
