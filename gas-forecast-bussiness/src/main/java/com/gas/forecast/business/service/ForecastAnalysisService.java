package com.gas.forecast.business.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gas.forecast.business.component.ForecastAnalysisLlmClient;
import com.gas.forecast.business.dto.ForecastAnalysisContext;
import com.gas.forecast.business.dto.response.ModelForecastAnalysisResponse;
import com.gas.forecast.business.enums.ModelForecastStatus;
import com.gas.forecast.business.enums.ModelTrainStatus;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.ModelForecastAnalysisTb;
import com.gas.forecast.dao.domain.ModelForecastRecordTb;
import com.gas.forecast.dao.domain.ModelForecastResultTb;
import com.gas.forecast.dao.domain.ModelTrainBacktestTb;
import com.gas.forecast.dao.domain.ModelTrainRecordTb;
import com.gas.forecast.dao.mapper.ModelForecastAnalysisTbMapper;
import com.gas.forecast.dao.mapper.ModelForecastRecordTbMapper;
import com.gas.forecast.dao.mapper.ModelForecastResultTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainBacktestTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainRecordTbMapper;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 预测完成后的异步大模型分析。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForecastAnalysisService {
    private static final String PENDING = "PENDING";
    private static final String RUNNING = "RUNNING";
    private static final String SUCCESS = "SUCCESS";
    private static final String FAILED = "FAILED";

    private final ModelForecastAnalysisTbMapper analysisMapper;
    private final ModelForecastRecordTbMapper recordMapper;
    private final ModelForecastResultTbMapper resultMapper;
    private final ModelTrainRecordTbMapper trainRecordMapper;
    private final ModelTrainBacktestTbMapper trainBacktestMapper;
    private final ForecastAnalysisLlmClient llmClient;
    private final ObjectMapper objectMapper;
    /** 字段名与 Bean 名一致，Spring 在多个 Executor 中按名称选择。 */
    private final Executor forecastAnalysisTaskExecutor;

    /** 预测请求返回前只入队，不等待大模型。 */
    public void submit(String forecastBatchNo) {
        submit(forecastBatchNo, false);
    }

    public void submit(String forecastBatchNo, boolean force) {
        if (!TextUtils.hasText(forecastBatchNo))
            return;
        ModelForecastAnalysisTb analysis = find(forecastBatchNo, false);
        Date now = new Date();
        if (analysis == null) {
            analysis = new ModelForecastAnalysisTb();
            analysis.setForecastBatchNo(forecastBatchNo);
            analysis.setAnalysisStatus(PENDING);
            analysis.setCreateTime(now);
            analysis.setUpdateTime(now);
            analysisMapper.insert(analysis);
        } else {
            if (!force && (PENDING.equals(analysis.getAnalysisStatus()) || RUNNING.equals(analysis.getAnalysisStatus()) || SUCCESS.equals(analysis.getAnalysisStatus())))
                return;
            analysis.setAnalysisStatus(PENDING);
            analysis.setErrorMessage(null);
            analysis.setUpdateTime(now);
            analysisMapper.update(null, Wrappers.<ModelForecastAnalysisTb>lambdaUpdate().eq(ModelForecastAnalysisTb::getId, analysis.getId()).set(ModelForecastAnalysisTb::getAnalysisStatus, PENDING)
                    .set(ModelForecastAnalysisTb::getAnalysisContent, null).set(ModelForecastAnalysisTb::getErrorMessage, null).set(ModelForecastAnalysisTb::getUpdateTime, now));
        }
        forecastAnalysisTaskExecutor.execute(() -> generate(forecastBatchNo));
    }

    public ModelForecastAnalysisResponse get(String forecastBatchNo) {
        if (!TextUtils.hasText(forecastBatchNo))
            throw new BusinessException("预测批次号不能为空");
        ModelForecastAnalysisTb entity = find(forecastBatchNo, true);
        if (entity == null)
            return null;
        String content = entity.getAnalysisContent() == null ? null : new String(entity.getAnalysisContent(), StandardCharsets.UTF_8);
        return new ModelForecastAnalysisResponse(entity.getId(), entity.getForecastBatchNo(), content, entity.getLlmModel(), entity.getAnalysisStatus(), entity.getErrorMessage(),
                entity.getCreateTime(), entity.getUpdateTime());
    }

    public void retry(String forecastBatchNo) {
        ModelForecastRecordTb record = recordMapper.selectOne(Wrappers.<ModelForecastRecordTb>lambdaQuery().eq(ModelForecastRecordTb::getForecastBatchNo, forecastBatchNo).last("limit 1"));
        if (record == null || record.getStatus() == null || record.getStatus() != ModelForecastStatus.SUCCESS.getCode())
            throw new BusinessException("仅预测成功的批次可以生成分析");
        ModelForecastAnalysisTb existing = find(forecastBatchNo, false);
        if (existing != null && (PENDING.equals(existing.getAnalysisStatus()) || RUNNING.equals(existing.getAnalysisStatus())))
            throw new BusinessException("分析任务正在执行，请勿重复提交");
        submit(forecastBatchNo, true);
    }

    private void generate(String forecastBatchNo) {
        ModelForecastAnalysisTb analysis = find(forecastBatchNo, false);
        if (analysis == null)
            return;
        try {
            int claimed = analysisMapper.update(null,
                    Wrappers.<ModelForecastAnalysisTb>lambdaUpdate().eq(ModelForecastAnalysisTb::getId, analysis.getId()).eq(ModelForecastAnalysisTb::getAnalysisStatus, PENDING)
                            .set(ModelForecastAnalysisTb::getAnalysisStatus, RUNNING).set(ModelForecastAnalysisTb::getLlmModel, llmClient.model()).set(ModelForecastAnalysisTb::getErrorMessage, null)
                            .set(ModelForecastAnalysisTb::getUpdateTime, new Date()));
            if (claimed != 1)
                return;
            analysis.setAnalysisStatus(RUNNING);
            analysis.setLlmModel(llmClient.model());

            PromptData promptData = buildPrompt(forecastBatchNo);
            String content = llmClient.analyze(promptData.agentCode(), promptData.prompt());
            analysis.setAnalysisContent(content.getBytes(StandardCharsets.UTF_8));
            analysis.setAnalysisStatus(SUCCESS);
            analysis.setLlmModel(llmClient.model());
            analysis.setErrorMessage(null);
        } catch (Exception exception) {
            log.error("预测批次 {} 的大模型分析失败", forecastBatchNo, exception);
            analysis.setAnalysisStatus(FAILED);
            analysis.setErrorMessage(abbreviate(exception.getMessage(), 500));
        }
        analysis.setUpdateTime(new Date());
        analysisMapper.updateById(analysis);
    }

    private PromptData buildPrompt(String forecastBatchNo) throws Exception {
        ModelForecastRecordTb record = recordMapper.selectOne(Wrappers.<ModelForecastRecordTb>lambdaQuery()
                .select(ModelForecastRecordTb::getId, ModelForecastRecordTb::getForecastBatchNo, ModelForecastRecordTb::getAgentCode, ModelForecastRecordTb::getScopeType,
                        ModelForecastRecordTb::getRegionCode, ModelForecastRecordTb::getRegionName, ModelForecastRecordTb::getIndustryCode, ModelForecastRecordTb::getIndustryName,
                        ModelForecastRecordTb::getCustomerCode, ModelForecastRecordTb::getCustomerName, ModelForecastRecordTb::getForecastStartDate, ModelForecastRecordTb::getForecastHorizon,
                        ModelForecastRecordTb::getForecastFrequency, ModelForecastRecordTb::getFeatureSnapshot, ModelForecastRecordTb::getRequestParam)
                .eq(ModelForecastRecordTb::getForecastBatchNo, forecastBatchNo).last("limit 1"));
        if (record == null)
            throw new IllegalStateException("预测记录不存在：" + forecastBatchNo);
        List<ModelForecastResultTb> points = resultMapper.selectList(Wrappers.<ModelForecastResultTb>lambdaQuery().eq(ModelForecastResultTb::getForecastBatchNo, forecastBatchNo)
                .orderByAsc(ModelForecastResultTb::getForecastDate).orderByAsc(ModelForecastResultTb::getId));
        if (points.isEmpty())
            throw new IllegalStateException("预测结果不存在：" + forecastBatchNo);

        ForecastAnalysisContext context = buildAnalysisContext(record, points);
        String prompt = "请严格依据以下 JSON 简要分析天然气预测结果。只回答两项：1.整体趋势是上升、下降还是基本平稳；" + "2.造成该走势的主要原因。全文控制在2至3句话、120个汉字以内，不罗列全部指标，不输出长篇风险和业务建议。" + "趋势需要依据预测序列及历史数据；原因只能引用 JSON 中已有的历史特征、未来特征、训练或回测数据。"
                + "不得补充 JSON 中不存在的天气、供给、价格、库存或业务事实；证据不足时直接说明暂不能判断外部原因。\n\n" + objectMapper.writeValueAsString(context);
        return new PromptData(record.getAgentCode(), prompt);
    }

    private ForecastAnalysisContext buildAnalysisContext(ModelForecastRecordTb record, List<ModelForecastResultTb> results) throws Exception {
        JsonNode forecastRequest = parseBytes(record.getRequestParam());
        String trainBatchNo = text(forecastRequest, "train_batch_no");
        ModelTrainRecordTb trainRecord = loadTrainRecord(record, trainBatchNo);
        JsonNode trainRequest = trainRecord == null ? null : parseBytes(trainRecord.getRequestParam());
        JsonNode trainResult = trainRecord == null ? null : parseJson(trainRecord.getResultJson());

        var forecastInfo = new ForecastAnalysisContext.ForecastInfo(record.getForecastBatchNo(), record.getAgentCode(), record.getScopeType(), record.getRegionCode(), record.getRegionName(),
                record.getIndustryCode(), record.getIndustryName(), record.getCustomerCode(), record.getCustomerName(), record.getForecastStartDate(), record.getForecastHorizon(),
                record.getForecastFrequency());
        var trainingInfo = trainingInfo(trainRecord, trainRequest, trainResult);
        var historicalFeatures = loadHistoricalFeatures(record, trainRecord);
        var trainingData = datasetSection("model_train_record_tb.request_param.dataset", trainRequest == null ? null : trainRequest.path("dataset"), 60);
        JsonNode futureRows = parseBytes(record.getFeatureSnapshot());
        if (futureRows == null && forecastRequest != null)
            futureRows = forecastRequest.path("dataset");
        var futureFeatures = datasetSection("model_forecast_record_tb.feature_snapshot", futureRows, 120);
        var backtests = loadBacktests(trainRecord);
        var forecastPoints = results.stream().map(point -> new ForecastAnalysisContext.ForecastPoint(point.getForecastDate(), point.getForecastValue())).toList();
        return new ForecastAnalysisContext(forecastInfo, trainingInfo, historicalFeatures, trainingData, futureFeatures, backtests, forecastPoints);
    }

    private ForecastAnalysisContext.TrainingInfo trainingInfo(ModelTrainRecordTb record, JsonNode request, JsonNode result) {
        if (record == null)
            return null;
        JsonNode metrics = result == null ? null : result.path("metrics");
        if (metrics == null || metrics.isMissingNode() || metrics.isNull() || metrics.isEmpty()) {
            ObjectNode fallback = objectMapper.createObjectNode();
            putDecimal(fallback, "mape", record.getMape());
            putDecimal(fallback, "wmape", record.getWmape());
            putDecimal(fallback, "smape", record.getSmape());
            putDecimal(fallback, "rmse", record.getRmse());
            putDecimal(fallback, "mae", record.getMae());
            putDecimal(fallback, "r2", record.getR2());
            metrics = fallback;
        }
        String selectedModel = text(result, "selected_model_name");
        JsonNode selectedParams = result == null ? null : emptyToNull(result.path("selected_model_params"));
        return new ForecastAnalysisContext.TrainingInfo(record.getBatchNo(), text(request, "model_code"), TextUtils.hasText(selectedModel) ? selectedModel : record.getBestModel(),
                record.getModelVersion(), record.getTrainStartDate(), record.getTrainEndDate(), metrics, selectedParams, text(result, "selection_reason"));
    }

    private ModelTrainRecordTb loadTrainRecord(ModelForecastRecordTb forecast, String trainBatchNo) {
        var query = Wrappers.<ModelTrainRecordTb>lambdaQuery();
        if (TextUtils.hasText(trainBatchNo)) {
            query.eq(ModelTrainRecordTb::getBatchNo, trainBatchNo);
        } else {
            query.eq(ModelTrainRecordTb::getAgentCode, forecast.getAgentCode()).eq(ModelTrainRecordTb::getStatus, ModelTrainStatus.SUCCESS.getCode());
            eqText(query, ModelTrainRecordTb::getRegionCode, forecast.getRegionCode());
            eqText(query, ModelTrainRecordTb::getIndustryCode, forecast.getIndustryCode());
            eqText(query, ModelTrainRecordTb::getCustomerCode, forecast.getCustomerCode());
            query.orderByDesc(ModelTrainRecordTb::getUpdatedAt).orderByDesc(ModelTrainRecordTb::getId);
        }
        return trainRecordMapper.selectOne(query.last("limit 1"));
    }

    private ForecastAnalysisContext.DatasetSection loadHistoricalFeatures(ModelForecastRecordTb forecast, ModelTrainRecordTb trainRecord) {
        int limit = historyLimit(forecast.getForecastFrequency());
        ArrayNode data = objectMapper.createArrayNode();
        if (trainRecord != null && trainRecord.getRequestParam() != null) {
            try {
                JsonNode dataset = objectMapper.readTree(trainRecord.getRequestParam()).path("dataset");
                List<JsonNode> rows = new ArrayList<>();
                if (dataset.isArray())
                    dataset.forEach(item -> {
                        if (!TextUtils.hasText(forecast.getForecastStartDate()) || item.path("date").asText().compareTo(forecast.getForecastStartDate()) < 0)
                            rows.add(item);
                    });
                rows.stream().skip(Math.max(0, rows.size() - limit)).forEach(item -> data.add(item.deepCopy()));
            } catch (Exception exception) {
                log.warn("读取训练请求数据快照失败 batchNo={}", trainRecord.getBatchNo(), exception);
            }
        }
        return new ForecastAnalysisContext.DatasetSection("训练批次数据快照最近历史窗口", data.size(), false, data);
    }

    private List<ForecastAnalysisContext.BacktestPoint> loadBacktests(ModelTrainRecordTb trainRecord) {
        if (trainRecord == null || !TextUtils.hasText(trainRecord.getBatchNo()))
            return Collections.emptyList();
        List<ModelTrainBacktestTb> rows = new ArrayList<>(trainBacktestMapper.selectList(Wrappers.<ModelTrainBacktestTb>lambdaQuery()
                .eq(ModelTrainBacktestTb::getTrainBatchNo, trainRecord.getBatchNo()).orderByDesc(ModelTrainBacktestTb::getTrainDate).orderByDesc(ModelTrainBacktestTb::getId).last("limit 30")));
        Collections.reverse(rows);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        return rows.stream().map(row -> new ForecastAnalysisContext.BacktestPoint(formatter.format(row.getTrainDate()), row.getActualValue(), row.getPredictedValue())).toList();
    }

    private ForecastAnalysisContext.DatasetSection datasetSection(String source, JsonNode rows, int limit) {
        if (rows == null || !rows.isArray())
            return new ForecastAnalysisContext.DatasetSection(source, 0, false, objectMapper.createArrayNode());
        int total = rows.size();
        if (total <= limit)
            return new ForecastAnalysisContext.DatasetSection(source, total, false, rows.deepCopy());
        ArrayNode sample = objectMapper.createArrayNode();
        int head = limit / 2;
        for (int index = 0; index < head; index++)
            sample.add(rows.get(index));
        for (int index = total - (limit - head); index < total; index++)
            sample.add(rows.get(index));
        return new ForecastAnalysisContext.DatasetSection(source, total, true, sample);
    }

    private ModelForecastAnalysisTb find(String forecastBatchNo, boolean includeContent) {
        var query = Wrappers.<ModelForecastAnalysisTb>lambdaQuery().eq(ModelForecastAnalysisTb::getForecastBatchNo, forecastBatchNo).last("limit 1");
        if (includeContent)
            query.select(ModelForecastAnalysisTb::getId, ModelForecastAnalysisTb::getForecastBatchNo, ModelForecastAnalysisTb::getAnalysisContent, ModelForecastAnalysisTb::getLlmModel,
                    ModelForecastAnalysisTb::getAnalysisStatus, ModelForecastAnalysisTb::getErrorMessage, ModelForecastAnalysisTb::getCreateTime, ModelForecastAnalysisTb::getUpdateTime);
        return analysisMapper.selectOne(query);
    }

    private JsonNode parseBytes(byte[] value) {
        if (value == null || value.length == 0)
            return null;
        try {
            return objectMapper.readTree(value);
        } catch (Exception exception) {
            log.warn("大模型分析上下文中的请求快照无法解析", exception);
            return null;
        }
    }

    private JsonNode parseJson(Object value) {
        if (value instanceof JsonNode node)
            return node;
        if (value == null || !TextUtils.hasText(value.toString()))
            return null;
        try {
            return objectMapper.readTree(value.toString());
        } catch (Exception exception) {
            log.warn("大模型分析上下文中的训练结果无法解析", exception);
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asText().trim() : null;
    }

    private JsonNode emptyToNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() || node.isEmpty() ? null : node;
    }

    private void putDecimal(ObjectNode node, String field, java.math.BigDecimal value) {
        if (value != null)
            node.put(field, value);
    }

    private int historyLimit(String frequency) {
        return switch (frequency == null ? "" : frequency.toUpperCase()) {
            case "MONTH", "MONTHLY" -> 24;
            case "TENDAY" -> 18;
            default -> 60;
        };
    }

    private String granularity(String frequency) {
        return switch (frequency == null ? "" : frequency.toUpperCase()) {
            case "MONTH", "MONTHLY" -> "MONTH";
            case "TENDAY" -> "TENDAY";
            default -> "DAY";
        };
    }

    private <T> void eqText(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<T> query, com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> field, String value) {
        if (TextUtils.hasText(value) && !"ALL".equalsIgnoreCase(value))
            query.eq(field, value);
    }

    private String abbreviate(String text, int maxLength) {
        String value = TextUtils.hasText(text) ? text : "未知错误";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private record PromptData(String agentCode, String prompt) {
    }
}
