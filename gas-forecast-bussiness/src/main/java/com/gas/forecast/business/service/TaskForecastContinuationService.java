package com.gas.forecast.business.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.dto.request.ModelForecastExecuteRequest;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.ExecutionBatchTb;
import com.gas.forecast.dao.domain.ExecutionTaskTb;
import com.gas.forecast.dao.domain.ModelForecastConfigTb;
import com.gas.forecast.dao.mapper.ExecutionBatchTbMapper;
import com.gas.forecast.dao.mapper.ExecutionTaskTbMapper;
import com.gas.forecast.dao.mapper.ModelForecastConfigTbMapper;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 训练成功后，在同一个统一执行批次中创建并提交预测任务。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskForecastContinuationService {
    private final ExecutionTaskTbMapper taskMapper;
    private final ExecutionBatchTbMapper batchMapper;
    private final ModelForecastConfigTbMapper forecastConfigMapper;
    private final ModelForecastManagementService forecastService;
    private final ExecutionBatchProgressService batchProgressService;

    /** 只续跑本次训练所属统一批次中的预测，不扫描或创建无来源的预测。 */
    public void continueAfterTraining(String trainBatchNo) {
        if (!TextUtils.hasText(trainBatchNo)) return;
        List<ExecutionTaskTb> trainTasks = taskMapper.selectList(
                Wrappers.<ExecutionTaskTb>lambdaQuery()
                        .eq(ExecutionTaskTb::getBusinessType, "TRAIN")
                        .eq(ExecutionTaskTb::getBusinessNo, trainBatchNo)
                        .in(ExecutionTaskTb::getStatus, "RUNNING", "WAITING", "SUCCESS"));
        for (ExecutionTaskTb trainTask : trainTasks) {
            if (!isWorkflowBatch(trainTask.getBatchNo())) continue;
            try {
                continueOne(trainTask, trainBatchNo);
            } catch (RuntimeException exception) {
                log.error("训练成功后续跑预测失败 taskNo={}, trainBatchNo={}",
                        trainTask.getTaskNo(), trainBatchNo, exception);
                createFailedForecastTask(trainTask, exception.getMessage());
            }
        }
    }

    private void continueOne(ExecutionTaskTb trainTask, String trainBatchNo) {
        if (forecastTaskExists(trainTask)) return;
        Date now = new Date();
        int claimed = taskMapper.update(null,
                Wrappers.<ExecutionTaskTb>lambdaUpdate()
                        .eq(ExecutionTaskTb::getId, trainTask.getId())
                        .in(ExecutionTaskTb::getStatus, "RUNNING", "WAITING", "SUCCESS")
                        .ne(ExecutionTaskTb::getCurrentStepCode, "SUBMITTING_FORECAST")
                        .set(ExecutionTaskTb::getStatus, "SUCCESS")
                        .set(ExecutionTaskTb::getCurrentStage, "COMPLETED")
                        .set(ExecutionTaskTb::getCurrentStepCode, "TRAINING_COMPLETED")
                        .set(ExecutionTaskTb::getProgressPercent, BigDecimal.valueOf(100))
                        .set(ExecutionTaskTb::getFinishedAt, now)
                        .set(ExecutionTaskTb::getUpdatedAt, now));
        if (claimed != 1) return;

        ModelForecastConfigTb config = forecastConfigMapper.selectOne(
                Wrappers.<ModelForecastConfigTb>lambdaQuery()
                        .eq(ModelForecastConfigTb::getAgentCode, trainTask.getAgentCode())
                        .eq(ModelForecastConfigTb::getCustomerCode, trainTask.getCustomerCode())
                        .eq(ModelForecastConfigTb::getEnabled, 1)
                        .orderByDesc(ModelForecastConfigTb::getUpdatedAt)
                        .orderByDesc(ModelForecastConfigTb::getId)
                        .last("limit 1"));
        if (config == null) throw new IllegalStateException("未找到训练任务对应的预测配置");

        ModelForecastExecuteRequest request = new ModelForecastExecuteRequest();
        request.setForecastId(config.getId());
        request.setForecastStartDate(config.getForecastStartDate());
        request.setForecastHorizon(config.getForecastHorizon());
        request.setForecastFrequency(config.getForecastFrequency());
        var response = forecastService.submit(request);

        ExecutionTaskTb forecastTask = newForecastTask(trainTask);
        forecastTask.setBusinessNo(response.getForecastBatchNo());
        forecastTask.setStatus("RUNNING");
        forecastTask.setProgressPercent(BigDecimal.valueOf(70));
        forecastTask.setStartedAt(now);
        taskMapper.insert(forecastTask);

        batchProgressService.refresh(trainTask.getBatchNo());
        log.info("训练任务已续跑预测 taskNo={}, trainBatchNo={}, forecastBatchNo={}",
                trainTask.getTaskNo(), trainBatchNo, response.getForecastBatchNo());
    }

    private boolean forecastTaskExists(ExecutionTaskTb trainTask) {
        return taskMapper.selectCount(Wrappers.<ExecutionTaskTb>lambdaQuery()
                .eq(ExecutionTaskTb::getBatchNo, trainTask.getBatchNo())
                .eq(ExecutionTaskTb::getBusinessType, "FORECAST")
                .eq(ExecutionTaskTb::getCustomerCode, trainTask.getCustomerCode())) > 0;
    }

    private boolean isWorkflowBatch(String batchNo) {
        return batchMapper.selectCount(Wrappers.<ExecutionBatchTb>lambdaQuery()
                .eq(ExecutionBatchTb::getBatchNo, batchNo)
                .eq(ExecutionBatchTb::getBatchType, "WORKFLOW")) > 0;
    }

    private void createFailedForecastTask(ExecutionTaskTb trainTask, String message) {
        if (forecastTaskExists(trainTask)) return;
        ExecutionTaskTb task = newForecastTask(trainTask);
        Date now = new Date();
        task.setStatus("FAILED");
        task.setProgressPercent(BigDecimal.ZERO);
        task.setErrorCode("FORECAST_CONTINUATION_FAILED");
        task.setErrorMessage(message);
        task.setFinishedAt(now);
        taskMapper.insert(task);
        batchProgressService.refresh(trainTask.getBatchNo());
    }

    private ExecutionTaskTb newForecastTask(ExecutionTaskTb trainTask) {
        Date now = new Date();
        ExecutionTaskTb task = new ExecutionTaskTb();
        task.setTaskNo("TASK-" + UUID.randomUUID().toString().replace("-", "").toUpperCase());
        task.setBatchNo(trainTask.getBatchNo());
        task.setBusinessType("FORECAST");
        task.setAgentCode(trainTask.getAgentCode());
        task.setScopeType(trainTask.getScopeType());
        task.setRegionCode(trainTask.getRegionCode());
        task.setRegionName(trainTask.getRegionName());
        task.setIndustryCode(trainTask.getIndustryCode());
        task.setIndustryName(trainTask.getIndustryName());
        task.setCustomerCode(trainTask.getCustomerCode());
        task.setCustomerName(trainTask.getCustomerName());
        task.setCurrentStage("FORECASTING");
        task.setCurrentStepCode("WAIT_FORECAST_RESULT");
        task.setAttemptNo(1);
        task.setMaxAttempts(3);
        task.setRequestJson(trainTask.getRequestJson());
        task.setCreatedBy(trainTask.getCreatedBy());
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return task;
    }

}
