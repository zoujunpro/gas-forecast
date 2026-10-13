package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.service.ModelTrainStepRecorder;
import com.gas.forecast.dao.domain.ModelTrainExecutionStepTb;
import com.gas.forecast.dao.mapper.ModelTrainExecutionStepTbMapper;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ModelTrainStepRecorderImpl implements ModelTrainStepRecorder {
    private final ModelTrainExecutionStepTbMapper mapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long start(String batchNo, int attemptNo, Long parentStepId, int stepLevel, String stepCode, String stepName, int stepOrder, String inputSummary) {
        Date now = new Date();
        ModelTrainExecutionStepTb existing = mapper.selectOne(Wrappers.<ModelTrainExecutionStepTb>lambdaQuery().eq(ModelTrainExecutionStepTb::getTrainBatchNo, batchNo)
                .eq(ModelTrainExecutionStepTb::getAttemptNo, attemptNo).eq(ModelTrainExecutionStepTb::getStepCode, stepCode).last("limit 1"));
        ModelTrainExecutionStepTb step = existing == null ? new ModelTrainExecutionStepTb() : existing;
        step.setTrainBatchNo(batchNo);
        step.setAttemptNo(attemptNo);
        step.setParentStepId(parentStepId);
        step.setStepLevel(stepLevel);
        step.setStepCode(stepCode);
        step.setStepName(stepName);
        step.setStepOrder(stepOrder);
        step.setStatus("RUNNING");
        step.setInputSummary(abbreviate(inputSummary, 4000));
        step.setOutputSummary(null);
        step.setProducedCount(null);
        step.setErrorMessage(null);
        step.setStartedAt(now);
        step.setFinishedAt(null);
        step.setDurationMs(null);
        step.setUpdatedAt(now);
        if (existing == null) {
            step.setCreatedAt(now);
            mapper.insert(step);
        } else
            mapper.updateById(step);
        return step.getId();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void success(Long stepId, String outputSummary, Integer producedCount) {
        finish(stepId, "SUCCESS", outputSummary, producedCount, null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long stepId, Throwable error) {
        finish(stepId, "FAILED", null, null, error == null ? "未知错误" : error.getMessage());
    }

    private void finish(Long stepId, String status, String outputSummary, Integer producedCount, String errorMessage) {
        if (stepId == null)
            return;
        ModelTrainExecutionStepTb step = mapper.selectById(stepId);
        if (step == null)
            return;
        Date now = new Date();
        step.setStatus(status);
        step.setOutputSummary(abbreviate(outputSummary, 20000));
        step.setProducedCount(producedCount);
        step.setErrorMessage(abbreviate(errorMessage, 4000));
        step.setFinishedAt(now);
        step.setDurationMs(Math.max(0, now.getTime() - step.getStartedAt().getTime()));
        step.setUpdatedAt(now);
        mapper.updateById(step);
    }

    private String abbreviate(String value, int max) {
        if (value == null || value.length() <= max)
            return value;
        return value.substring(0, max);
    }
}
