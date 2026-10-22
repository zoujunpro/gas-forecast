package com.gas.forecast.business.service;

/** 智能体统一任务的状态和步骤查询服务。 */
public interface AgentExecutionTaskService {
    Object status(String taskNo, boolean includeRecovery);

    Object steps(String taskNo, boolean includeBusinessSteps, boolean includeAttempts);

}
