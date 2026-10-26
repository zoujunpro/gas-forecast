package com.gas.forecast.business.common.scheduler;

import com.gas.forecast.business.common.client.modelplatform.ModelPlatformClient;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gas.forecast.business.enums.ExecutionStepCodeEnum;
import com.gas.forecast.business.enums.ExecutionTargetStatusEnum;
import com.gas.forecast.business.application.task.step.ExecutionStepRecorder;
import com.gas.forecast.business.application.task.ExecutionTaskLifecycleService;
import com.gas.forecast.business.application.forecast.ForecastExecutionPayloadFactory;
import com.gas.forecast.business.application.forecast.ForecastExecutionRecordService;
import com.gas.forecast.business.application.forecast.ForecastExecutionResultPersistenceService;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.ExecutionTaskDetailTb;
import com.gas.forecast.dao.domain.ExecutionTaskTb;
import com.gas.forecast.dao.domain.ModelExecuteRecordTb;
import com.gas.forecast.dao.mapper.ExecutionTaskDetailTbMapper;
import com.gas.forecast.dao.mapper.ExecutionTaskTbMapper;
import com.gas.forecast.dao.mapper.ModelExecuteRecordTbMapper;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 以任务明细为队列项执行统一模型预测。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "gas.agent.forecast-queue", name = "scheduler-enabled", havingValue = "true")
public class ForecastExecutionDispatcher {
    private final ExecutionTaskDetailTbMapper executionTaskDetailMapper;
    private final ExecutionTaskTbMapper executionTaskMapper;
    private final ModelExecuteRecordTbMapper modelExecuteRecordMapper;
    private final ExecutionTaskLifecycleService executionTaskLifecycleService;
    private final ForecastExecutionRecordService forecastExecutionRecordService;
    private final ForecastExecutionPayloadFactory forecastExecutionPayloadFactory;
    private final ForecastExecutionResultPersistenceService forecastExecutionResultPersistenceService;
    private final ModelPlatformClient modelPlatformClient;
    private final ExecutionStepRecorder executionStepRecorder;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${gas.agent.forecast-queue.dispatch-delay-ms:2000}")
    public void dispatch() {
        List<ExecutionTaskDetailTb> pendingExecutionTaskDetails = executionTaskDetailMapper.selectList(Wrappers.<ExecutionTaskDetailTb>lambdaQuery()
                // FAILED 只能由显式重试恢复为 PENDING，不能由定时器无限自动重试。
                .eq(ExecutionTaskDetailTb::getStatus, ExecutionTargetStatusEnum.PENDING.getCode())
                .orderByAsc(ExecutionTaskDetailTb::getId).last("limit 10"));
        for (ExecutionTaskDetailTb pendingExecutionTaskDetail : pendingExecutionTaskDetails) {
            ExecutionTaskTb executionTask = executionTaskMapper.selectOne(Wrappers.<ExecutionTaskTb>lambdaQuery().eq(ExecutionTaskTb::getTaskNo, pendingExecutionTaskDetail.getTaskNo()).last("limit 1"));
            if (executionTask == null) continue;
            ExecutionTaskDetailTb executionTaskDetail = executionTaskLifecycleService.claimForecastDetail(pendingExecutionTaskDetail.getId());
            if (executionTaskDetail != null) executeForecast(executionTask, executionTaskDetail);
        }
    }

