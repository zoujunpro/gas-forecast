package com.gas.forecast.business.service;

import com.gas.forecast.common.util.TextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 将预测业务批次的最终结果回写到任务目标关联记录。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskForecastCompletionService {
    private final ExecutionTaskLifecycleService taskLifecycleService;

    public void markSuccess(String forecastBatchNo) {
        finish(forecastBatchNo, true, null);
    }

    public void markFailed(String forecastBatchNo, String message) {
        finish(forecastBatchNo, false, message);
    }

    private void finish(String forecastBatchNo, boolean success, String message) {
        if (!TextUtils.hasText(forecastBatchNo)) return;
        taskLifecycleService.markForecastCompleted(forecastBatchNo, success);
        log.info("预测批次已回写任务目标 forecastBatchNo={}, status={}, message={}",
                forecastBatchNo, success ? "SUCCESS" : "FAILED", message);
    }
}
