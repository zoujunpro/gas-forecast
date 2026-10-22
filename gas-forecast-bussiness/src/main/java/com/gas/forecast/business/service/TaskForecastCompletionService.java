package com.gas.forecast.business.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.ExecutionTaskTb;
import com.gas.forecast.dao.mapper.ExecutionTaskTbMapper;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 将预测业务批次的最终结果回写到对应的统一预测任务和执行批次。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskForecastCompletionService {
    private final ExecutionTaskTbMapper taskMapper;
    private final ExecutionBatchProgressService batchProgressService;

    public void markSuccess(String forecastBatchNo) {
        finish(forecastBatchNo, "SUCCESS", null);
    }

    public void markFailed(String forecastBatchNo, String message) {
        finish(forecastBatchNo, "FAILED", message);
    }

    private void finish(String forecastBatchNo, String status, String message) {
        if (!TextUtils.hasText(forecastBatchNo)) return;
        List<ExecutionTaskTb> tasks = taskMapper.selectList(
                Wrappers.<ExecutionTaskTb>lambdaQuery()
                        .eq(ExecutionTaskTb::getBusinessType, "FORECAST")
                        .eq(ExecutionTaskTb::getBusinessNo, forecastBatchNo));
        if (tasks.isEmpty()) return;

        Date now = new Date();
        boolean success = "SUCCESS".equals(status);
        int updated = taskMapper.update(null,
                Wrappers.<ExecutionTaskTb>lambdaUpdate()
                        .eq(ExecutionTaskTb::getBusinessType, "FORECAST")
                        .eq(ExecutionTaskTb::getBusinessNo, forecastBatchNo)
                        .in(ExecutionTaskTb::getStatus, "PENDING", "WAITING", "RUNNING")
                        .set(ExecutionTaskTb::getStatus, status)
                        .set(ExecutionTaskTb::getCurrentStage, success ? "COMPLETED" : "FORECASTING")
                        .set(ExecutionTaskTb::getCurrentStepCode,
                                success ? "FORECAST_COMPLETED" : "WAIT_FORECAST_RESULT")
                        .set(ExecutionTaskTb::getProgressPercent,
                                success ? BigDecimal.valueOf(100) : BigDecimal.valueOf(75))
                        .set(ExecutionTaskTb::getErrorCode, success ? null : "FORECAST_FAILED")
                        .set(ExecutionTaskTb::getErrorMessage, success ? null : message)
                        .set(ExecutionTaskTb::getFinishedAt, now)
                        .set(ExecutionTaskTb::getUpdatedAt, now));

        tasks.stream().map(ExecutionTaskTb::getBatchNo).distinct()
                .forEach(batchProgressService::refresh);
        if (updated > 0) {
            log.info("预测批次已回写统一任务 forecastBatchNo={}, status={}, taskCount={}",
                    forecastBatchNo, status, updated);
        }
    }
}