    private void executeForecast(ExecutionTaskTb executionTask, ExecutionTaskDetailTb executionTaskDetail) {
        ModelExecuteRecordTb modelExecuteRecord = null;
        try {
            modelExecuteRecord = forecastExecutionRecordService.prepare(executionTaskDetail, executionTask);
            executionStepRecorder.startByForecastNo(modelExecuteRecord.getForecastNo(), ExecutionStepCodeEnum.SALES_DATA_PREPARE, "正在准备历史销量数据");
            JsonNode forecastExecutionPayload = forecastExecutionPayloadFactory.build(executionTask, executionTaskDetail, modelExecuteRecord);
            modelExecuteRecord.setRequestParam(forecastExecutionPayload.toString());
            modelExecuteRecordMapper.updateById(modelExecuteRecord);
            executionStepRecorder.successByForecastNo(modelExecuteRecord.getForecastNo(), ExecutionStepCodeEnum.SALES_DATA_PREPARE,
                    "历史销量数据已准备，共" + modelExecuteRecord.getHistoryRows() + "条，覆盖"
                            + modelExecuteRecord.getHistoryStartDate() + "至" + modelExecuteRecord.getHistoryEndDate(), null);

            executionStepRecorder.startByForecastNo(modelExecuteRecord.getForecastNo(), ExecutionStepCodeEnum.FACTOR_DATA_PREPARE,
                    "正在准备日期、周期等基础特征");
            executionStepRecorder.successByForecastNo(modelExecuteRecord.getForecastNo(), ExecutionStepCodeEnum.FACTOR_DATA_PREPARE,
                    "基础特征已准备：日期派生、周期及滞后滚动特征", null);

            executionStepRecorder.startByForecastNo(modelExecuteRecord.getForecastNo(), ExecutionStepCodeEnum.DYNAMIC_INDICATOR,
                    "正在识别本次预测可用的动态指标");
            executionStepRecorder.successByForecastNo(modelExecuteRecord.getForecastNo(), ExecutionStepCodeEnum.DYNAMIC_INDICATOR,
                    "动态指标处理完成，本次采用模型内置特征快照", null);

            executionStepRecorder.startByForecastNo(modelExecuteRecord.getForecastNo(), ExecutionStepCodeEnum.MODEL_SELECT, "正在训练、回测并筛选模型");
            JsonNode forecastExecutionResult = modelPlatformClient.executeForecast(forecastExecutionPayload);
            applyForecastExecutionResult(modelExecuteRecord, forecastExecutionResult);
            forecastExecutionResultPersistenceService.replace(modelExecuteRecord, forecastExecutionResult);
            executionStepRecorder.successByForecastNo(modelExecuteRecord.getForecastNo(), ExecutionStepCodeEnum.MODEL_SELECT, "模型训练与回测完成", forecastExecutionResult.path("training").toString());
            executionStepRecorder.startByForecastNo(modelExecuteRecord.getForecastNo(), ExecutionStepCodeEnum.FORECAST, "正在生成未来销量预测");
            executionStepRecorder.successByForecastNo(modelExecuteRecord.getForecastNo(), ExecutionStepCodeEnum.FORECAST,
                    "预测完成，共" + forecastExecutionResult.path("forecast").path("points").size() + "个数据点", forecastExecutionResult.path("forecast").toString());
            executionStepRecorder.startByForecastNo(modelExecuteRecord.getForecastNo(), ExecutionStepCodeEnum.FORECAST_ANALYSIS,
                    "正在整理回测与预测结果");
            String forecastAnalysis = buildForecastAnalysisMetrics(modelExecuteRecord, forecastExecutionResult);
            modelExecuteRecordMapper.updateById(modelExecuteRecord);
            executionStepRecorder.successByForecastNo(modelExecuteRecord.getForecastNo(), ExecutionStepCodeEnum.FORECAST_ANALYSIS,
                    "预测结果研判已生成", forecastAnalysis);
            executionTaskLifecycleService.completeForecastDetail(executionTaskDetail.getId(), true);
        } catch (RuntimeException exception) {
            if (modelExecuteRecord != null) {
                modelExecuteRecord.setStatus("FAILED"); modelExecuteRecord.setErrorMessage(exception.getMessage()); modelExecuteRecord.setFinishedAt(new Date()); modelExecuteRecord.setUpdatedAt(new Date());
                modelExecuteRecordMapper.updateById(modelExecuteRecord);
                executionStepRecorder.failByForecastNo(modelExecuteRecord.getForecastNo(), ExecutionStepCodeEnum.MODEL_SELECT, "FORECAST_EXECUTION_FAILED", exception);
            }
            executionTaskLifecycleService.completeForecastDetail(executionTaskDetail.getId(), false);
            log.error("统一预测执行失败 detailId={}", executionTaskDetail.getId(), exception);
        }
    }

