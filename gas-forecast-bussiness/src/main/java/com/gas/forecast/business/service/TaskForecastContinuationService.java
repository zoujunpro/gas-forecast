package com.gas.forecast.business.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.dto.request.ModelForecastExecuteRequest;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.AgentExecutionBatchTb;
import com.gas.forecast.dao.domain.AgentExecutionTaskTb;
import com.gas.forecast.dao.domain.AiConversationTb;
import com.gas.forecast.dao.domain.ModelForecastConfigTb;
import com.gas.forecast.dao.mapper.AgentExecutionBatchTbMapper;
import com.gas.forecast.dao.mapper.AgentExecutionTaskTbMapper;
import com.gas.forecast.dao.mapper.AiConversationTbMapper;
import com.gas.forecast.dao.mapper.ModelForecastConfigTbMapper;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 只续跑用户已经明确发起的预测任务，不扫描或创建无来源的预测。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskForecastContinuationService {
    private final AgentExecutionTaskTbMapper taskMapper;
    private final AgentExecutionBatchTbMapper batchMapper;
    private final ModelForecastConfigTbMapper forecastConfigMapper;
    private final AiConversationTbMapper conversationMapper;
    private final ModelForecastManagementService forecastService;

    public void continueAfterTraining(String trainBatchNo) {
        if (!TextUtils.hasText(trainBatchNo)) return;
        List<AgentExecutionTaskTb> tasks = taskMapper.selectList(Wrappers.<AgentExecutionTaskTb>lambdaQuery()
                .eq(AgentExecutionTaskTb::getTaskType, "FORECAST").eq(AgentExecutionTaskTb::getLatestTrainBatchNo, trainBatchNo)
                .isNull(AgentExecutionTaskTb::getLatestForecastBatchNo).in(AgentExecutionTaskTb::getStatus, "EXECUTING", "WAITING_TRAINING"));
        for (AgentExecutionTaskTb task : tasks) {
            try {
                continueOne(task, trainBatchNo);
            } catch (RuntimeException exception) {
                log.error("用户预测任务训练成功后续跑失败 taskNo={}, trainBatchNo={}", task.getTaskNo(), trainBatchNo, exception);
                markFailed(task, exception.getMessage());
            }
        }
    }

    private void continueOne(AgentExecutionTaskTb task, String trainBatchNo) {
        int claimed = taskMapper.update(null, Wrappers.<AgentExecutionTaskTb>lambdaUpdate().eq(AgentExecutionTaskTb::getId, task.getId())
                .isNull(AgentExecutionTaskTb::getLatestForecastBatchNo).in(AgentExecutionTaskTb::getStatus, "EXECUTING", "WAITING_TRAINING")
                .ne(AgentExecutionTaskTb::getCurrentStepCode, "SUBMITTING_FORECAST").set(AgentExecutionTaskTb::getCurrentStage, "FORECASTING")
                .set(AgentExecutionTaskTb::getCurrentStepCode, "SUBMITTING_FORECAST").set(AgentExecutionTaskTb::getProgressPercent, BigDecimal.valueOf(60))
                .set(AgentExecutionTaskTb::getUpdatedAt, new Date()));
        if (claimed != 1) return;
        ModelForecastConfigTb config = forecastConfigMapper.selectOne(Wrappers.<ModelForecastConfigTb>lambdaQuery()
                .eq(ModelForecastConfigTb::getAgentCode, task.getAgentCode()).eq(ModelForecastConfigTb::getCustomerCode, task.getCustomerCode())
                .eq(ModelForecastConfigTb::getEnabled, 1).orderByDesc(ModelForecastConfigTb::getUpdatedAt).orderByDesc(ModelForecastConfigTb::getId).last("limit 1"));
        if (config == null) throw new IllegalStateException("未找到用户任务对应的预测配置");
        ModelForecastExecuteRequest request = new ModelForecastExecuteRequest();
        request.setForecastId(config.getId()); request.setForecastStartDate(config.getForecastStartDate());
        request.setForecastHorizon(config.getForecastHorizon()); request.setForecastFrequency(config.getForecastFrequency());
        var response = forecastService.submit(request);
        Date now = new Date();
        AgentExecutionBatchTb relation = new AgentExecutionBatchTb();
        relation.setTaskNo(task.getTaskNo()); relation.setTaskAttemptNo(task.getTaskAttemptNo()); relation.setStage("FORECASTING");
        relation.setBatchType("FORECAST"); relation.setBatchNo(response.getForecastBatchNo()); relation.setBatchAttemptNo(1);
        relation.setPreviousBatchNo(trainBatchNo); relation.setRetryMode("CONTINUE_TASK"); relation.setTriggerSource("USER_TASK"); relation.setStatus("RUNNING");
        relation.setCreatedBy(task.getCreatedBy()); relation.setCreatedByName(task.getCreatedByName()); relation.setCreatedAt(now); relation.setStartedAt(now); relation.setUpdatedAt(now);
        batchMapper.insert(relation);
        task.setLatestForecastBatchNo(response.getForecastBatchNo()); task.setStatus("EXECUTING"); task.setCurrentStage("FORECASTING");
        task.setCurrentStepCode("WAIT_FORECAST_RESULT"); task.setProgressPercent(BigDecimal.valueOf(70)); task.setUpdatedAt(now);
        taskMapper.updateById(task);
        if (TextUtils.hasText(task.getConversationNo())) {
            conversationMapper.update(null, Wrappers.<AiConversationTb>lambdaUpdate()
                    .eq(AiConversationTb::getConversationNo, task.getConversationNo())
                    .set(AiConversationTb::getLastTrainBatchNo, trainBatchNo)
                    .set(AiConversationTb::getLastForecastBatchNo, response.getForecastBatchNo())
                    .set(AiConversationTb::getUpdatedAt, now));
        }
        log.info("用户预测任务已从训练阶段续跑预测 taskNo={}, trainBatchNo={}, forecastBatchNo={}", task.getTaskNo(), trainBatchNo, response.getForecastBatchNo());
    }

    private void markFailed(AgentExecutionTaskTb task, String message) {
        task.setStatus("FAILED"); task.setCurrentStage("FORECASTING"); task.setCurrentStepCode("START_FORECAST");
        task.setErrorCode("TASK_CONTINUATION_FAILED"); task.setErrorMessage(message); task.setRetryable(1);
        task.setFinishedAt(new Date()); task.setUpdatedAt(new Date()); taskMapper.updateById(task);
    }
}
