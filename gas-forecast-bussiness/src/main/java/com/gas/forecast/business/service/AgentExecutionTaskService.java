package com.gas.forecast.business.service;

import java.util.Map;

/** 智能体统一任务的进度、步骤记录和中断恢复服务。 */
public interface AgentExecutionTaskService {
    Object status(String taskNo, boolean includeRecovery);

    Object steps(String taskNo, boolean includeBusinessSteps, boolean includeAttempts);

    Object recordStep(String taskNo, StepCommand command);

    Object resume(String taskNo, String requestedStep, boolean confirmed);

    Object retry(String taskNo, String retryStage, boolean confirmed);

    record StepCommand(String stepCode, String stepName, String status, Integer stepOrder, String inputSummary, String outputSummary, Map<String, Object> checkpoint,
            String errorCode, String errorMessage) {
    }
}
