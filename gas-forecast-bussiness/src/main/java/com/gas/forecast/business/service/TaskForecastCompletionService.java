package com.gas.forecast.business.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.AgentExecutionTaskTb;
import com.gas.forecast.dao.mapper.AgentExecutionBatchTbMapper;
import com.gas.forecast.dao.mapper.AgentExecutionTaskTbMapper;
import java.math.BigDecimal;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 将预测批次的最终结果主动回写到用户任务，避免任务必须等到查询时才刷新。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskForecastCompletionService {
    private final AgentExecutionTaskTbMapper taskMapper;
    private final AgentExecutionBatchTbMapper batchMapper;

    public void markSuccess(String forecastBatchNo) {
        finish(forecastBatchNo, "SUCCESS", null);
    }

    public void markFailed(String forecastBatchNo, String message) {
        finish(forecastBatchNo, "FAILED", message);
    }

    private void finish(String forecastBatchNo, String status, String message) {
        if (!TextUtils.hasText(forecastBatchNo)) return;
        Date now = new Date();
        boolean success = "SUCCESS".equals(status);
        int updated = taskMapper.update(null, Wrappers.<AgentExecutionTaskTb>lambdaUpdate()
                .eq(AgentExecutionTaskTb::getTaskType, "FORECAST")
                .eq(AgentExecutionTaskTb::getLatestForecastBatchNo, forecastBatchNo)
                .in(AgentExecutionTaskTb::getStatus, "EXECUTING", "WAITING_TRAINING")
                .set(AgentExecutionTaskTb::getStatus, status)
                .set(AgentExecutionTaskTb::getCurrentStage, success ? "COMPLETED" : "FORECASTING")
                .set(AgentExecutionTaskTb::getCurrentStepCode, success ? "RESULT_EXPLANATION" : "WAIT_FORECAST_RESULT")
                .set(AgentExecutionTaskTb::getProgressPercent, success ? BigDecimal.valueOf(100) : BigDecimal.valueOf(75))
                .set(AgentExecutionTaskTb::getErrorCode, success ? null : "FORECAST_FAILED")
                .set(AgentExecutionTaskTb::getErrorMessage, success ? null : message)
                .set(AgentExecutionTaskTb::getRetryable, success ? 0 : 1)
                .set(AgentExecutionTaskTb::getFinishedAt, now)
                .set(AgentExecutionTaskTb::getUpdatedAt, now));
        batchMapper.update(null, Wrappers.<com.gas.forecast.dao.domain.AgentExecutionBatchTb>lambdaUpdate()
                .eq(com.gas.forecast.dao.domain.AgentExecutionBatchTb::getBatchType, "FORECAST")
                .eq(com.gas.forecast.dao.domain.AgentExecutionBatchTb::getBatchNo, forecastBatchNo)
                .set(com.gas.forecast.dao.domain.AgentExecutionBatchTb::getStatus, status)
                .set(com.gas.forecast.dao.domain.AgentExecutionBatchTb::getErrorMessage, success ? null : message)
                .set(com.gas.forecast.dao.domain.AgentExecutionBatchTb::getFinishedAt, now)
                .set(com.gas.forecast.dao.domain.AgentExecutionBatchTb::getUpdatedAt, now));
        if (updated > 0) log.info("预测批次已回写用户任务 forecastBatchNo={}, status={}, taskCount={}", forecastBatchNo, status, updated);
    }
}
