package com.gas.forecast.business.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

public record ModelTrainExecutionStepResponse(Long id, String trainBatchNo, Integer attemptNo, Long parentStepId, Integer stepLevel, String stepCode, String stepName, Integer stepOrder, String status,
        String inputSummary, String outputSummary, Integer producedCount, String errorMessage, @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8") Date startedAt,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8") Date finishedAt, Long durationMs) {
}
