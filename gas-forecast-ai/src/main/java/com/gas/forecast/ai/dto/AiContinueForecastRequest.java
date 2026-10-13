package com.gas.forecast.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiContinueForecastRequest(@NotBlank @Size(max = 64) String conversationNo, @NotBlank @Size(max = 64) String trainBatchNo) {
}
