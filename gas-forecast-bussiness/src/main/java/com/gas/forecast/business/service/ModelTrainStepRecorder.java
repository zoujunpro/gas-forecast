package com.gas.forecast.business.service;

public interface ModelTrainStepRecorder {
    default Long start(String batchNo, int attemptNo, String stepCode, String stepName, int stepOrder, String inputSummary) {
        return start(batchNo, attemptNo, null, 1, stepCode, stepName, stepOrder, inputSummary);
    }
    Long start(String batchNo, int attemptNo, Long parentStepId, int stepLevel, String stepCode, String stepName, int stepOrder, String inputSummary);
    void success(Long stepId, String outputSummary, Integer producedCount);
    void fail(Long stepId, Throwable error);
}
