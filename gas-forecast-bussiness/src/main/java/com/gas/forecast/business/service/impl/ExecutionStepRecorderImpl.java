package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.enums.ExecutionStepCode;
import com.gas.forecast.business.service.ExecutionStepRecorder;
import com.gas.forecast.business.service.ExecutionTaskEventService;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.ExecutionStepTb;
import com.gas.forecast.dao.domain.ExecutionTaskDetailTb;
import com.gas.forecast.dao.mapper.ExecutionStepTbMapper;
import com.gas.forecast.dao.mapper.ExecutionTaskDetailTbMapper;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 六步业务流程的真实状态记录器；通过训练或预测批次反查任务明细。 */
@Service
@RequiredArgsConstructor
public class ExecutionStepRecorderImpl implements ExecutionStepRecorder {
    private final ExecutionStepTbMapper executionStepTbMapper;
    private final ExecutionTaskDetailTbMapper executionTaskDetailTbMapper;
    private final ExecutionTaskEventService executionTaskEventService;

    @Override
    @Transactional
    public void initialize(ExecutionTaskDetailTb taskDetail) {
        for (ExecutionStepCode code : ExecutionStepCode.values()) {
            ExecutionStepTb step = new ExecutionStepTb();
            step.setTaskDetailNo(taskDetail.getDetailNo());
            step.setStepNo(taskDetail.getDetailNo() + "-S" + String.format("%02d", code.getOrder()));
            step.setAttemptNo(1);
            step.setStepCode(code.name());
            step.setStepName(code.getName());
            step.setStepOrder(code.getOrder());
            step.setStatus("PENDING");
            step.setCreatedAt(new Date());
            step.setUpdatedAt(new Date());
            executionStepTbMapper.insert(step);
        }
    }

    @Override public void startByTrainBatch(String batchNo, ExecutionStepCode code, String input) { trainDetails(batchNo).forEach(detail -> start(detail, code, input)); }
    @Override public void successByTrainBatch(String batchNo, ExecutionStepCode code, String output, String metrics) { trainDetails(batchNo).forEach(detail -> success(detail, code, output, metrics)); }
    @Override public void successWithDetailsByTrainBatch(String batchNo, ExecutionStepCode code, String summary, String detailJson) { trainDetails(batchNo).forEach(detail -> success(detail, code, summary, detailJson)); }
    @Override public void failByTrainBatch(String batchNo, ExecutionStepCode code, String errorCode, Throwable error) { trainDetails(batchNo).forEach(detail -> fail(detail, code, errorCode, error)); }
    @Override public void startByForecastBatch(String batchNo, ExecutionStepCode code, String input) { forecastDetails(batchNo).forEach(detail -> start(detail, code, input)); }
    @Override public void successByForecastBatch(String batchNo, ExecutionStepCode code, String output, String metrics) { forecastDetails(batchNo).forEach(detail -> success(detail, code, output, metrics)); }
    @Override public void failByForecastBatch(String batchNo, ExecutionStepCode code, String errorCode, Throwable error) { forecastDetails(batchNo).forEach(detail -> fail(detail, code, errorCode, error)); }
    @Override public void skipByForecastBatch(String batchNo, ExecutionStepCode code, String message) { forecastDetails(batchNo).forEach(detail -> skip(detail, code, message)); }

    private void start(ExecutionTaskDetailTb detail, ExecutionStepCode code, String input) {
        if (detail == null) return;
        ExecutionStepTb step = step(detail.getDetailNo(), code);
        if (step == null || "SUCCESS".equals(step.getStatus())) return;
        Date now = new Date(); step.setStatus("RUNNING"); step.setInputJson(input); step.setStartedAt(now); step.setUpdatedAt(now);
        executionStepTbMapper.updateById(step); executionTaskEventService.publishStep(detail, step);
    }
    private void success(ExecutionTaskDetailTb detail, ExecutionStepCode code, String output, String metrics) {
        if (detail == null) return;
        ExecutionStepTb step = step(detail.getDetailNo(), code); if (step == null) return;
        Date now = new Date(); step.setStatus("SUCCESS"); step.setMessage(output); step.setOutputJson(output); step.setMetricsJson(metrics); step.setFinishedAt(now);
        step.setDurationMs(step.getStartedAt() == null ? 0 : Math.max(0, now.getTime() - step.getStartedAt().getTime())); step.setUpdatedAt(now); executionStepTbMapper.updateById(step); executionTaskEventService.publishStep(detail, step);
    }
    private void fail(ExecutionTaskDetailTb detail, ExecutionStepCode code, String errorCode, Throwable error) {
        if (detail == null) return;
        ExecutionStepTb step = step(detail.getDetailNo(), code); if (step == null) return;
        Date now = new Date(); step.setStatus("FAILED"); step.setErrorCode(errorCode); step.setErrorMessage(readableFailure(errorCode, error)); step.setFinishedAt(now);
        step.setDurationMs(step.getStartedAt() == null ? 0 : Math.max(0, now.getTime() - step.getStartedAt().getTime())); step.setUpdatedAt(now); executionStepTbMapper.updateById(step); executionTaskEventService.publishStep(detail, step);
    }
    private void skip(ExecutionTaskDetailTb detail, ExecutionStepCode code, String message) {
        if (detail == null) return;
        ExecutionStepTb step = step(detail.getDetailNo(), code); if (step == null) return;
        Date now = new Date(); step.setStatus("SKIPPED"); step.setMessage(message); step.setFinishedAt(now); step.setDurationMs(0L); step.setUpdatedAt(now); executionStepTbMapper.updateById(step); executionTaskEventService.publishStep(detail, step);
    }
    /** 不把下游 Python 的 KeyError 或空异常直接暴露成“date”“data”等无意义文本。 */
    private String readableFailure(String errorCode, Throwable error) {
        String message = error == null ? null : error.getMessage();
        if (!TextUtils.hasText(message)) return "执行失败，未获取到具体异常信息，请查看服务日志";
        String normalized = message.trim().replace("'", "");
        if ("date".equalsIgnoreCase(normalized)) {
            return "未来销量预测失败：模型平台缺少日期字段（date），请检查历史销量或特征数据是否包含有效日期";
        }
        if ("data".equalsIgnoreCase(normalized)) {
            return "未来销量预测失败：模型平台未返回有效数据，请检查预测请求和模型平台服务";
        }
        return TextUtils.hasText(errorCode) ? "执行失败：" + message : message;
    }
    private ExecutionStepTb step(String detailNo, ExecutionStepCode code) { return executionStepTbMapper.selectOne(Wrappers.<ExecutionStepTb>lambdaQuery().eq(ExecutionStepTb::getTaskDetailNo, detailNo).eq(ExecutionStepTb::getAttemptNo, 1).eq(ExecutionStepTb::getStepCode, code.name()).last("limit 1")); }
    private java.util.List<ExecutionTaskDetailTb> trainDetails(String batchNo) { return TextUtils.hasText(batchNo) ? executionTaskDetailTbMapper.selectList(Wrappers.<ExecutionTaskDetailTb>lambdaQuery().eq(ExecutionTaskDetailTb::getTrainBatchNo, batchNo)) : java.util.List.of(); }
    private java.util.List<ExecutionTaskDetailTb> forecastDetails(String batchNo) { return TextUtils.hasText(batchNo) ? executionTaskDetailTbMapper.selectList(Wrappers.<ExecutionTaskDetailTb>lambdaQuery().eq(ExecutionTaskDetailTb::getForecastBatchNo, batchNo)) : java.util.List.of(); }
}