    private void applyForecastExecutionResult(ModelExecuteRecordTb modelExecuteRecord, JsonNode forecastExecutionResult) {
        JsonNode trainingResult = forecastExecutionResult.path("training"); JsonNode trainingMetrics = trainingResult.path("metrics");
        modelExecuteRecord.setStatus("SUCCESS"); modelExecuteRecord.setTrainingResultJson(trainingResult.toString()); modelExecuteRecord.setBacktestResultJson(forecastExecutionResult.path("backtest").toString()); modelExecuteRecord.setForecastResultJson(forecastExecutionResult.path("forecast").toString());
        modelExecuteRecord.setBestModel(textOf(trainingResult, "selected_model_name")); modelExecuteRecord.setCandidateCount(trainingResult.path("requested_candidate_count").asInt());
        modelExecuteRecord.setSuccessfulCandidateCount(trainingResult.path("successful_candidate_count").asInt());
        modelExecuteRecord.setMape(decimalOf(trainingMetrics, "mape")); modelExecuteRecord.setWmape(decimalOf(trainingMetrics, "wmape")); modelExecuteRecord.setSmape(decimalOf(trainingMetrics, "smape"));
        modelExecuteRecord.setRmse(decimalOf(trainingMetrics, "rmse")); modelExecuteRecord.setMae(decimalOf(trainingMetrics, "mae")); modelExecuteRecord.setR2(decimalOf(trainingMetrics, "r2"));
        modelExecuteRecord.setFinishedAt(new Date()); modelExecuteRecord.setUpdatedAt(new Date()); modelExecuteRecordMapper.updateById(modelExecuteRecord);
    }
    private String textOf(JsonNode jsonNode, String fieldName) { String textValue = jsonNode.path(fieldName).asText(null); return TextUtils.hasText(textValue) ? textValue : null; }
    private BigDecimal decimalOf(JsonNode jsonNode, String fieldName) { return jsonNode.path(fieldName).isNumber() ? jsonNode.path(fieldName).decimalValue() : null; }

    /**
     * 预测完成后给会话卡片留下一段短、可追溯的结果研判。
     * 不虚构外部业务原因，只依据模型实际回测指标与未来预测点生成结论。
     */
    private String buildForecastAnalysisMetrics(ModelExecuteRecordTb modelExecuteRecord, JsonNode forecastExecutionResult) {
        JsonNode backtestPoints = forecastExecutionResult.path("backtest").path("points");
        JsonNode forecastPoints = forecastExecutionResult.path("forecast").path("points");
        int backtestDays = backtestPoints.isArray() ? backtestPoints.size() : 0;
        int forecastDays = forecastPoints.isArray() ? forecastPoints.size() : 0;
        BigDecimal meanAbsolutePercentageError = modelExecuteRecord.getMape();
        String accuracyAssessment = meanAbsolutePercentageError == null ? "回测指标未返回，暂不对精度作判断"
                : meanAbsolutePercentageError.compareTo(BigDecimal.TEN) <= 0 ? "回测精度较好，可作为近期经营参考"
                : meanAbsolutePercentageError.compareTo(BigDecimal.valueOf(20)) <= 0 ? "回测精度可用，建议结合实际业务变化持续跟踪"
                : "回测误差偏高，建议将本次结果作为趋势参考，并结合实际销量持续校正";
        StringBuilder analysisContent = new StringBuilder("本次预测采用")
                .append(textOf(modelExecuteRecord.getBestModel(), "默认模型"));
        if (backtestDays > 0) analysisContent.append("，基于近").append(backtestDays).append("天回测");
        if (meanAbsolutePercentageError != null) analysisContent.append("，MAPE 为").append(formatNumber(meanAbsolutePercentageError)).append("%");
        analysisContent.append("。 ").append(accuracyAssessment).append("。");
        if (forecastDays > 0) {
            double totalForecastValue = 0D;
            for (JsonNode forecastPoint : forecastPoints) totalForecastValue += forecastPoint.path("prediction").asDouble(forecastPoint.path("forecast_value").asDouble());
            analysisContent.append("未来").append(forecastDays).append("天预测均值为")
                    .append(String.format(Locale.ROOT, "%.2f", totalForecastValue / forecastDays)).append("。");
        }
        modelExecuteRecord.setAnalysisContent(analysisContent.toString());
        ObjectNode forecastAnalysisMetrics = objectMapper.createObjectNode();
        forecastAnalysisMetrics.put("analysis", analysisContent.toString());
        forecastAnalysisMetrics.put("model", textOf(modelExecuteRecord.getBestModel(), "默认模型"));
        forecastAnalysisMetrics.put("backtestDays", backtestDays);
        forecastAnalysisMetrics.put("forecastDays", forecastDays);
        if (meanAbsolutePercentageError != null) forecastAnalysisMetrics.put("mape", meanAbsolutePercentageError);
        return forecastAnalysisMetrics.toString();
    }

    private String textOf(String textValue, String fallbackValue) { return TextUtils.hasText(textValue) ? textValue : fallbackValue; }
    private String formatNumber(BigDecimal decimalValue) { return String.format(Locale.ROOT, "%.2f", decimalValue); }
}
