package com.gas.forecast.business.common.scheduler;

import com.gas.forecast.business.common.client.modelplatform.ModelPlatformClient;
import com.gas.forecast.business.common.client.agent.PythonAgentChatClient;
import com.gas.forecast.business.dto.PythonForecastAnalysisRequest;
import com.gas.forecast.business.dto.PythonForecastAnalysisResponse;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gas.forecast.business.enums.ExecutionTargetStatusEnum;
import com.gas.forecast.business.application.task.ExecutionTaskLifecycleService;
import com.gas.forecast.business.application.forecast.ForecastExecutionPayloadFactory;
import com.gas.forecast.business.application.forecast.ForecastExecutionRecordService;
import com.gas.forecast.business.application.forecast.ForecastExecutionResultPersistenceService;
import com.gas.forecast.business.service.AgentRunEventRecorder;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.ExecutionTaskDetailTb;
import com.gas.forecast.dao.domain.ExecutionTaskTb;
import com.gas.forecast.dao.domain.ModelExecuteRecordTb;
import com.gas.forecast.dao.domain.DataDailySalesTb;
import com.gas.forecast.dao.mapper.DataDailySalesTbMapper;
import com.gas.forecast.dao.mapper.ExecutionTaskDetailTbMapper;
import com.gas.forecast.dao.mapper.ExecutionTaskTbMapper;
import com.gas.forecast.dao.mapper.ModelExecuteRecordTbMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
    private static final int SUPPORTED_WEATHER_FEATURE_COUNT = 18;
    private final ExecutionTaskDetailTbMapper executionTaskDetailMapper;
    private final ExecutionTaskTbMapper executionTaskMapper;
    private final ModelExecuteRecordTbMapper modelExecuteRecordMapper;
    private final DataDailySalesTbMapper dataDailySalesTbMapper;
    private final ExecutionTaskLifecycleService executionTaskLifecycleService;
    private final ForecastExecutionRecordService forecastExecutionRecordService;
    private final ForecastExecutionPayloadFactory forecastExecutionPayloadFactory;
    private final ForecastExecutionResultPersistenceService forecastExecutionResultPersistenceService;
    private final ModelPlatformClient modelPlatformClient;
    private final PythonAgentChatClient pythonAgentChatClient;
    private final AgentRunEventRecorder agentRunEventRecorder;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${gas.agent.forecast-queue.dispatch-delay-ms:2000}")
    public void dispatch() {
        List<ExecutionTaskDetailTb> pendingExecutionTaskDetails = executionTaskDetailMapper.selectList(Wrappers.<ExecutionTaskDetailTb>lambdaQuery()
                // FAILED 只能由显式重试恢复为 PENDING，不能由定时器无限自动重试。
                .eq(ExecutionTaskDetailTb::getStatus, ExecutionTargetStatusEnum.PENDING.getCode()).orderByAsc(ExecutionTaskDetailTb::getId).last("limit 10"));
        for (ExecutionTaskDetailTb pendingExecutionTaskDetail : pendingExecutionTaskDetails) {
            ExecutionTaskTb executionTask = executionTaskMapper.selectOne(Wrappers.<ExecutionTaskTb>lambdaQuery().eq(ExecutionTaskTb::getTaskNo, pendingExecutionTaskDetail.getTaskNo()).last("limit 1"));
            if (executionTask == null)
                continue;
            ExecutionTaskDetailTb executionTaskDetail = executionTaskLifecycleService.claimForecastDetail(pendingExecutionTaskDetail.getId());
            if (executionTaskDetail != null)
                executeForecast(executionTask, executionTaskDetail);
        }
    }

    private void executeForecast(ExecutionTaskTb executionTask, ExecutionTaskDetailTb executionTaskDetail) {
        ModelExecuteRecordTb modelExecuteRecord = null;
        Long activeEventId = null;
        try {
            modelExecuteRecord = forecastExecutionRecordService.prepare(executionTaskDetail, executionTask);
            activeEventId = agentRunEventRecorder.start(executionTask.getRunNo(), "DATA_QUERY", "正在读取历史用气数据。", modelExecuteRecord.getForecastNo(), executionTaskDetail.getDetailNo());
            JsonNode forecastExecutionPayload = forecastExecutionPayloadFactory.build(executionTask, executionTaskDetail, modelExecuteRecord);
            modelExecuteRecord.setRequestParam(forecastExecutionPayload.toString());
            modelExecuteRecordMapper.updateById(modelExecuteRecord);
            ObjectNode salesValidation = salesValidationMetrics(modelExecuteRecord);
            agentRunEventRecorder.success(activeEventId, salesValidationMessage(salesValidation), salesValidation.toString());
            activeEventId = null;

            activeEventId = agentRunEventRecorder.start(executionTask.getRunNo(), "FEATURE_PREPARE", "正在准备日期和天气特征。", modelExecuteRecord.getForecastNo(), executionTaskDetail.getDetailNo());
            ObjectNode weatherFeatures = weatherFeatureMetrics(forecastExecutionPayload);
            agentRunEventRecorder.success(activeEventId, weatherFeatureMessage(weatherFeatures) + "。", weatherFeatures.toString());
            activeEventId = null;

            activeEventId = agentRunEventRecorder.start(executionTask.getRunNo(), "DYNAMIC_INDICATOR", "正在检查本次预测可用的动态指标。", modelExecuteRecord.getForecastNo(), executionTaskDetail.getDetailNo());
            ObjectNode dynamicIndicatorMetrics = dynamicIndicatorMetrics();
            agentRunEventRecorder.success(activeEventId, "本次未接入外部动态指标，将结合历史用气、日期规律、近期变化和可用天气信息进行预测。", dynamicIndicatorMetrics.toString());
            activeEventId = null;

            JsonNode forecastExecutionResult = modelPlatformClient.executeForecast(forecastExecutionPayload);
            applyForecastExecutionResult(modelExecuteRecord, forecastExecutionResult);
            forecastExecutionResultPersistenceService.replace(modelExecuteRecord, forecastExecutionResult);
            activeEventId = agentRunEventRecorder.start(executionTask.getRunNo(), "RESULT_ANALYZE", "正在整理回测指标和预测解读。", modelExecuteRecord.getForecastNo(), executionTaskDetail.getDetailNo());
            String forecastAnalysis = buildForecastAnalysisMetrics(modelExecuteRecord, forecastExecutionResult);
            enrichForecastAnalysisWithAi(modelExecuteRecord, forecastExecutionResult);
            modelExecuteRecordMapper.updateById(modelExecuteRecord);
            agentRunEventRecorder.success(activeEventId, "预测结果、回测指标和AI解读已整理完成。", forecastAnalysis);
            activeEventId = null;
            executionTaskLifecycleService.completeForecastDetail(executionTaskDetail.getId(), true);
        } catch (RuntimeException exception) {
            if (activeEventId == null) {
                activeEventId = agentRunEventRecorder.start(executionTask.getRunNo(), "FORECAST", "预测执行失败。", modelExecuteRecord == null ? null : modelExecuteRecord.getForecastNo(), executionTaskDetail.getDetailNo());
            }
            agentRunEventRecorder.failure(activeEventId, "本次预测执行失败：" + exception.getMessage(), "FORECAST_EXECUTION_FAILED", exception);
            if (modelExecuteRecord != null) {
                modelExecuteRecord.setStatus("FAILED");
                modelExecuteRecord.setErrorMessage(exception.getMessage());
                modelExecuteRecord.setFinishedAt(new Date());
                modelExecuteRecord.setUpdatedAt(new Date());
                modelExecuteRecordMapper.updateById(modelExecuteRecord);
            }
            executionTaskLifecycleService.completeForecastDetail(executionTaskDetail.getId(), false);
            log.error("统一预测执行失败 detailId={}", executionTaskDetail.getId(), exception);
        }
    }

    private void applyForecastExecutionResult(ModelExecuteRecordTb modelExecuteRecord, JsonNode forecastExecutionResult) {
        JsonNode trainingResult = forecastExecutionResult.path("training");
        JsonNode trainingMetrics = trainingResult.path("metrics");
        modelExecuteRecord.setStatus("SUCCESS");
        modelExecuteRecord.setTrainingResultJson(trainingResult.toString());
        modelExecuteRecord.setBacktestResultJson(forecastExecutionResult.path("backtest").toString());
        modelExecuteRecord.setForecastResultJson(forecastExecutionResult.path("forecast").toString());
        modelExecuteRecord.setBestModel(textOf(trainingResult, "selected_model_name"));
        modelExecuteRecord.setCandidateCount(trainingResult.path("requested_candidate_count").asInt());
        modelExecuteRecord.setSuccessfulCandidateCount(trainingResult.path("successful_candidate_count").asInt());
        modelExecuteRecord.setMape(decimalOf(trainingMetrics, "mape"));
        modelExecuteRecord.setWmape(decimalOf(trainingMetrics, "wmape"));
        modelExecuteRecord.setSmape(decimalOf(trainingMetrics, "smape"));
        modelExecuteRecord.setRmse(decimalOf(trainingMetrics, "rmse"));
        modelExecuteRecord.setMae(decimalOf(trainingMetrics, "mae"));
        modelExecuteRecord.setR2(decimalOf(trainingMetrics, "r2"));
        modelExecuteRecord.setFinishedAt(new Date());
        modelExecuteRecord.setUpdatedAt(new Date());
        modelExecuteRecordMapper.updateById(modelExecuteRecord);
    }

    /**
     * 第二步只展示本次历史数据实际携带的天气字段，不把模型平台内部的派生特征当作已知事实。
     */
    private ObjectNode weatherFeatureMetrics(JsonNode payload) {
        Set<String> featureNames = new LinkedHashSet<>();
        int coveredDays = 0;
        for (JsonNode row : payload.path("history_dataset")) {
            boolean hasWeather = false;
            var fields = row.fieldNames();
            while (fields.hasNext()) {
                String label = weatherFeatureLabel(fields.next());
                if (label != null) {
                    featureNames.add(label);
                    hasWeather = true;
                }
            }
            if (hasWeather)
                coveredDays++;
        }
        ObjectNode metrics = objectMapper.createObjectNode();
        metrics.put("weatherSupportedFeatureCount", SUPPORTED_WEATHER_FEATURE_COUNT);
        // 仅统计历史数据中至少出现过一个非空值、并随本次请求送入模型的天气字段。
        metrics.put("weatherFeatureCount", featureNames.size());
        metrics.put("weatherCoveredDays", coveredDays);
        ArrayNode features = metrics.putArray("weatherFeatures");
        featureNames.forEach(features::add);
        return metrics;
    }

    private String weatherFeatureMessage(ObjectNode metrics) {
        int supported = metrics.path("weatherSupportedFeatureCount").asInt();
        int used = metrics.path("weatherFeatureCount").asInt();
        int coveredDays = metrics.path("weatherCoveredDays").asInt();
        return used == 0 ? "基础特征已准备：天气特征共" + supported + "项，本次实际使用0项" : "基础特征已准备：天气特征共" + supported + "项，实际使用" + used + "项，覆盖" + coveredDays + "天";
    }

    /** 把入模前的历史用气数据校验结果作为真实过程产物上送。 */
    private ObjectNode salesValidationMetrics(ModelExecuteRecordTb record) {
        var query = Wrappers.<DataDailySalesTb>lambdaQuery();
        if (TextUtils.hasText(record.getCustomerCode()))
            query.eq(DataDailySalesTb::getCustomerCode, record.getCustomerCode());
        else {
            if (TextUtils.hasText(record.getRegionCode()))
                query.eq(DataDailySalesTb::getRegionCode, record.getRegionCode());
            if (TextUtils.hasText(record.getIndustryCode()))
                query.eq(DataDailySalesTb::getIndustryCode, record.getIndustryCode());
        }
        if (record.getHistoryStartDate() != null)
            query.ge(DataDailySalesTb::getStatDate, record.getHistoryStartDate().toString());
        if (record.getHistoryEndDate() != null)
            query.le(DataDailySalesTb::getStatDate, record.getHistoryEndDate().toString());
        List<DataDailySalesTb> rows = dataDailySalesTbMapper.selectList(query);
        int emptySalesCount = (int) rows.stream().filter(item -> !TextUtils.hasText(item.getGasSales())).count();
        Set<LocalDate> dates = new HashSet<>();
        for (DataDailySalesTb item : rows) {
            if (!TextUtils.hasText(item.getGasSales()) || !TextUtils.hasText(item.getStatDate()))
                continue;
            try {
                dates.add(LocalDate.parse(item.getStatDate()));
            } catch (RuntimeException ignored) {
            }
        }
        LocalDate start = dates.stream().min(LocalDate::compareTo).orElse(record.getHistoryStartDate());
        LocalDate end = dates.stream().max(LocalDate::compareTo).orElse(record.getHistoryEndDate());
        long expectedDays = start == null || end == null ? 0 : ChronoUnit.DAYS.between(start, end) + 1;
        long missingDateCount = Math.max(0, expectedDays - dates.size());
        int dateGapCount = 0;
        if (dates.size() > 1) {
            List<LocalDate> ordered = dates.stream().sorted().toList();
            for (int index = 1; index < ordered.size(); index++)
                if (ChronoUnit.DAYS.between(ordered.get(index - 1), ordered.get(index)) > 1)
                    dateGapCount++;
        }
        ObjectNode metrics = objectMapper.createObjectNode();
        metrics.put("rawSalesRows", rows.size());
        metrics.put("validSalesRows", dates.size());
        metrics.put("emptySalesCount", emptySalesCount);
        metrics.put("missingDateCount", missingDateCount);
        metrics.put("dateGapCount", dateGapCount);
        if (start != null)
            metrics.put("startDate", start.toString());
        if (end != null)
            metrics.put("endDate", end.toString());
        return metrics;
    }

    private String salesValidationMessage(ObjectNode metrics) {
        String range = metrics.path("startDate").asText("-") + "至" + metrics.path("endDate").asText("-");
        return "历史用气数据已核验：有效" + metrics.path("validSalesRows").asInt() + "条，空值" + metrics.path("emptySalesCount").asInt() + "条；日期" + range + "，缺失" + metrics.path("missingDateCount").asLong() + "天，断档" + metrics.path("dateGapCount").asInt() + "处。";
    }

    /**
     * 当前预测链路尚未把外部市场指标拼入模型请求，不能把这一步描述为已获取到动态指标。 显式落库本次的实际处理方式，避免前端仅显示一句无法追溯的“处理完成”。
     */
    private ObjectNode dynamicIndicatorMetrics() {
        ObjectNode metrics = objectMapper.createObjectNode();
        metrics.put("dynamicIndicatorSource", "未接入外部市场指标");
        metrics.put("dynamicIndicatorCount", 0);
        metrics.put("featureSnapshot", "历史用气、日期周期、滞后滚动及天气特征");
        metrics.put("processingDescription", "本次没有可用的外部动态指标输入；模型将在训练阶段基于已准备的特征快照完成特征处理和模型筛选。");
        return metrics;
    }

    private String weatherFeatureLabel(String name) {
        return switch (name) {
            case "max_temp" -> "最高温";
            case "min_temp" -> "最低温";
            case "avg_temp" -> "平均温度";
            case "feels_like_max" -> "最高体感温度";
            case "feels_like_min" -> "最低体感温度";
            case "feels_like_avg" -> "平均体感温度";
            case "dew_point" -> "露点温度";
            case "humidity" -> "湿度";
            case "precipitation" -> "降水量";
            case "wind_gust" -> "阵风风速";
            case "wind_speed" -> "平均风速";
            case "sea_level_pressure" -> "海平面气压";
            case "cloud_cover" -> "云量";
            case "visibility" -> "能见度";
            case "solar_radiation" -> "太阳辐射";
            case "solar_energy" -> "太阳能量";
            case "uv_index" -> "紫外线指数";
            case "hdd" -> "采暖度日";
            default -> null;
        };
    }
    private String textOf(JsonNode jsonNode, String fieldName) {
        String textValue = jsonNode.path(fieldName).asText(null);
        return TextUtils.hasText(textValue) ? textValue : null;
    }
    private BigDecimal decimalOf(JsonNode jsonNode, String fieldName) {
        return jsonNode.path(fieldName).isNumber() ? jsonNode.path(fieldName).decimalValue() : null;
    }

    /**
     * 预测完成后给会话卡片留下一段短、可追溯的结果研判。 不虚构外部业务原因，只依据模型实际回测指标与未来预测点生成结论。
     */
    private String buildForecastAnalysisMetrics(ModelExecuteRecordTb modelExecuteRecord, JsonNode forecastExecutionResult) {
        JsonNode backtestPoints = forecastExecutionResult.path("backtest").path("points");
        JsonNode forecastPoints = forecastExecutionResult.path("forecast").path("points");
        int backtestDays = backtestPoints.isArray() ? backtestPoints.size() : 0;
        int forecastDays = forecastPoints.isArray() ? forecastPoints.size() : 0;
        BigDecimal meanAbsolutePercentageError = modelExecuteRecord.getMape();
        String accuracyAssessment = meanAbsolutePercentageError == null ? "回测指标未返回，暂不对精度作判断" : meanAbsolutePercentageError.compareTo(BigDecimal.TEN) <= 0 ? "回测精度较好，可作为近期用气趋势参考" : meanAbsolutePercentageError.compareTo(BigDecimal.valueOf(20)) <= 0 ? "回测精度可用，建议结合实际业务变化持续跟踪" : "回测误差偏高，建议仅作为用气趋势参考，并结合实际用气持续校正";
        StringBuilder analysisContent = new StringBuilder("本次预测采用").append(textOf(modelExecuteRecord.getBestModel(), "默认模型"));
        if (backtestDays > 0)
            analysisContent.append("，基于近").append(backtestDays).append("天回测");
        if (meanAbsolutePercentageError != null)
            analysisContent.append("，MAPE 为").append(formatNumber(meanAbsolutePercentageError)).append("%");
        analysisContent.append("。 ").append(accuracyAssessment).append("。");
        if (forecastDays > 0) {
            double totalForecastValue = 0D;
            for (JsonNode forecastPoint : forecastPoints)
                totalForecastValue += forecastPoint.path("prediction").asDouble(forecastPoint.path("forecast_value").asDouble());
            analysisContent.append("未来").append(forecastDays).append("天预测均值为").append(String.format(Locale.ROOT, "%.2f", totalForecastValue / forecastDays)).append("。");
        }
        modelExecuteRecord.setAnalysisContent(analysisContent.toString());
        ObjectNode forecastAnalysisMetrics = objectMapper.createObjectNode();
        forecastAnalysisMetrics.put("analysis", analysisContent.toString());
        forecastAnalysisMetrics.put("model", textOf(modelExecuteRecord.getBestModel(), "默认模型"));
        forecastAnalysisMetrics.put("backtestDays", backtestDays);
        forecastAnalysisMetrics.put("forecastDays", forecastDays);
        if (meanAbsolutePercentageError != null)
            forecastAnalysisMetrics.put("mape", meanAbsolutePercentageError);
        return forecastAnalysisMetrics.toString();
    }

    /**
     * 用同一份预测快照补充面向业务人员的长解读与推荐追问；模型异常绝不能让预测任务失败。 规则研判已在调用前写入
     * analysisContent，因此失败时数据库仍有可追溯的基础结论。
     */
    private void enrichForecastAnalysisWithAi(ModelExecuteRecordTb record, JsonNode forecastExecutionResult) {
        try {
            ObjectNode context = objectMapper.createObjectNode();
            context.put("forecastNo", record.getForecastNo());
            context.put("regionName", textOf(record.getRegionName(), "未提供"));
            context.put("industryName", textOf(record.getIndustryName(), "未提供"));
            context.put("customerName", textOf(record.getCustomerName(), "未提供"));
            context.put("forecastHorizon", record.getForecastHorizon() == null ? 0 : record.getForecastHorizon());
            context.put("bestModel", textOf(record.getBestModel(), "未提供"));
            if (record.getMape() != null)
                context.put("mape", record.getMape());
            if (record.getWmape() != null)
                context.put("wmape", record.getWmape());
            context.put("ruleAnalysis", record.getAnalysisContent());
            context.set("backtest", forecastExecutionResult.path("backtest"));
            context.set("forecast", forecastExecutionResult.path("forecast"));
            PythonForecastAnalysisResponse generated = pythonAgentChatClient.forecastAnalysis(new PythonForecastAnalysisRequest(record.getForecastNo(), context));
            record.setAnalysisContent(generated.analysis().trim());
            record.setAnalysisQuestionsJson(objectMapper.writeValueAsString(generated.questions()));
        } catch (Exception exception) {
            log.warn("预测AI解读未生成，保留规则研判 forecastNo={} cause={}", record.getForecastNo(), exception.getMessage());
        }
    }

    private String textOf(String textValue, String fallbackValue) {
        return TextUtils.hasText(textValue) ? textValue : fallbackValue;
    }
    private String formatNumber(BigDecimal decimalValue) {
        return String.format(Locale.ROOT, "%.2f", decimalValue);
    }
}
