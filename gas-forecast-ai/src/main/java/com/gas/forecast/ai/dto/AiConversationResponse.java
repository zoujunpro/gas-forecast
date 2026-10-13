package com.gas.forecast.ai.dto;

import com.gas.forecast.ai.dto.AiChatResponse.ExecutionProgress;
import com.gas.forecast.ai.dto.AiChatResponse.ForecastResult;
import java.util.Date;
import java.util.List;

public record AiConversationResponse(String conversationNo, String title, String status, String lastTrainBatchNo, String lastForecastBatchNo, Date updatedAt, List<Message> messages) {
    public record Message(Long id, String role, String content, Date createdAt, ExecutionProgress progress, ForecastResult forecastResult, String analysis, List<String> suggestedQuestions) {
    }
}
