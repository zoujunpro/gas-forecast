package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.enums.ModelForecastStatus;
import com.gas.forecast.business.service.AgentExecutionTaskService;
import com.gas.forecast.business.service.ExecutionBatchProgressService;
import com.gas.forecast.business.service.ModelTrainExecutionService;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.security.context.SecurityContextHolder;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.ExecutionBatchTb;
import com.gas.forecast.dao.domain.ExecutionStepTb;
import com.gas.forecast.dao.domain.ExecutionTaskTb;
import com.gas.forecast.dao.domain.ModelForecastRecordTb;
import com.gas.forecast.dao.domain.ModelTrainRecordTb;
import com.gas.forecast.dao.mapper.ExecutionStepTbMapper;
import com.gas.forecast.dao.mapper.ExecutionTaskTbMapper;
import com.gas.forecast.dao.mapper.ModelForecastRecordTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainRecordTbMapper;
import java.math.BigDecimal;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 基于统一执行批次和统一执行任务提供状态查询。 */
@Service
@RequiredArgsConstructor
public class AgentExecutionTaskServiceImpl implements AgentExecutionTaskService {
    private static final Set<String> ACTIVE_TRAIN_STATUSES =
            Set.of("WAITING", "CLAIMED", "PREPARING", "RUNNING", "PENDING");

    private final ExecutionTaskTbMapper taskMapper;
    private final ExecutionStepTbMapper stepMapper;
    private final ModelTrainRecordTbMapper trainRecordMapper;
    private final ModelForecastRecordTbMapper forecastRecordMapper;
    private final ModelTrainExecutionService trainExecutionService;
    private final ExecutionBatchProgressService batchProgressService;

    /** 查询任务当前状态，并以真实训练或预测记录刷新任务状态。 */
    @Override
    public Object status(String taskNo, boolean includeRecovery) {
        ExecutionTaskTb task = owned(taskNo);
        refreshFromBusinessRecord(task);
        ExecutionBatchTb batch = batchProgressService.refresh(task.getBatchNo());
        List<ExecutionTaskTb> batchTasks = batchProgressService.tasks(task.getBatchNo());

        Map<String, Object> result = taskView(task);
        result.put("batch", batch);
        result.put("summary", summary(batchTasks));
        result.put("tasks", batchTasks.stream().map(this::taskView).toList());
        result.put("currentStepName", stepName(task.getCurrentStepCode()));
        if (includeRecovery) {
            result.put("retryable", false);
            result.put("allowedActions", List.of("QUERY_STATUS"));
            result.put("nextAction", terminal(task.getStatus()) ? "NONE" : "WAIT");
            result.put("reason", "当前版本只查询任务状态，任务恢复和重试尚未启用");
        }
        return result;
    }

    /**
     * 查询新execution_step_tb中的步骤。当前业务尚未写入该表，因此通常返回空列表。
     * 训练服务已有的业务步骤可通过includeBusinessSteps一并读取。
     */
    @Override
    public Object steps(String taskNo, boolean includeBusinessSteps, boolean includeAttempts) {
        ExecutionTaskTb task = owned(taskNo);
        var query = Wrappers.<ExecutionStepTb>lambdaQuery()
                .eq(ExecutionStepTb::getTaskNo, taskNo);
        if (!includeAttempts) {
            query.eq(ExecutionStepTb::getAttemptNo,
                    task.getAttemptNo() == null || task.getAttemptNo() < 1 ? 1 : task.getAttemptNo());
        }
        List<ExecutionStepTb> steps = stepMapper.selectList(
                query.orderByAsc(ExecutionStepTb::getAttemptNo)
                        .orderByAsc(ExecutionStepTb::getStepOrder));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskNo", taskNo);
        result.put("attemptNo", task.getAttemptNo());
        result.put("steps", steps);
        if (includeBusinessSteps && "TRAIN".equals(task.getBusinessType())
                && TextUtils.hasText(task.getBusinessNo())) {
            result.put("trainingSteps", trainExecutionService.getExecutionSteps(task.getBusinessNo()));
        } else {
            result.put("trainingSteps", List.of());
        }
        return result;
    }

    private void refreshFromBusinessRecord(ExecutionTaskTb task) {
        if (!TextUtils.hasText(task.getBusinessNo())) return;
        if ("TRAIN".equals(task.getBusinessType())) {
            refreshTraining(task);
        } else if ("FORECAST".equals(task.getBusinessType())) {
            refreshForecast(task);
        }
    }

    private void refreshTraining(ExecutionTaskTb task) {
        ModelTrainRecordTb record = trainRecordMapper.selectOne(
                Wrappers.<ModelTrainRecordTb>lambdaQuery()
                        .eq(ModelTrainRecordTb::getBatchNo, task.getBusinessNo())
                        .last("limit 1"));
        if (record == null || record.getStatus() == null) return;
        String status = record.getStatus().toUpperCase();
        if (ACTIVE_TRAIN_STATUSES.contains(status)) {
            updateTask(task, "RUNNING", "TRAINING", "WAIT_TRAINING_RESULT", 35, null, false);
        } else if ("SUCCESS".equals(status)) {
            updateTask(task, "SUCCESS", "COMPLETED", "TRAINING_COMPLETED", 100, null, true);
        } else {
            String error = TextUtils.hasText(record.getErrorMessage())
                    ? record.getErrorMessage() : "训练执行失败";
            updateTask(task, "FAILED", "TRAINING", "WAIT_TRAINING_RESULT", 35, error, true);
        }
    }

