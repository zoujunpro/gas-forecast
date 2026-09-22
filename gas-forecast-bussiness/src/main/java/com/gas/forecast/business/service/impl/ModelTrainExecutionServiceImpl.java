package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gas.forecast.business.dto.req.ModelTrainAgentTrainReqDTO;
import com.gas.forecast.business.dto.req.ModelTrainExecuteReqDTO;
import com.gas.forecast.business.dto.resp.ModelTrainAgentRespDTO;
import com.gas.forecast.business.dto.resp.ModelTrainExecuteRespDTO;
import com.gas.forecast.business.enums.BaseCodeType;
import com.gas.forecast.business.service.BaseCodeGenerateService;
import com.gas.forecast.business.service.ModelTrainExecutionService;
import com.gas.forecast.business.service.ModelPlatformService;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.util.HttpUtil;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.ModelConfigScopeTb;
import com.gas.forecast.dao.domain.ModelConfigTb;
import com.gas.forecast.dao.domain.ModelFeatureDefinitionTb;
import com.gas.forecast.dao.domain.ModelFeatureRef;
import com.gas.forecast.dao.domain.ModelTrainBacktestTb;
import com.gas.forecast.dao.domain.ModelTrainConfigTb;
import com.gas.forecast.dao.domain.ModelTrainRecordTb;
import com.gas.forecast.dao.domain.ModelTrainFeatureDataTb;
import com.gas.forecast.dao.mapper.ModelConfigScopeTbMapper;
import com.gas.forecast.dao.mapper.ModelConfigTbMapper;
import com.gas.forecast.dao.mapper.ModelFeatureDefinitionTbMapper;
import com.gas.forecast.dao.mapper.ModelFeatureRefMapper;
import com.gas.forecast.dao.mapper.ModelTrainBacktestTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainConfigTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainRecordTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainFeatureDataTbMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientResponseException;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Date;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ModelTrainExecutionServiceImpl implements ModelTrainExecutionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModelTrainExecutionServiceImpl.class);
    private static final DateTimeFormatter BATCH_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int RESULT_DATASET_PREVIEW_LIMIT = 100;

    private final ModelTrainConfigTbMapper modelTrainConfigTbMapper;
    private final ModelConfigTbMapper modelConfigTbMapper;
    private final ModelConfigScopeTbMapper modelConfigScopeTbMapper;
    private final ModelFeatureRefMapper modelFeatureRefMapper;
    private final ModelFeatureDefinitionTbMapper modelFeatureDefinitionTbMapper;
    private final ModelTrainFeatureDataTbMapper modelTrainFeatureDataTbMapper;
    private final ModelTrainBacktestTbMapper modelTrainBacktestTbMapper;
    private final ModelTrainRecordTbMapper modelTrainDetailTbMapper;
    private final BaseCodeGenerateService baseCodeGenerateService;
    private final ObjectMapper objectMapper;
    private final Executor modelTrainTaskExecutor;
    private final TransactionTemplate transactionTemplate;
    private final ModelPlatformService modelPlatformService;

    @Value("${gas.agent.train-url:http://127.0.0.1:8090/api/v1/train}")
    private String trainUrl;

    public ModelTrainExecutionServiceImpl(ModelTrainConfigTbMapper modelTrainConfigTbMapper,
                                          ModelConfigTbMapper modelConfigTbMapper,
                                          ModelConfigScopeTbMapper modelConfigScopeTbMapper,
                                          ModelFeatureRefMapper modelFeatureRefMapper,
                                          ModelFeatureDefinitionTbMapper modelFeatureDefinitionTbMapper,
                                          ModelTrainFeatureDataTbMapper modelTrainFeatureDataTbMapper,
                                          ModelTrainBacktestTbMapper modelTrainBacktestTbMapper,
                                          ModelTrainRecordTbMapper modelTrainDetailTbMapper,
                                          BaseCodeGenerateService baseCodeGenerateService,
                                          ObjectMapper objectMapper,
                                          @Qualifier("modelTrainTaskExecutor") Executor modelTrainTaskExecutor,
                                          TransactionTemplate transactionTemplate,
                                          ModelPlatformService modelPlatformService) {
        this.modelTrainConfigTbMapper = modelTrainConfigTbMapper;
        this.modelConfigTbMapper = modelConfigTbMapper;
        this.modelConfigScopeTbMapper = modelConfigScopeTbMapper;
        this.modelFeatureRefMapper = modelFeatureRefMapper;
        this.modelFeatureDefinitionTbMapper = modelFeatureDefinitionTbMapper;
        this.modelTrainFeatureDataTbMapper = modelTrainFeatureDataTbMapper;
        this.modelTrainBacktestTbMapper = modelTrainBacktestTbMapper;
        this.modelTrainDetailTbMapper = modelTrainDetailTbMapper;
        this.baseCodeGenerateService = baseCodeGenerateService;
        this.objectMapper = objectMapper;
        this.modelTrainTaskExecutor = modelTrainTaskExecutor;
        this.transactionTemplate = transactionTemplate;
        this.modelPlatformService = modelPlatformService;
    }

    @Override
    @Transactional
    public ModelTrainExecuteRespDTO execute(ModelTrainExecuteReqDTO reqDTO) {
        String trainCode = TextUtils.hasText(reqDTO.trainCode()) ? reqDTO.trainCode() : reqDTO.configCode();
        if (!TextUtils.hasText(trainCode)) {
            throw new BusinessException("训练配置编码不能为空");
        }
        ModelTrainConfigTb trainConfig = modelTrainConfigTbMapper.selectOne(Wrappers.<ModelTrainConfigTb>lambdaQuery()
                .eq(ModelTrainConfigTb::getTrainCode, trainCode));
        if (trainConfig == null) {
            throw new BusinessException("训练配置不存在");
        }
        if (trainConfig.getModelId() == null) {
            throw new BusinessException("训练配置未配置所属模型");
        }

        ModelConfigTb modelConfig = modelConfigTbMapper.selectById(trainConfig.getModelId());
        if (modelConfig == null) {
            throw new BusinessException("所属模型不存在");
        }

        List<FeatureMapping> features = loadFeatureMappings(modelConfig.getId());
        List<ModelTrainFeatureDataTb> trainData = loadTrainData(trainConfig, modelConfig, features);
        if (trainData.isEmpty()) {
            throw new BusinessException("训练时间范围和模型作用范围内没有可用训练数据");
        }

        ModelTrainAgentTrainReqDTO validationRequest = buildTrainRequest(trainConfig, modelConfig, null, trainData, features);
        modelPlatformService.validateTrainingData(objectMapper.valueToTree(validationRequest));

        String retryBatchNo = reqDTO.retryBatchNo();
        String batchNo = TextUtils.hasText(retryBatchNo) ? validateRetryBatch(retryBatchNo) : generateTrainBatchNo();
        ModelTrainAgentTrainReqDTO trainRequest = buildTrainRequest(trainConfig, modelConfig, batchNo, trainData, features);
        JsonNode requestPayload = objectMapper.valueToTree(trainRequest);
        if (TextUtils.hasText(retryBatchNo)) {
            resetFailedTrainDetail(trainConfig, batchNo, "PENDING", requestPayload);
        } else {
            saveTrainDetail(trainConfig, batchNo, "PENDING", requestPayload, null);
        }
        String status = TextUtils.hasText(trainUrl) ? "RUNNING" : "PENDING";
        ObjectNode submitResponse = objectMapper.createObjectNode()
                .put("message", TextUtils.hasText(trainUrl) ? "训练任务已提交，后台等待模型系统返回结果。" : "未配置 gas.agent.train-url，已生成训练请求入参。")
                .put("model_code", trainRequest.modelCode())
                .put("train_batch_no", batchNo);
        updateTrainDetailAfterSubmit(batchNo, status, submitResponse, null);
        submitTrainAfterCommit(batchNo, trainRequest);
        return new ModelTrainExecuteRespDTO(
                trainConfig.getTrainCode(),
                batchNo,
                trainRequest.modelCode(),
                trainData.size(),
                status,
                requestPayload,
                submitResponse
        );
    }

    @Override
    @Transactional(readOnly = true)
    public JsonNode validateTrainingData(ModelTrainExecuteReqDTO reqDTO) {
        String trainCode = TextUtils.hasText(reqDTO.trainCode()) ? reqDTO.trainCode() : reqDTO.configCode();
        if (!TextUtils.hasText(trainCode)) {
            throw new BusinessException("训练配置编码不能为空");
        }
        ModelTrainConfigTb trainConfig = modelTrainConfigTbMapper.selectOne(Wrappers.<ModelTrainConfigTb>lambdaQuery()
                .eq(ModelTrainConfigTb::getTrainCode, trainCode));
        if (trainConfig == null) {
            throw new BusinessException("训练配置不存在");
        }
        if (trainConfig.getModelId() == null) {
            throw new BusinessException("训练配置未配置所属模型");
        }
        ModelConfigTb modelConfig = modelConfigTbMapper.selectById(trainConfig.getModelId());
        if (modelConfig == null) {
            throw new BusinessException("所属模型不存在");
        }
        List<FeatureMapping> features = loadFeatureMappings(modelConfig.getId());
        List<ModelTrainFeatureDataTb> trainData = loadTrainData(trainConfig, modelConfig, features);
        if (trainData.isEmpty()) {
            throw new BusinessException("训练时间范围和模型作用范围内没有可用训练数据");
        }
        ModelTrainAgentTrainReqDTO validationRequest = buildTrainRequest(trainConfig, modelConfig, null, trainData, features);
        return modelPlatformService.validateTrainingData(objectMapper.valueToTree(validationRequest));
    }

    private String validateRetryBatch(String retryBatchNo) {
        ModelTrainRecordTb detail = modelTrainDetailTbMapper.selectOne(Wrappers.<ModelTrainRecordTb>lambdaQuery()
                .eq(ModelTrainRecordTb::getBatchNo, retryBatchNo));
        if (detail == null) {
            throw new BusinessException("重试训练批次不存在：" + retryBatchNo);
        }
        if (!"FAILED".equalsIgnoreCase(detail.getStatus())) {
            throw new BusinessException("只有失败的训练批次可以复用原批次号重试");
        }
        return retryBatchNo;
    }

    private String generateTrainBatchNo() {
        String sequence = baseCodeGenerateService.nextCode(BaseCodeType.TRAIN_BATCH);
        String sequenceNo = sequence.substring(BaseCodeType.TRAIN_BATCH.prefix().length());
        return BaseCodeType.TRAIN_BATCH.prefix() + LocalDate.now().format(BATCH_DATE_FORMATTER) + "-" + sequenceNo;
    }

    @Override
    @Transactional
    public ModelTrainExecuteRespDTO updateTrainResult(JsonNode reqDTO) {
        JsonNode trainResult = unwrapTrainResult(reqDTO);
        String batchNo = firstText(trainResult, "batchNo", "trainBatchNo", "train_batch_no", "batch_no");
        if (!TextUtils.hasText(batchNo)) {
            throw new BusinessException("训练批次号不能为空");
        }
        ModelTrainRecordTb detail = modelTrainDetailTbMapper.selectOne(Wrappers.<ModelTrainRecordTb>lambdaQuery()
                .eq(ModelTrainRecordTb::getBatchNo, batchNo));
        if (detail == null) {
            throw new BusinessException("训练批次不存在：" + batchNo);
        }

        String status = firstText(trainResult, "status", "trainStatus", "train_status");
        if (!TextUtils.hasText(status)) {
            status = hasFailure(trainResult) ? "FAILED" : "SUCCESS";
        }
        status = normalizeStatus(status);
        updateTrainDetail(trainResult, batchNo, status, null);
        replaceBacktestDetails(batchNo, trainResult);

        JsonNode requestPayload = parseRequestPayload(detail.getRequestParam());
        return new ModelTrainExecuteRespDTO(
                null,
                batchNo,
                firstText(requestPayload, "modelCode", "model_code"),
                datasetSize(requestPayload),
                status,
                requestPayload,
                trainResult
        );
    }

    @Override
    public JsonNode getTrainResult(JsonNode reqDTO) {
        var query = Wrappers.<ModelTrainRecordTb>lambdaQuery();
        String batchNo = firstText(reqDTO, "batchNo", "batch_no", "trainBatchNo", "train_batch_no");
        boolean loadDetail = TextUtils.hasText(batchNo);
        if (loadDetail) {
            query.eq(ModelTrainRecordTb::getBatchNo, batchNo);
        } else {
            // The request/result columns can contain the complete training dataset. Do not
            // read them for the batch list; the selected batch is loaded on demand below.
            query.select(
                    ModelTrainRecordTb::getId,
                    ModelTrainRecordTb::getBatchNo,
                    ModelTrainRecordTb::getAgentCode,
                    ModelTrainRecordTb::getRegionCode,
                    ModelTrainRecordTb::getRegionName,
                    ModelTrainRecordTb::getIndustryCode,
                    ModelTrainRecordTb::getIndustryName,
                    ModelTrainRecordTb::getCustomerCode,
                    ModelTrainRecordTb::getCustomerName,
                    ModelTrainRecordTb::getTrainStartDate,
                    ModelTrainRecordTb::getTrainEndDate,
                    ModelTrainRecordTb::getStatus,
                    ModelTrainRecordTb::getBestModel,
                    ModelTrainRecordTb::getMape,
                    ModelTrainRecordTb::getWmape,
                    ModelTrainRecordTb::getSmape,
                    ModelTrainRecordTb::getRmse,
                    ModelTrainRecordTb::getMae,
                    ModelTrainRecordTb::getR2,
                    ModelTrainRecordTb::getTrainDurationSeconds,
                    ModelTrainRecordTb::getModelVersion,
                    ModelTrainRecordTb::getErrorMessage,
                    ModelTrainRecordTb::getCreatedAt,
                    ModelTrainRecordTb::getUpdatedAt);
        }
        eqIfText(query, ModelTrainRecordTb::getAgentCode, firstText(reqDTO, "agentCode", "agent_code"));
        eqIfText(query, ModelTrainRecordTb::getRegionCode, firstText(reqDTO, "regionCode", "region_code"));
        eqIfText(query, ModelTrainRecordTb::getIndustryCode, firstText(reqDTO, "industryCode", "industry_code"));
        eqIfText(query, ModelTrainRecordTb::getCustomerCode, firstText(reqDTO, "customerCode", "customer_code"));
        eqIfText(query, ModelTrainRecordTb::getTrainStartDate, firstText(reqDTO, "trainStartDate", "train_start_date"));
        eqIfText(query, ModelTrainRecordTb::getTrainEndDate, firstText(reqDTO, "trainEndDate", "train_end_date"));
        query.orderByDesc(ModelTrainRecordTb::getUpdatedAt).orderByDesc(ModelTrainRecordTb::getId)
                .last(loadDetail ? "limit 1" : "limit 20");
        List<ModelTrainRecordTb> details = modelTrainDetailTbMapper.selectList(query);

        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode detailNodes = objectMapper.createArrayNode();
        for (ModelTrainRecordTb detail : details) {
            ObjectNode item = objectMapper.createObjectNode();
            item.put("id", detail.getId());
            item.put("batchNo", detail.getBatchNo());
            item.put("agentCode", detail.getAgentCode());
            item.put("agentName", agentName(detail.getAgentCode()));
            item.put("regionName", detail.getRegionName());
            item.put("industryName", detail.getIndustryName());
            item.put("customerName", detail.getCustomerName());
            item.put("trainStartDate", detail.getTrainStartDate());
            item.put("trainEndDate", detail.getTrainEndDate());
            item.put("status", detail.getStatus());
            item.put("bestModel", detail.getBestModel());
            putDecimal(item, "mape", detail.getMape());
            putDecimal(item, "wmape", detail.getWmape());
            putDecimal(item, "smape", detail.getSmape());
            putDecimal(item, "rmse", detail.getRmse());
            putDecimal(item, "mae", detail.getMae());
            putDecimal(item, "r2", detail.getR2());
            putDecimal(item, "trainDurationSeconds", detail.getTrainDurationSeconds());
            item.put("modelVersion", detail.getModelVersion());
            item.put("errorMessage", detail.getErrorMessage());
            if (TextUtils.hasText(detail.getErrorMessage())) {
                item.put("message", detail.getErrorMessage());
            }
            putDate(item, "createdAt", detail.getCreatedAt());
            putDate(item, "updatedAt", detail.getUpdatedAt());
            if (loadDetail) {
                JsonNode requestJson = parseRequestPayload(detail.getRequestParam());
                if (requestJson != null) {
                    item.set("requestJson", previewRequestPayload(requestJson));
                }
                JsonNode node = parseJsonValue(detail.getResultJson());
                if (node != null) {
                    item.set("resultJson", node);
                }
            }
            detailNodes.add(item);
        }
        result.set("details", detailNodes);
        result.set("batches", detailNodes);
        if (!details.isEmpty()) {
            result.put("selectedBatchNo", details.get(0).getBatchNo());
        }
        return result;
    }

    private JsonNode previewRequestPayload(JsonNode requestJson) {
        if (!requestJson.isObject()) {
            return requestJson;
        }
        JsonNode dataset = requestJson.get("dataset");
        if (dataset == null || !dataset.isArray() || dataset.size() <= RESULT_DATASET_PREVIEW_LIMIT) {
            return requestJson;
        }
        ObjectNode preview = objectMapper.createObjectNode();
        requestJson.fields().forEachRemaining(entry -> {
            if (!"dataset".equals(entry.getKey())) {
                preview.set(entry.getKey(), entry.getValue());
            }
        });
        ArrayNode rows = objectMapper.createArrayNode();
        for (int index = 0; index < RESULT_DATASET_PREVIEW_LIMIT; index++) {
            rows.add(dataset.get(index));
        }
        preview.set("dataset", rows);
        preview.put("dataset_total", dataset.size());
        preview.put("dataset_preview_limit", RESULT_DATASET_PREVIEW_LIMIT);
        preview.put("dataset_truncated", true);
        return preview;
    }

    private List<FeatureMapping> loadFeatureMappings(Long modelId) {
        List<ModelFeatureRef> refs = modelFeatureRefMapper.selectList(Wrappers.<ModelFeatureRef>lambdaQuery()
                .eq(ModelFeatureRef::getModelId, modelId)
                .orderByAsc(ModelFeatureRef::getFeatureOrder)
                .orderByAsc(ModelFeatureRef::getId));
        if (refs.isEmpty()) {
            throw new BusinessException("所属模型未配置训练特征");
        }
        List<Long> featureIds = refs.stream().map(ModelFeatureRef::getFeatureId).filter(id -> id != null).distinct().toList();
        Map<Long, ModelFeatureDefinitionTb> definitions = modelFeatureDefinitionTbMapper.selectBatchIds(featureIds).stream()
                .filter(item -> item.getEnabled() == null || item.getEnabled() == 1)
                .collect(Collectors.toMap(ModelFeatureDefinitionTb::getId, Function.identity(), (left, right) -> left));
        List<FeatureMapping> mappings = refs.stream()
                .map(ref -> definitions.get(ref.getFeatureId()))
                .filter(definition -> definition != null && TextUtils.hasText(definition.getFeatureCode())
                        && TextUtils.hasText(definition.getFeatureColumn()))
                .map(definition -> new FeatureMapping(normalizeFeatureKey(definition.getFeatureCode()), definition.getFeatureColumn(),
                        definition.getTimeGranularity()))
                .toList();
        if (mappings.isEmpty()) {
            throw new BusinessException("所属模型没有可用训练特征");
        }
        return mappings;
    }

    private List<ModelTrainFeatureDataTb> loadTrainData(ModelTrainConfigTb trainConfig,
                                                        ModelConfigTb modelConfig,
                                                        List<FeatureMapping> features) {
        var query = Wrappers.<ModelTrainFeatureDataTb>lambdaQuery();
        String timeGranularity = TextUtils.hasText(trainConfig.getTimeGranularity())
                ? trainConfig.getTimeGranularity()
                : resolveTimeGranularity(features);
        if (TextUtils.hasText(timeGranularity)) {
            query.eq(ModelTrainFeatureDataTb::getTimeGranularity, timeGranularity);
        }
        boolean recentMode = "RECENT".equals(trainConfig.getTrainMode());
        if (!recentMode && TextUtils.hasText(trainConfig.getTrainStartDate())) {
            query.ge(ModelTrainFeatureDataTb::getStatDate, trainConfig.getTrainStartDate());
        }
        if (!recentMode && TextUtils.hasText(trainConfig.getTrainEndDate())) {
            query.le(ModelTrainFeatureDataTb::getStatDate, trainConfig.getTrainEndDate());
        }
        applyTrainScope(query, trainConfig, modelConfig.getId());
        if (recentMode) {
            int recentPeriods = trainConfig.getRecentPeriods() == null ? 36 : trainConfig.getRecentPeriods();
            query.orderByDesc(ModelTrainFeatureDataTb::getStatDate).orderByDesc(ModelTrainFeatureDataTb::getId)
                    .last("limit " + Math.max(recentPeriods, 1));
            return modelTrainFeatureDataTbMapper.selectList(query).stream()
                    .sorted(Comparator.comparing(ModelTrainFeatureDataTb::getStatDate).thenComparing(ModelTrainFeatureDataTb::getId))
                    .toList();
        }
        query.orderByAsc(ModelTrainFeatureDataTb::getStatDate).orderByAsc(ModelTrainFeatureDataTb::getId);
        return modelTrainFeatureDataTbMapper.selectList(query);
    }

    private void applyTrainScope(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ModelTrainFeatureDataTb> query,
                                 ModelTrainConfigTb trainConfig,
                                 Long modelId) {
        if (TextUtils.hasText(trainConfig.getRegionCode())) {
            query.eq(ModelTrainFeatureDataTb::getRegionCode, trainConfig.getRegionCode());
        }
        if (TextUtils.hasText(trainConfig.getIndustryCode())) {
            query.eq(ModelTrainFeatureDataTb::getIndustryCode, trainConfig.getIndustryCode());
        }
        if (TextUtils.hasText(trainConfig.getCustomerCode())) {
            query.eq(ModelTrainFeatureDataTb::getCustomerCode, trainConfig.getCustomerCode());
        }
        if (TextUtils.hasText(trainConfig.getRegionCode())
                || TextUtils.hasText(trainConfig.getIndustryCode())
                || TextUtils.hasText(trainConfig.getCustomerCode())) {
            return;
        }

        List<ModelConfigScopeTb> scopes = modelConfigScopeTbMapper.selectList(Wrappers.<ModelConfigScopeTb>lambdaQuery()
                .eq(ModelConfigScopeTb::getModelId, modelId));
        List<String> regionCodes = scopes.stream().map(ModelConfigScopeTb::getRegionCode).filter(TextUtils::hasText).distinct().toList();
        List<String> industryCodes = scopes.stream().map(ModelConfigScopeTb::getIndustryCode).filter(TextUtils::hasText).distinct().toList();
        List<String> customerCodes = scopes.stream().map(ModelConfigScopeTb::getCustomerCode).filter(TextUtils::hasText).distinct().toList();
        if (!regionCodes.isEmpty()) {
            query.in(ModelTrainFeatureDataTb::getRegionCode, regionCodes);
        }
        if (!industryCodes.isEmpty()) {
            query.in(ModelTrainFeatureDataTb::getIndustryCode, industryCodes);
        }
        if (!customerCodes.isEmpty()) {
            query.in(ModelTrainFeatureDataTb::getCustomerCode, customerCodes);
        }
    }

    private ModelTrainAgentTrainReqDTO buildTrainRequest(ModelTrainConfigTb trainConfig,
                                                         ModelConfigTb modelConfig,
                                                         String batchNo,
                                                         List<ModelTrainFeatureDataTb> trainData,
                                                         List<FeatureMapping> features) {
        List<JsonNode> dataset = new ArrayList<>();
        for (ModelTrainFeatureDataTb row : trainData) {
            ObjectNode item = objectMapper.createObjectNode();
            item.put("date", row.getStatDate());
            putDecimal(item, "gas_sales", row.getGasSales());
            for (FeatureMapping feature : features) {
                Double value = readFeatureValue(row, feature.featureColumn());
                if (value != null) {
                    item.put(feature.payloadKey(), value);
                }
            }
            dataset.add(item);
        }
        return new ModelTrainAgentTrainReqDTO(
                trainConfig.getAgentCode(),
                modelConfig.getModelCode(),
                batchNo,
                trainConfig.getRegionCode(),
                trainConfig.getRegionName(),
                trainConfig.getIndustryCode(),
                trainConfig.getIndustryName(),
                trainConfig.getCustomerCode(),
                trainConfig.getCustomerName(),
                objectMapper.createObjectNode(),
                dataset
        );
    }

    private JsonNode callTrainAgent(ModelTrainAgentTrainReqDTO trainRequest) {
        if (!TextUtils.hasText(trainUrl)) {
            return null;
        }
        return HttpUtil.postJson(trainUrl, objectMapper.valueToTree(trainRequest));
    }

    private void submitTrainAfterCommit(String batchNo, ModelTrainAgentTrainReqDTO trainRequest) {
        if (!TextUtils.hasText(trainUrl)) {
            return;
        }
        Runnable submitTask = () -> modelTrainTaskExecutor.execute(() -> waitTrainResultAndPersist(batchNo, trainRequest));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submitTask.run();
                }
            });
            return;
        }
        submitTask.run();
    }

    private void waitTrainResultAndPersist(String batchNo, ModelTrainAgentTrainReqDTO trainRequest) {
        try {
            JsonNode agentResponse = callTrainAgent(trainRequest);
            JsonNode trainResult = normalizeTrainResult(batchNo, agentResponse);
            transactionTemplate.executeWithoutResult(status -> updateTrainResult(trainResult));
        } catch (RuntimeException e) {
            LOGGER.error("model train failed batchNo={}", batchNo, e);
            String message = extractTrainErrorMessage(e);
            ObjectNode failedResult = objectMapper.createObjectNode()
                    .put("train_batch_no", batchNo)
                    .put("status", "FAILED")
                    .put("message", message);
            transactionTemplate.executeWithoutResult(status ->
                    updateTrainDetailAfterSubmit(batchNo, "FAILED", failedResult, message));
        }
    }

    private String extractTrainErrorMessage(RuntimeException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof RestClientResponseException responseException) {
                String responseBody = responseException.getResponseBodyAsString();
                if (TextUtils.hasText(responseBody)) {
                    try {
                        JsonNode errorResponse = objectMapper.readTree(responseBody);
                        String message = firstText(errorResponse, "message");
                        if (TextUtils.hasText(message)) {
                            return message;
                        }
                    } catch (Exception parseException) {
                        LOGGER.warn("model train error response is not valid JSON body={}", responseBody);
                    }
                }
            }
            current = current.getCause();
        }
        return TextUtils.hasText(exception.getMessage()) ? exception.getMessage() : "模型训练失败";
    }

    private JsonNode normalizeTrainResult(String batchNo, JsonNode agentResponse) {
        ModelTrainAgentRespDTO response = parseAgentResponse(agentResponse);
        ObjectNode result;
        if (response != null && response.data() != null) {
            result = objectMapper.valueToTree(response.data());
        } else {
            JsonNode data = agentResponse == null ? null : firstNode(agentResponse, "data", "result");
            if (data != null && data.isObject()) {
                result = data.deepCopy();
            } else if (agentResponse != null && agentResponse.isObject()) {
                result = agentResponse.deepCopy();
            } else {
                result = objectMapper.createObjectNode();
            }
        }
        if (response != null && response.code() != null && response.code() != 0
                && !TextUtils.hasText(firstText(result, "status", "trainStatus", "train_status"))) {
            result.put("status", "FAILED");
        }
        if (response != null && TextUtils.hasText(response.message()) && !result.has("message")) {
            result.put("message", response.message());
        }
        if (!TextUtils.hasText(firstText(result, "batchNo", "trainBatchNo", "train_batch_no", "batch_no"))) {
            result.put("train_batch_no", batchNo);
        }
        return result;
    }

    private ModelTrainAgentRespDTO parseAgentResponse(JsonNode agentResponse) {
        if (agentResponse == null || !agentResponse.isObject() || !agentResponse.has("code")) {
            return null;
        }
        try {
            return objectMapper.treeToValue(agentResponse, ModelTrainAgentRespDTO.class);
        } catch (Exception e) {
            LOGGER.warn("model train response cannot convert to standard envelope response={}", agentResponse, e);
            return null;
        }
    }

    private JsonNode unwrapTrainResult(JsonNode node) {
        if (node == null || !node.isObject() || !node.has("code")) {
            return node;
        }
        JsonNode data = firstNode(node, "data", "result");
        String batchNo = firstText(data, "batchNo", "trainBatchNo", "train_batch_no", "batch_no");
        return normalizeTrainResult(batchNo, node);
    }

    private void updateTrainDetailAfterSubmit(String batchNo, String status, JsonNode agentResponse, String errorMessage) {
        updateTrainDetail(agentResponse, batchNo, status, errorMessage);
    }

    private void saveTrainDetail(ModelTrainConfigTb trainConfig,
                                 String batchNo,
                                 String status,
                                 JsonNode payload,
                                 JsonNode result) {
        java.util.Date now = new java.util.Date();
        ModelTrainRecordTb detail = new ModelTrainRecordTb();
        detail.setBatchNo(batchNo);
        detail.setAgentCode(defaultText(trainConfig.getAgentCode(), "unknown"));
        detail.setRegionCode(defaultText(trainConfig.getRegionCode(), "ALL"));
        detail.setRegionName(defaultText(trainConfig.getRegionName(), "全部区域"));
        detail.setCustomerCode(defaultText(trainConfig.getCustomerCode(), "ALL"));
        detail.setCustomerName(defaultText(trainConfig.getCustomerName(), "全部客户"));
        detail.setIndustryCode(defaultText(trainConfig.getIndustryCode(), "ALL"));
        detail.setIndustryName(defaultText(trainConfig.getIndustryName(), "全部行业"));
        detail.setTrainStartDate(defaultText(trainConfig.getTrainStartDate(), ""));
        detail.setTrainEndDate(defaultText(trainConfig.getTrainEndDate(), ""));
        detail.setStatus(status);
        JsonNode resultJson = result == null ? objectMapper.createObjectNode()
                .put("message", "训练任务已创建，等待模型系统回写结果。") : result;
        detail.setResultJson(jsonText(resultJson));
        detail.setRequestParam(payload == null ? null : payload.toString().getBytes(StandardCharsets.UTF_8));
        detail.setCreatedBy("system");
        detail.setCreatedByName("系统");
        detail.setCreatedAt(now);
        detail.setUpdatedAt(now);
        modelTrainDetailTbMapper.insert(detail);
    }

    private void resetFailedTrainDetail(ModelTrainConfigTb trainConfig,
                                        String batchNo,
                                        String status,
                                        JsonNode payload) {
        java.util.Date now = new java.util.Date();
        modelTrainBacktestTbMapper.delete(Wrappers.<ModelTrainBacktestTb>lambdaQuery()
                .eq(ModelTrainBacktestTb::getTrainBatchNo, batchNo));
        ModelTrainRecordTb detail = modelTrainDetailTbMapper.selectOne(Wrappers.<ModelTrainRecordTb>lambdaQuery()
                .eq(ModelTrainRecordTb::getBatchNo, batchNo));
        if (detail == null) {
            throw new BusinessException("重试训练批次不存在：" + batchNo);
        }
        detail.setAgentCode(defaultText(trainConfig.getAgentCode(), "unknown"));
        detail.setRegionCode(defaultText(trainConfig.getRegionCode(), "ALL"));
        detail.setRegionName(defaultText(trainConfig.getRegionName(), "全部区域"));
        detail.setCustomerCode(defaultText(trainConfig.getCustomerCode(), "ALL"));
        detail.setCustomerName(defaultText(trainConfig.getCustomerName(), "全部客户"));
        detail.setIndustryCode(defaultText(trainConfig.getIndustryCode(), "ALL"));
        detail.setIndustryName(defaultText(trainConfig.getIndustryName(), "全部行业"));
        detail.setTrainStartDate(defaultText(trainConfig.getTrainStartDate(), ""));
        detail.setTrainEndDate(defaultText(trainConfig.getTrainEndDate(), ""));
        detail.setStatus(status);
        detail.setBestModel(null);
        detail.setMape(null);
        detail.setWmape(null);
        detail.setSmape(null);
        detail.setRmse(null);
        detail.setMae(null);
        detail.setR2(null);
        detail.setTrainDurationSeconds(null);
        detail.setModelVersion(null);
        detail.setErrorMessage(null);
        detail.setResultJson(jsonText(objectMapper.createObjectNode()
                .put("message", "失败批次已重新提交训练，等待模型系统回写结果。")));
        detail.setRequestParam(payload == null ? null : payload.toString().getBytes(StandardCharsets.UTF_8));
        detail.setUpdatedAt(now);
        modelTrainDetailTbMapper.updateById(detail);
    }

    private void updateTrainDetail(JsonNode result, String batchNo, String status, String errorMessage) {
        ModelTrainRecordTb detail = modelTrainDetailTbMapper.selectOne(Wrappers.<ModelTrainRecordTb>lambdaQuery()
                .eq(ModelTrainRecordTb::getBatchNo, batchNo));
        if (detail == null) {
            return;
        }
        java.util.Date now = new java.util.Date();
        detail.setStatus(status);
        if (result != null) {
            detail.setResultJson(jsonText(result));
            detail.setBestModel(firstText(result, "selected_model_name", "bestModel", "best_model", "modelName", "model_name"));
            detail.setMape(firstMetric(result, "mape", "MAPE", "backtest_MAPE", "backtestMape"));
            detail.setWmape(firstMetric(result, "wmape", "WMAPE", "backtest_WMAPE", "backtestWmape"));
            detail.setSmape(firstMetric(result, "smape", "SMAPE", "backtest_SMAPE", "backtestSmape"));
            detail.setRmse(firstMetric(result, "rmse", "RMSE", "backtest_RMSE", "backtestRmse"));
            detail.setMae(firstMetric(result, "mae", "MAE"));
            detail.setR2(firstMetric(result, "r2", "R2"));
            BigDecimal trainDurationSeconds = firstMetric(result, "elapsed_seconds", "elapsedSeconds", "train_duration_seconds", "trainDurationSeconds", "duration_seconds", "durationSeconds");
            if (trainDurationSeconds == null && isTerminalStatus(status) && detail.getCreatedAt() != null) {
                long durationMillis = Math.abs(now.getTime() - detail.getCreatedAt().getTime());
                trainDurationSeconds = BigDecimal.valueOf(durationMillis).divide(BigDecimal.valueOf(1000), 3, java.math.RoundingMode.HALF_UP);
            }
            detail.setTrainDurationSeconds(trainDurationSeconds);
            detail.setModelVersion(firstText(result, "model_version", "modelVersion", "artifact_version", "artifactVersion"));
            JsonNode metadata = firstNode(result, "metadata");
            if (metadata != null && !TextUtils.hasText(detail.getModelVersion())) {
                detail.setModelVersion(firstText(metadata, "model_version", "modelVersion", "artifact_version", "artifactVersion"));
            }
            String resultError = firstText(result, "errorMessage", "error_message", "error");
            if (!TextUtils.hasText(resultError) && "FAILED".equals(status)) {
                resultError = firstText(result, "message");
            }
            detail.setErrorMessage(resultError);
        }
        if (TextUtils.hasText(errorMessage)) {
            detail.setErrorMessage(errorMessage);
        }
        detail.setUpdatedAt(now);
        modelTrainDetailTbMapper.updateById(detail);
    }

    private void replaceBacktestDetails(String batchNo, JsonNode reqDTO) {
        JsonNode details = firstNode(reqDTO, "rolling_backtest_results", "backtests", "backtestDetails", "backtest_details", "details");
        if (details == null || !details.isArray()) {
            return;
        }
        modelTrainBacktestTbMapper.delete(Wrappers.<ModelTrainBacktestTb>lambdaQuery()
                .eq(ModelTrainBacktestTb::getTrainBatchNo, batchNo));
        java.util.Date now = new java.util.Date();
        for (JsonNode item : details) {
            java.util.Date trainDate = parseDate(firstText(item, "stat_date", "trainDate", "train_date", "date"));
            BigDecimal actualValue = firstDecimal(item, "actualValue", "actual_value", "actual");
            BigDecimal predictedValue = firstDecimal(item, "predictedValue", "predicted_value", "prediction", "predicted");
            if (trainDate == null || actualValue == null || predictedValue == null) {
                continue;
            }
            ModelTrainBacktestTb backtest = new ModelTrainBacktestTb();
            backtest.setTrainBatchNo(batchNo);
            backtest.setTrainDate(trainDate);
            backtest.setActualValue(actualValue);
            backtest.setPredictedValue(predictedValue);
            backtest.setCreatedAt(now);
            modelTrainBacktestTbMapper.insert(backtest);
        }
    }

    private String normalizeStatus(String status) {
        String value = status == null ? "" : status.trim().toUpperCase();
        return switch (value) {
            case "SUCCESS", "SUCCEEDED", "DONE", "COMPLETED", "COMPLETE" -> "SUCCESS";
            case "FAILED", "FAIL", "ERROR" -> "FAILED";
            case "RUNNING", "PROCESSING" -> "RUNNING";
            default -> TextUtils.hasText(value) ? value : "SUCCESS";
        };
    }

    private boolean isTerminalStatus(String status) {
        return "SUCCESS".equals(status) || "FAILED".equals(status);
    }

    private boolean hasFailure(JsonNode node) {
        if (TextUtils.hasText(firstText(node, "errorMessage", "error_message", "error"))) {
            return true;
        }
        JsonNode issues = firstNode(node, "issues");
        if (issues == null || !issues.isArray()) {
            return false;
        }
        for (JsonNode issue : issues) {
            if ("error".equalsIgnoreCase(firstText(issue, "severity"))) {
                return true;
            }
        }
        return false;
    }

    private Integer datasetSize(Object configJson) {
        if (configJson instanceof JsonNode node && node.path("dataset").isArray()) {
            return node.path("dataset").size();
        }
        return null;
    }

    private JsonNode parseRequestPayload(byte[] requestParam) {
        if (requestParam == null || requestParam.length == 0) {
            return null;
        }
        try {
            return objectMapper.readTree(requestParam);
        } catch (Exception e) {
            LOGGER.warn("train request param cannot parse", e);
            return null;
        }
    }

    private String jsonText(JsonNode node) {
        return node == null ? null : node.toString();
    }

    private JsonNode parseJsonValue(Object value) {
        if (value instanceof JsonNode node) {
            return node;
        }
        if (value == null) {
            return null;
        }
        String text = value.toString();
        if (!TextUtils.hasText(text)) {
            return null;
        }
        try {
            return objectMapper.readTree(text);
        } catch (Exception e) {
            LOGGER.warn("train result json cannot parse", e);
            return null;
        }
    }

    private JsonNode firstNode(JsonNode node, String... names) {
        if (node == null) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private String firstText(JsonNode node, String... names) {
        JsonNode value = firstNode(node, names);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return TextUtils.hasText(text) ? text : null;
    }

    private String defaultText(String value, String fallback) {
        return TextUtils.hasText(value) ? value : fallback;
    }

    private String agentName(String agentCode) {
        if (!TextUtils.hasText(agentCode)) {
            return null;
        }
        return switch (agentCode) {
            case "winter-supply", "winner-agent" -> "冬季保供预测智能体";
            case "monthly-sales" -> "月度销量预测智能体";
            case "short-term" -> "短期客户预测智能体";
            default -> agentCode;
        };
    }

    private BigDecimal firstDecimal(JsonNode node, String... names) {
        JsonNode value = firstNode(node, names);
        if (value == null || value.isNull() || !TextUtils.hasText(value.asText())) {
            return null;
        }
        try {
            return new BigDecimal(value.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal firstMetric(JsonNode node, String... names) {
        BigDecimal value = firstDecimal(node, names);
        if (value != null) {
            return value;
        }
        JsonNode metrics = firstNode(node, "metrics");
        return firstDecimal(metrics, names);
    }

    private java.util.Date parseDate(String value) {
        if (!TextUtils.hasText(value)) {
            return null;
        }
        try {
            return Date.valueOf(value.substring(0, Math.min(value.length(), 10)));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String resolveTimeGranularity(List<FeatureMapping> features) {
        Map<String, Long> counts = features.stream()
                .map(FeatureMapping::timeGranularity)
                .filter(TextUtils::hasText)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));
        return counts.entrySet().stream()
                .max(Comparator.comparingLong(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private String normalizeFeatureKey(String featureCode) {
        return "hdd".equalsIgnoreCase(featureCode) ? "HDD" : featureCode;
    }

    private void putDecimal(ObjectNode node, String key, BigDecimal value) {
        if (value != null) {
            node.put(key, value);
        }
    }

    private void putDate(ObjectNode node, String key, java.util.Date value) {
        if (value != null) {
            node.put(key, value.toString());
        }
    }

    private <T> void eqIfText(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<T> query,
                              SFunction<T, ?> column,
                              String value) {
        if (TextUtils.hasText(value)) {
            query.eq(column, value);
        }
    }

    private Double readFeatureValue(ModelTrainFeatureDataTb row, String featureColumn) {
        String property = toFeatureProperty(featureColumn);
        try {
            Method method = ModelTrainFeatureDataTb.class.getMethod("get" + property);
            Object value = method.invoke(row);
            return value instanceof Number number ? number.doubleValue() : null;
        } catch (ReflectiveOperationException e) {
            throw new BusinessException("训练特征字段不存在：" + featureColumn);
        }
    }

    private String toFeatureProperty(String featureColumn) {
        String column = featureColumn == null ? "" : featureColumn.trim().toLowerCase();
        if (!column.matches("feature_\\d{3}")) {
            throw new BusinessException("训练特征字段格式不合法：" + featureColumn);
        }
        return "Feature" + column.substring("feature_".length());
    }

    private record FeatureMapping(String payloadKey, String featureColumn, String timeGranularity) {
    }
}