    private void refreshForecast(ExecutionTaskTb task) {
        ModelForecastRecordTb record = forecastRecordMapper.selectOne(
                Wrappers.<ModelForecastRecordTb>lambdaQuery()
                        .eq(ModelForecastRecordTb::getForecastBatchNo, task.getBusinessNo())
                        .last("limit 1"));
        if (record == null || record.getStatus() == null) return;
        if (record.getStatus() == ModelForecastStatus.RUNNING.getCode()) {
            updateTask(task, "RUNNING", "FORECASTING", "WAIT_FORECAST_RESULT", 75, null, false);
        } else if (record.getStatus() == ModelForecastStatus.SUCCESS.getCode()) {
            updateTask(task, "SUCCESS", "COMPLETED", "FORECAST_COMPLETED", 100, null, true);
        } else {
            updateTask(task, "FAILED", "FORECASTING", "WAIT_FORECAST_RESULT", 75,
                    "预测执行失败", true);
        }
    }

    private void updateTask(ExecutionTaskTb task, String status, String stage,
            String stepCode, int progress, String error, boolean finished) {
        boolean changed = !status.equals(task.getStatus())
                || !stage.equals(task.getCurrentStage())
                || !stepCode.equals(task.getCurrentStepCode())
                || task.getProgressPercent() == null
                || task.getProgressPercent().compareTo(BigDecimal.valueOf(progress)) != 0
                || (error != null && !error.equals(task.getErrorMessage()));
        if (!changed) return;
        Date now = new Date();
        task.setStatus(status);
        task.setCurrentStage(stage);
        task.setCurrentStepCode(stepCode);
        task.setProgressPercent(BigDecimal.valueOf(progress));
        task.setErrorCode(error == null ? null : "BUSINESS_EXECUTION_FAILED");
        task.setErrorMessage(error);
        if (finished) task.setFinishedAt(now);
        task.setUpdatedAt(now);
        taskMapper.updateById(task);
    }

    private ExecutionTaskTb owned(String taskNo) {
        if (!TextUtils.hasText(taskNo)) throw new BusinessException("taskNo不能为空");
        ExecutionTaskTb task = taskMapper.selectOne(
                Wrappers.<ExecutionTaskTb>lambdaQuery()
                        .eq(ExecutionTaskTb::getTaskNo, taskNo)
                        .eq(ExecutionTaskTb::getCreatedBy,
                                String.valueOf(SecurityContextHolder.getUserId()))
                        .last("limit 1"));
        if (task == null) throw new BusinessException("任务不存在或无权访问");
        return task;
    }

    private Map<String, Object> taskView(ExecutionTaskTb task) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskNo", task.getTaskNo());
        result.put("executionBatchNo", task.getBatchNo());
        result.put("businessType", task.getBusinessType());
        result.put("businessNo", task.getBusinessNo());
        result.put("agentCode", task.getAgentCode());
        result.put("regionCode", task.getRegionCode());
        result.put("regionName", task.getRegionName());
        result.put("industryCode", task.getIndustryCode());
        result.put("industryName", task.getIndustryName());
        result.put("customerCode", task.getCustomerCode());
        result.put("customerName", task.getCustomerName());
        result.put("status", task.getStatus());
        result.put("currentStage", task.getCurrentStage());
        result.put("currentStepCode", task.getCurrentStepCode());
        result.put("progressPercent", task.getProgressPercent());
        result.put("attemptNo", task.getAttemptNo());
        result.put("errorCode", task.getErrorCode());
        result.put("errorMessage", task.getErrorMessage());
        if ("TRAIN".equals(task.getBusinessType())) {
            result.put("trainBatchNo", task.getBusinessNo());
        } else if ("FORECAST".equals(task.getBusinessType())) {
            result.put("forecastBatchNo", task.getBusinessNo());
        }
        return result;
    }

    private Map<String, Long> summary(List<ExecutionTaskTb> tasks) {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("total", (long) tasks.size());
        result.put("pending", count(tasks, "PENDING") + count(tasks, "WAITING"));
        result.put("running", count(tasks, "RUNNING"));
        result.put("success", count(tasks, "SUCCESS"));
        result.put("failed", count(tasks, "FAILED"));
        result.put("cancelled", count(tasks, "CANCELLED"));
        return result;
    }

    private long count(List<ExecutionTaskTb> tasks, String status) {
        return tasks.stream().filter(task -> status.equals(task.getStatus())).count();
    }

    private boolean terminal(String status) {
        return Set.of("SUCCESS", "FAILED", "CANCELLED").contains(status);
    }

    private String stepName(String code) {
        if (code == null) return null;
        return switch (code) {
            case "WAIT_TRAINING_RESULT" -> "等待训练结果";
            case "TRAINING_COMPLETED" -> "训练完成";
            case "WAIT_FORECAST_RESULT" -> "等待预测结果";
            case "FORECAST_COMPLETED" -> "预测完成";
            case "START_FORECAST" -> "提交预测";
            default -> code;
        };
    }
}
