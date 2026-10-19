package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gas.forecast.business.component.ModelPlatformClient;
import com.gas.forecast.business.component.dto.ModelTrainApiRequest;
import com.gas.forecast.business.component.dto.ModelTrainApiResponse;
import com.gas.forecast.business.component.dto.ModelTrainResultApiResponse;
import com.gas.forecast.business.component.dto.ModelTrainingValidationApiResponse;
import com.gas.forecast.business.dto.request.ModelTrainExecuteRequest;
import com.gas.forecast.business.dto.request.ModelTrainResultRequest;
import com.gas.forecast.business.dto.response.ModelTrainExecuteResponse;
import com.gas.forecast.business.dto.response.ModelTrainExecutionStepResponse;
import com.gas.forecast.business.dto.response.ModelTrainRecordResponse;
import com.gas.forecast.business.dto.response.ModelTrainResultResponse;
import com.gas.forecast.business.enums.BaseCodeType;
import com.gas.forecast.business.enums.ModelTrainStatus;
import com.gas.forecast.business.service.BaseCodeGenerateService;
import com.gas.forecast.business.service.DynamicFeatureResult;
import com.gas.forecast.business.service.DynamicTrainingFeatureService;
import com.gas.forecast.business.service.ModelTrainExecutionService;
import com.gas.forecast.business.service.ModelTrainStepRecorder;
import com.gas.forecast.business.service.TrainingDatasetAssembler;
import com.gas.forecast.business.service.TrainingDataQualityValidator;
import com.gas.forecast.business.service.TaskForecastContinuationService;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.enums.ForecastAgentEnum;
import com.gas.forecast.common.security.context.SecurityContextHolder;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.ModelConfigTb;
import com.gas.forecast.dao.domain.ModelFeatureDefinitionTb;
import com.gas.forecast.dao.domain.ModelFeatureRef;
import com.gas.forecast.dao.domain.ModelTrainBacktestTb;
import com.gas.forecast.dao.domain.ModelTrainConfigTb;
import com.gas.forecast.dao.domain.ModelTrainFeatureDataTb;
import com.gas.forecast.dao.domain.ModelTrainRecordTb;
import com.gas.forecast.dao.mapper.ModelConfigTbMapper;
import com.gas.forecast.dao.mapper.ModelFeatureDefinitionTbMapper;
import com.gas.forecast.dao.mapper.ModelFeatureRefMapper;
import com.gas.forecast.dao.mapper.ModelTrainBacktestTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainConfigTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainExecutionStepTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainRecordTbMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class ModelTrainExecutionServiceImpl implements ModelTrainExecutionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModelTrainExecutionServiceImpl.class);
    private static final DateTimeFormatter BATCH_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ModelTrainConfigTbMapper modelTrainConfigTbMapper;
    private final ModelConfigTbMapper modelConfigTbMapper;
    private final ModelFeatureRefMapper modelFeatureRefMapper;
    private final ModelFeatureDefinitionTbMapper modelFeatureDefinitionTbMapper;
    private final ModelTrainBacktestTbMapper modelTrainBacktestTbMapper;
    private final ModelTrainRecordTbMapper modelTrainDetailTbMapper;
    private final ModelTrainExecutionStepTbMapper modelTrainExecutionStepTbMapper;
    private final BaseCodeGenerateService baseCodeGenerateService;
    private final ObjectMapper objectMapper;
    private final Executor modelTrainTaskExecutor;
    private final TransactionTemplate transactionTemplate;
    private final ModelPlatformClient modelPlatformClient;
    private final DynamicTrainingFeatureService dynamicTrainingFeatureService;
    private final ModelTrainStepRecorder trainStepRecorder;
    private final TrainingDatasetAssembler trainingDatasetAssembler;
    private final TrainingDataQualityValidator trainingDataQualityValidator;
    private final TaskForecastContinuationService taskForecastContinuationService;

    @Override
    @Transactional(readOnly = true)
    public List<ModelTrainExecutionStepResponse> getExecutionSteps(String batchNo) {
        if (!TextUtils.hasText(batchNo))
            throw new BusinessException("训练批次号不能为空");
        return modelTrainExecutionStepTbMapper
                .selectList(Wrappers.<com.gas.forecast.dao.domain.ModelTrainExecutionStepTb>lambdaQuery().eq(com.gas.forecast.dao.domain.ModelTrainExecutionStepTb::getTrainBatchNo, batchNo)
                        .orderByAsc(com.gas.forecast.dao.domain.ModelTrainExecutionStepTb::getAttemptNo).orderByAsc(com.gas.forecast.dao.domain.ModelTrainExecutionStepTb::getStepLevel)
                        .orderByAsc(com.gas.forecast.dao.domain.ModelTrainExecutionStepTb::getStepOrder).orderByAsc(com.gas.forecast.dao.domain.ModelTrainExecutionStepTb::getId))
                .stream()
                .map(step -> new ModelTrainExecutionStepResponse(step.getId(), step.getTrainBatchNo(), step.getAttemptNo(), step.getParentStepId(), step.getStepLevel(), step.getStepCode(),
                        step.getStepName(), step.getStepOrder(), step.getStatus(), step.getInputSummary(), step.getOutputSummary(), step.getProducedCount(), step.getErrorMessage(),
                        step.getStartedAt(), step.getFinishedAt(), step.getDurationMs()))
                .toList();
    }

    @Override
    @Transactional
    public ModelTrainExecuteResponse execute(ModelTrainExecuteRequest reqDTO) {
        // 1. 根据主键加载训练配置，避免使用可能发生变化的业务编码关联。
        ModelTrainConfigTb trainConfig = modelTrainConfigTbMapper.selectById(reqDTO.getTrainConfigId());
        if (trainConfig == null) {
            throw new BusinessException("训练配置不存在");
        }
        if (trainConfig.getModelId() == null) {
            throw new BusinessException("训练配置未配置所属模型");
        }

        // 2. 加载训练配置绑定的模型，并确认模型仍然存在。
        ModelConfigTb modelConfig = modelConfigTbMapper.selectById(trainConfig.getModelId());
        if (modelConfig == null) {
            throw new BusinessException("所属模型不存在");
        }

        // 队列在创建时已经分配批次号。首次执行准备数据；失败重试优先复用请求快照。
        if (TextUtils.hasText(reqDTO.getRetryBatchNo())) {
            String batchNo = reqDTO.getRetryBatchNo().trim();
            ModelTrainRecordTb existing = modelTrainDetailTbMapper.selectOne(Wrappers.<ModelTrainRecordTb>lambdaQuery().eq(ModelTrainRecordTb::getBatchNo, batchNo).last("limit 1"));
            if (existing == null)
                throw new BusinessException("训练批次不存在：" + batchNo);
            if (existing.getRequestParam() != null && existing.getRequestParam().length > 0) {
                return retryWithSnapshot(trainConfig, batchNo);
            }
            java.util.Date now = new java.util.Date();
            modelTrainDetailTbMapper.update(null, Wrappers.<ModelTrainRecordTb>lambdaUpdate().eq(ModelTrainRecordTb::getId, existing.getId()).set(ModelTrainRecordTb::getStatus, "PREPARING")
                    .set(ModelTrainRecordTb::getStartedAt, now).set(ModelTrainRecordTb::getErrorMessage, null).set(ModelTrainRecordTb::getUpdatedAt, now));
            ObjectNode payload = objectMapper.createObjectNode().put("model_code", modelConfig.getModelCode()).put("preparation_status", "PREPARING");
            submitPreparationAfterCommit(batchNo, trainConfig, modelConfig);
            return new ModelTrainExecuteResponse(trainConfig.getTrainCode(), batchNo, modelConfig.getModelCode(), 0, ModelTrainStatus.PENDING.getCode(), payload,
                    objectMapper.createObjectNode().put("message", "训练批次已领取，后台正在准备训练数据。").put("train_batch_no", batchNo));
        }

        // 3. 数据准备阶段不分配批次号；预校验通过并正式创建训练记录时才生成。
        String batchNo = null;
        ObjectNode pendingPayload = objectMapper.createObjectNode().put("model_code", modelConfig.getModelCode()).put("preparation_status", ModelTrainStatus.PENDING.getCode());
        ObjectNode createdResponse = objectMapper.createObjectNode().put("message", "训练任务已领取，后台正在准备训练数据和动态指标。").put("model_code", modelConfig.getModelCode());
        // 准备阶段不写训练批次；只有动态特征和平台预校验通过后才创建正式批次记录。
        submitPreparationAfterCommit(batchNo, trainConfig, modelConfig);
        return new ModelTrainExecuteResponse(trainConfig.getTrainCode(), batchNo, modelConfig.getModelCode(), 0, ModelTrainStatus.PENDING.getCode(), pendingPayload, createdResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ModelTrainingValidationApiResponse validateTrainingData(ModelTrainExecuteRequest reqDTO) {
        // 1. 加载并检查训练配置。
        ModelTrainConfigTb trainConfig = modelTrainConfigTbMapper.selectById(reqDTO.getTrainConfigId());
        if (trainConfig == null) {
            throw new BusinessException("训练配置不存在");
        }
        if (trainConfig.getModelId() == null) {
            throw new BusinessException("训练配置未配置所属模型");
        }
        // 2. 加载训练配置关联的模型。
        ModelConfigTb modelConfig = modelConfigTbMapper.selectById(trainConfig.getModelId());
        if (modelConfig == null) {
            throw new BusinessException("所属模型不存在");
        }
        // 3. 使用与正式训练完全相同的特征和范围规则准备数据。
        List<FeatureMapping> features = loadFeatureMappings(modelConfig.getId());
        List<ModelTrainFeatureDataTb> trainData = loadTrainData(trainConfig, modelConfig);
        trainingDataQualityValidator.validate(trainData);
        // 4. 只调用平台预校验，不创建批次和训练记录。
        DynamicFeatureResult dynamicFeatures = dynamicTrainingFeatureService.prepare(trainConfig, trainData);
        ModelTrainApiRequest validationRequest = buildTrainRequest(trainConfig, modelConfig, null, trainData, features, dynamicFeatures);
        return modelPlatformClient.validateTrainingData(validationRequest);
    }

    private ModelTrainExecuteResponse retryWithSnapshot(ModelTrainConfigTb trainConfig, String batchNo) {
        ModelTrainRecordTb record = modelTrainDetailTbMapper.selectOne(Wrappers.<ModelTrainRecordTb>lambdaQuery().eq(ModelTrainRecordTb::getBatchNo, batchNo));
        if (record == null || record.getRequestParam() == null || record.getRequestParam().length == 0) {
            throw new BusinessException("重试训练批次缺少原始请求快照：" + batchNo);
        }
        try {
            JsonNode payload = objectMapper.readTree(record.getRequestParam());
            if (payload.has("preparation_status")) {
                ModelConfigTb modelConfig = modelConfigTbMapper.selectById(trainConfig.getModelId());
                if (modelConfig == null) {
                    throw new BusinessException("所属模型不存在");
                }
                resetFailedTrainDetail(trainConfig, batchNo, ModelTrainStatus.PENDING, payload);
                submitPreparationAfterCommit(batchNo, trainConfig, modelConfig);
                ObjectNode response = objectMapper.createObjectNode().put("message", "失败批次已重新进入后台数据准备流程。").put("model_code", modelConfig.getModelCode()).put("train_batch_no", batchNo);
                return new ModelTrainExecuteResponse(trainConfig.getTrainCode(), batchNo, modelConfig.getModelCode(), 0, ModelTrainStatus.PENDING.getCode(), payload, response);
            }
            ModelTrainApiRequest trainRequest = objectMapper.treeToValue(payload, ModelTrainApiRequest.class);
            trainRequest.setTrainBatchNo(batchNo);
            JsonNode normalizedPayload = objectMapper.valueToTree(trainRequest);
            resetFailedTrainDetail(trainConfig, batchNo, ModelTrainStatus.PENDING, normalizedPayload);
            ObjectNode submitResponse = objectMapper.createObjectNode().put("message", "失败批次已创建后台重试任务。").put("model_code", trainRequest.getModelCode()).put("train_batch_no", batchNo);
            submitValidationAfterCommit(batchNo, trainRequest);
            int dataSize = trainRequest.getDataset() == null ? 0 : trainRequest.getDataset().size();
            return new ModelTrainExecuteResponse(trainConfig.getTrainCode(), batchNo, trainRequest.getModelCode(), dataSize, ModelTrainStatus.PENDING.getCode(), normalizedPayload, submitResponse);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("无法读取原始训练请求快照：" + exception.getMessage());
        }
    }

    private String generateTrainBatchNo() {
        // 先取得全局递增序号，再拼接业务前缀和当天日期形成可读批次号。
        String sequence = baseCodeGenerateService.nextCode(BaseCodeType.TRAIN_BATCH);
        String sequenceNo = sequence.substring(BaseCodeType.TRAIN_BATCH.prefix().length());
        return BaseCodeType.TRAIN_BATCH.prefix() + LocalDate.now().format(BATCH_DATE_FORMATTER) + "-" + sequenceNo;
    }

    @Override
    @Transactional
    public ModelTrainExecuteResponse updateTrainResult(ModelTrainApiResponse reqDTO) {
        // 平台主动回调先校验标准响应信封，再将 data 部分交给统一结果处理流程。
        validateTrainAgentResponse(reqDTO);
        return updateTrainResult(objectMapper.valueToTree(reqDTO.getData()));
    }

    private ModelTrainExecuteResponse updateTrainResult(JsonNode reqDTO) {
        // 1. 从平台结果中取得批次号，批次号是训练结果回写的唯一业务标识。
        JsonNode trainResult = reqDTO;
        String batchNo = trainResult.path("train_batch_no").asText(null);
        if (!TextUtils.hasText(batchNo)) {
            throw new BusinessException("训练批次号不能为空");
        }
        // 2. 确认本地已经存在对应训练记录，禁止写入来源不明的结果。
        ModelTrainRecordTb detail = modelTrainDetailTbMapper.selectOne(Wrappers.<ModelTrainRecordTb>lambdaQuery().eq(ModelTrainRecordTb::getBatchNo, batchNo));
        if (detail == null) {
            throw new BusinessException("训练批次不存在：" + batchNo);
        }

        // 3. 更新训练指标，并用本次平台结果整体替换该批次的回测明细。
        ModelTrainStatus status = ModelTrainStatus.SUCCESS;
        updateTrainDetail(trainResult, batchNo, status, null);
        replaceBacktestDetails(batchNo, trainResult);
        markTrainBatchSuccess(batchNo);
        // 事务提交后只续跑引用该训练批次的用户预测任务，不做全局自动预测扫描。
        submitAfterCommit(() -> taskForecastContinuationService.continueAfterTraining(batchNo));

        // 4. 从保存的请求快照恢复模型和数据量信息，组装接口响应。
        JsonNode requestPayload = parseRequestPayload(detail.getRequestParam());
        String modelCode = requestPayload == null ? null : requestPayload.path("model_code").asText(null);
        return new ModelTrainExecuteResponse(null, batchNo, modelCode, datasetSize(requestPayload), status.getCode(), requestPayload, trainResult);
    }

    /** 训练结果与批次调度状态在同一张表中结束，不依赖定时状态核对。 */
    private void markTrainBatchSuccess(String batchNo) {
        java.util.Date now = new java.util.Date();
        modelTrainDetailTbMapper.update(null,
                Wrappers.<ModelTrainRecordTb>lambdaUpdate().eq(ModelTrainRecordTb::getBatchNo, batchNo).set(ModelTrainRecordTb::getStatus, "SUCCESS").set(ModelTrainRecordTb::getActiveDedupKey, null)
                        .set(ModelTrainRecordTb::getErrorMessage, null).set(ModelTrainRecordTb::getFinishedAt, now).set(ModelTrainRecordTb::getUpdatedAt, now));
    }

    @Override
    public ModelTrainResultResponse getTrainResult(ModelTrainResultRequest reqDTO) {
        // 1. 指定批次时查询完整详情；未指定批次时只查询轻量列表字段。
        var query = Wrappers.<ModelTrainRecordTb>lambdaQuery();
        String batchNo = reqDTO.getBatchNo();
        boolean loadDetail = TextUtils.hasText(batchNo);
        if (!loadDetail && reqDTO.getTrainConfigId() == null) {
            throw new BusinessException("查询训练批次列表时训练配置ID不能为空");
        }
        if (loadDetail) {
            query.eq(ModelTrainRecordTb::getBatchNo, batchNo);
        } else {
            // 请求和结果字段可能包含完整训练集，列表查询不读取大字段，选中批次后再按需加载。
            query.select(ModelTrainRecordTb::getId, ModelTrainRecordTb::getTrainConfigId, ModelTrainRecordTb::getBatchNo, ModelTrainRecordTb::getAgentCode, ModelTrainRecordTb::getRegionCode,
                    ModelTrainRecordTb::getRegionName, ModelTrainRecordTb::getIndustryCode, ModelTrainRecordTb::getIndustryName, ModelTrainRecordTb::getCustomerCode,
                    ModelTrainRecordTb::getCustomerName, ModelTrainRecordTb::getTrainStartDate, ModelTrainRecordTb::getTrainEndDate, ModelTrainRecordTb::getStatus, ModelTrainRecordTb::getBestModel,
                    ModelTrainRecordTb::getMape, ModelTrainRecordTb::getWmape, ModelTrainRecordTb::getSmape, ModelTrainRecordTb::getRmse, ModelTrainRecordTb::getMae, ModelTrainRecordTb::getR2,
                    ModelTrainRecordTb::getTrainDurationSeconds, ModelTrainRecordTb::getModelVersion, ModelTrainRecordTb::getErrorMessage, ModelTrainRecordTb::getCreatedAt,
                    ModelTrainRecordTb::getUpdatedAt);
        }
        // 2. 追加页面传入的业务范围和训练时间过滤条件。
        if (reqDTO.getTrainConfigId() != null) {
            query.eq(ModelTrainRecordTb::getTrainConfigId, reqDTO.getTrainConfigId());
        }
        eqIfText(query, ModelTrainRecordTb::getAgentCode, reqDTO.getAgentCode());
        eqIfText(query, ModelTrainRecordTb::getRegionCode, reqDTO.getRegionCode());
        eqIfText(query, ModelTrainRecordTb::getIndustryCode, reqDTO.getIndustryCode());
        eqIfText(query, ModelTrainRecordTb::getCustomerCode, reqDTO.getCustomerCode());
        eqIfText(query, ModelTrainRecordTb::getTrainStartDate, reqDTO.getTrainStartDate());
        eqIfText(query, ModelTrainRecordTb::getTrainEndDate, reqDTO.getTrainEndDate());
        // 3. 详情只取一条，列表固定返回最近二十条训练记录。
        query.orderByDesc(ModelTrainRecordTb::getUpdatedAt).orderByDesc(ModelTrainRecordTb::getId).last(loadDetail ? "limit 1" : "limit 20");
        List<ModelTrainRecordTb> details = modelTrainDetailTbMapper.selectList(query);

        // 4. 将数据库实体转换为前端需要的训练记录响应。
        List<ModelTrainRecordResponse> resultDetails = new ArrayList<>();
        for (ModelTrainRecordTb detail : details) {
            ModelTrainRecordResponse item = new ModelTrainRecordResponse();
            item.setId(detail.getId());
            item.setBatchNo(detail.getBatchNo());
            item.setAgentCode(detail.getAgentCode());
            item.setAgentName(agentName(detail.getAgentCode()));
            item.setRegionName(detail.getRegionName());
            item.setIndustryName(detail.getIndustryName());
            item.setCustomerName(detail.getCustomerName());
            item.setTrainStartDate(detail.getTrainStartDate());
            item.setTrainEndDate(detail.getTrainEndDate());
            item.setStatus(detail.getStatus());
            item.setBestModel(detail.getBestModel());
            item.setMape(detail.getMape());
            item.setWmape(detail.getWmape());
            item.setSmape(detail.getSmape());
            item.setRmse(detail.getRmse());
            item.setMae(detail.getMae());
            item.setR2(detail.getR2());
            item.setTrainDurationSeconds(detail.getTrainDurationSeconds());
            item.setModelVersion(detail.getModelVersion());
            item.setErrorMessage(detail.getErrorMessage());
            if (TextUtils.hasText(detail.getErrorMessage())) {
                item.setMessage(customerFailureMessage(detail));
            }
            item.setCreatedAt(detail.getCreatedAt());
            item.setUpdatedAt(detail.getUpdatedAt());
            if (loadDetail) {
                // 5. 详情模式返回实际提交给模型的完整请求，训练数据页据此原样展示。
                JsonNode requestJson = parseRequestPayload(detail.getRequestParam());
                if (requestJson != null) {
                    item.setRequestJson(objectMapper.convertValue(requestJson, ModelTrainApiRequest.class));
                }
                JsonNode node = parseJsonValue(detail.getResultJson());
                if (node != null && TextUtils.hasText(node.path("train_batch_no").asText(null))) {
                    item.setResultJson(objectMapper.convertValue(node, ModelTrainResultApiResponse.class));
                }
            }
            resultDetails.add(item);
        }
        // 6. 默认将查询结果的第一条作为当前选中批次。
        String selectedBatchNo = details.isEmpty() ? null : details.get(0).getBatchNo();
        return new ModelTrainResultResponse(resultDetails, selectedBatchNo);
    }

    private List<FeatureMapping> loadFeatureMappings(Long modelId) {
        // 1. 按配置顺序读取模型引用的特征。
        List<ModelFeatureRef> refs = modelFeatureRefMapper
                .selectList(Wrappers.<ModelFeatureRef>lambdaQuery().eq(ModelFeatureRef::getModelId, modelId).orderByAsc(ModelFeatureRef::getFeatureOrder).orderByAsc(ModelFeatureRef::getId));
        if (refs.isEmpty()) {
            throw new BusinessException("所属模型未配置训练特征");
        }
        // 2. 批量读取已启用的特征定义，避免循环查询数据库。
        List<Long> featureIds = refs.stream().map(ModelFeatureRef::getFeatureId).filter(id -> id != null).distinct().toList();
        Map<Long, ModelFeatureDefinitionTb> definitions = modelFeatureDefinitionTbMapper.selectBatchIds(featureIds).stream().filter(item -> item.getEnabled() == null || item.getEnabled() == 1)
                .collect(Collectors.toMap(ModelFeatureDefinitionTb::getId, Function.identity(), (left, right) -> left));
        // 3. 将特征编码、物理字段和时间粒度整理为训练请求映射。
        List<FeatureMapping> mappings = refs.stream().filter(ref -> definitions.containsKey(ref.getFeatureId())).map(ref -> {
            ModelFeatureDefinitionTb definition = definitions.get(ref.getFeatureId());
            String payloadKey = TextUtils.hasText(ref.getOutputFeatureCode()) ? ref.getOutputFeatureCode() : definition.getFeatureCode();
            return new FeatureMapping(payloadKey, definition.getSourceField(), definition.getCalculationType(), definition.getMissingValueStrategy());
        }).filter(mapping -> TextUtils.hasText(mapping.getPayloadKey()) && TextUtils.hasText(mapping.getSourceField())).toList();
        if (mappings.isEmpty()) {
            throw new BusinessException("所属模型没有可用训练特征");
        }
        return mappings;
    }

    private List<ModelTrainFeatureDataTb> loadTrainData(ModelTrainConfigTb trainConfig, ModelConfigTb modelConfig) {
        return trainingDatasetAssembler.assemble(trainConfig, modelConfig);
    }

    private ModelTrainApiRequest buildTrainRequest(ModelTrainConfigTb trainConfig, ModelConfigTb modelConfig, String batchNo, List<ModelTrainFeatureDataTb> trainData, List<FeatureMapping> features,
            DynamicFeatureResult dynamicFeatures) {
        // 1. 将标准训练数据逐行转换成模型平台约定的 dataset 结构。
        List<JsonNode> dataset = new ArrayList<>();
        for (ModelTrainFeatureDataTb row : trainData) {
            // 固定输出日期和目标销量，再按照模型特征映射动态读取 feature_xxx 字段。
            ObjectNode item = objectMapper.createObjectNode();
            item.put("date", row.getStatDate());
            putDecimal(item, "gas_sales", row.getGasSales());
            for (FeatureMapping feature : features) {
                Double value = readSourceValue(row, feature);
                if (value == null && "ZERO".equalsIgnoreCase(feature.getMissingValueStrategy()))
                    value = 0D;
                if (value == null && "ERROR".equalsIgnoreCase(feature.getMissingValueStrategy())) {
                    throw new BusinessException("训练数据缺少特征值：日期=" + row.getStatDate() + "，特征=" + feature.getPayloadKey() + "，来源字段=" + feature.getSourceField());
                }
                if (value != null) {
                    item.put(feature.getPayloadKey(), value);
                }
            }
            dynamicFeatures.appendValues(item, row.getStatDate());
            dataset.add(item);
        }
        LOGGER.info("训练数据拼接完成: trainCode={}, rows={}, baseFeatures={}, dynamicFeatures={}, dynamicValueCount={}", trainConfig.getTrainCode(), dataset.size(), features.size(),
                dynamicFeatures.featureCodes(), dynamicFeatures.valueCount());
        // 2. 组装模型、批次、作用范围、扩展参数和数据集等请求上下文。
        ModelTrainApiRequest request = new ModelTrainApiRequest();
        request.setAgentCode(trainConfig.getAgentCode());
        request.setModelCode(modelConfig.getModelCode());
        request.setTrainBatchNo(batchNo);
        request.setRegionCode(trainConfig.getRegionCode());
        request.setRegionName(trainConfig.getRegionName());
        request.setIndustryCode(trainConfig.getIndustryCode());
        request.setIndustryName(trainConfig.getIndustryName());
        request.setCustomerCode(trainConfig.getCustomerCode());
        request.setCustomerName(trainConfig.getCustomerName());
        ObjectNode params = objectMapper.createObjectNode();
        params.set("dynamic_feature_snapshot", dynamicFeatures.snapshot());
        request.setParams(params);
        request.setDataset(dataset);
        return request;
    }

    private ModelTrainApiResponse callTrainAgent(ModelTrainApiRequest trainRequest) {
        // 统一通过模型平台服务发送请求，当前业务类不直接依赖 HTTP 地址和客户端。
        return modelPlatformClient.train(trainRequest);
    }

    private void submitPreparationAfterCommit(String batchNo, ModelTrainConfigTb trainConfig, ModelConfigTb modelConfig) {
        submitAfterCommit(() -> prepareValidateAndTrain(batchNo, trainConfig, modelConfig));
    }

    private void submitValidationAfterCommit(String batchNo, ModelTrainApiRequest trainRequest) {
        submitAfterCommit(() -> validateAndTrain(batchNo, trainRequest));
    }

    private void submitAfterCommit(Runnable asyncTask) {
        Runnable submitTask = () -> modelTrainTaskExecutor.execute(asyncTask);
        // 存在事务时必须等事务提交成功后启动后台流程，事务回滚则不创建异步任务。
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submitTask.run();
                }
            });
            return;
        }
        // 没有活动事务时直接提交异步任务。
        submitTask.run();
    }

    private void prepareValidateAndTrain(String batchNo, ModelTrainConfigTb trainConfig, ModelConfigTb modelConfig) {
        int attemptNo = currentAttemptNo(batchNo);
        Long currentStep = null;
        try {
            currentStep = startStep(batchNo, attemptNo, "LOAD_DATA", "加载训练数据", 1, "trainConfigId=" + trainConfig.getId());
            List<FeatureMapping> features = loadFeatureMappings(modelConfig.getId());
            List<ModelTrainFeatureDataTb> trainData = loadTrainData(trainConfig, modelConfig);
            trainingDataQualityValidator.validate(trainData);
            trainStepRecorder.success(currentStep, "基础特征=" + features.size() + "，训练数据=" + trainData.size(), trainData.size());
            currentStep = null;

            currentStep = startStep(batchNo, attemptNo, "DYNAMIC_FEATURE", "计算动态特征", 2, "基础训练数据=" + trainData.size());
            LOGGER.info("后台开始计算动态指标: batchNo={}, trainCode={}, baseRows={}", batchNo, trainConfig.getTrainCode(), trainData.size());
            DynamicFeatureResult dynamicFeatures = dynamicTrainingFeatureService.prepare(trainConfig, trainData, batchNo, attemptNo, currentStep);
            LOGGER.info("后台动态指标计算完成: batchNo={}, status={}, featureCodes={}, valueCount={}", batchNo, dynamicFeatures.snapshot().path("status").asText(), dynamicFeatures.featureCodes(),
                    dynamicFeatures.valueCount());
            trainStepRecorder.success(currentStep, dynamicFeatures.snapshot().toString(), dynamicFeatures.valueCount());
            currentStep = null;

            currentStep = startStep(batchNo, attemptNo, "BUILD_REQUEST", "组装训练请求", 3, "模型=" + modelConfig.getModelCode());
            ModelTrainApiRequest trainRequest = buildTrainRequest(trainConfig, modelConfig, batchNo, trainData, features, dynamicFeatures);
            trainStepRecorder.success(currentStep, "dataset=" + trainRequest.getDataset().size(), trainRequest.getDataset().size());
            currentStep = null;
            validateAndTrain(batchNo, trainConfig, trainRequest, attemptNo);
        } catch (RuntimeException exception) {
            trainStepRecorder.fail(currentStep, exception);
            markPreparationFailed(batchNo, exception);
        }
    }

    private void validateAndTrain(String batchNo, ModelTrainConfigTb trainConfig, ModelTrainApiRequest trainRequest, int attemptNo) {
        Long validationStep = startStep(batchNo, attemptNo, "VALIDATE_DATA", "校验训练数据", 4, "dataset=" + (trainRequest.getDataset() == null ? 0 : trainRequest.getDataset().size()));
        try {
            trainingDataQualityValidator.validatePayload(trainRequest.getDataset());
            ModelTrainingValidationApiResponse validation = modelPlatformClient.validateTrainingData(trainRequest);
            trainStepRecorder.success(validationStep, validationSuccessDetail(validation), trainRequest.getDataset() == null ? 0 : trainRequest.getDataset().size());
            validationStep = null;
            ModelTrainStatus status = modelPlatformClient.isTrainingEnabled() ? ModelTrainStatus.RUNNING : ModelTrainStatus.PENDING;
            // 批次号使用数据库序列生成，生成器要求必须处于事务中。因此批次分配、
            // 训练记录创建和任务状态回写必须在同一个事务内完成。
            String resolvedBatchNo = transactionTemplate.execute(tx -> {
                String value = TextUtils.hasText(batchNo) ? batchNo : generateTrainBatchNo();
                trainRequest.setTrainBatchNo(value);
                JsonNode requestPayload = objectMapper.valueToTree(trainRequest);
                ObjectNode submitResponse = objectMapper.createObjectNode().put("message", modelPlatformClient.isTrainingEnabled() ? "动态指标和数据校验完成，已提交模型训练。" : "动态指标和数据校验完成，未配置训练接口。")
                        .put("model_code", trainRequest.getModelCode()).put("train_batch_no", value);
                ModelTrainRecordTb existing = modelTrainDetailTbMapper.selectOne(Wrappers.<ModelTrainRecordTb>lambdaQuery().eq(ModelTrainRecordTb::getBatchNo, value).last("limit 1"));
                if (existing == null)
                    saveTrainDetail(trainConfig, value, status, requestPayload, submitResponse);
                else
                    updatePreparedTrainDetail(value, requestPayload, status, submitResponse);
                return value;
            });
            if (!TextUtils.hasText(resolvedBatchNo))
                throw new BusinessException("训练批次号生成失败");
            if (modelPlatformClient.isTrainingEnabled())
                waitTrainResultAndPersist(resolvedBatchNo, trainRequest, attemptNo);
        } catch (RuntimeException exception) {
            trainStepRecorder.fail(validationStep, exception);
            markPreparationFailed(batchNo, exception);
        }
    }

    private void validateAndTrain(String batchNo, ModelTrainApiRequest trainRequest) {
        int attemptNo = currentAttemptNo(batchNo);
        Long validationStep = startStep(batchNo, attemptNo, "VALIDATE_DATA", "校验训练数据", 4, "复用请求快照，dataset=" + (trainRequest.getDataset() == null ? 0 : trainRequest.getDataset().size()));
        try {
            // 数据和动态指标准备完成后再进行平台校验，并保存可供失败重试复用的完整请求快照。
            trainingDataQualityValidator.validatePayload(trainRequest.getDataset());
            ModelTrainingValidationApiResponse validation = modelPlatformClient.validateTrainingData(trainRequest);
            trainStepRecorder.success(validationStep, validationSuccessDetail(validation), trainRequest.getDataset() == null ? 0 : trainRequest.getDataset().size());
            validationStep = null;
            JsonNode requestPayload = objectMapper.valueToTree(trainRequest);
            ModelTrainStatus status = modelPlatformClient.isTrainingEnabled() ? ModelTrainStatus.RUNNING : ModelTrainStatus.PENDING;
            ObjectNode submitResponse = objectMapper.createObjectNode().put("message", modelPlatformClient.isTrainingEnabled() ? "动态指标和数据校验完成，已提交模型训练。" : "动态指标和数据校验完成，未配置训练接口。")
                    .put("model_code", trainRequest.getModelCode()).put("train_batch_no", batchNo);
            transactionTemplate.executeWithoutResult(tx -> updatePreparedTrainDetail(batchNo, requestPayload, status, submitResponse));
            if (modelPlatformClient.isTrainingEnabled()) {
                waitTrainResultAndPersist(batchNo, trainRequest, attemptNo);
            }
        } catch (RuntimeException exception) {
            trainStepRecorder.fail(validationStep, exception);
            markPreparationFailed(batchNo, exception);
        }
    }

    private String validationSuccessDetail(ModelTrainingValidationApiResponse validation) {
        if (validation == null || validation.getWarnings() == null || validation.getWarnings().isEmpty()) {
            return "模型平台校验通过";
        }
        String warnings = validation.getWarnings().stream()
                .map(ModelTrainingValidationApiResponse.ValidationIssueApiResponse::getMessage)
                .filter(TextUtils::hasText)
                .collect(Collectors.joining("；"));
        return TextUtils.hasText(warnings) ? "模型平台校验通过，提示：" + warnings : "模型平台校验通过";
    }

    private void markPreparationFailed(String batchNo, RuntimeException exception) {
        LOGGER.error("model train preparation failed batchNo={}", batchNo, exception);
        String message = modelPlatformClient.extractErrorMessage(exception, "训练数据准备失败");
        // 批次即任务；准备失败直接回写当前批次，后续由调度器安排自动重试。
        if (modelTrainDetailTbMapper.selectCount(Wrappers.<ModelTrainRecordTb>lambdaQuery().eq(ModelTrainRecordTb::getBatchNo, batchNo)) > 0) {
            ObjectNode failedResult = objectMapper.createObjectNode().put("train_batch_no", batchNo).put("status", ModelTrainStatus.FAILED.getCode()).put("message", message);
            transactionTemplate.executeWithoutResult(tx -> updateTrainDetailAfterSubmit(batchNo, ModelTrainStatus.FAILED, failedResult, message));
        }
    }

    private void waitTrainResultAndPersist(String batchNo, ModelTrainApiRequest trainRequest, int attemptNo) {
        Long trainStep = startStep(batchNo, attemptNo, "MODEL_TRAIN", "执行模型训练", 5, "model=" + trainRequest.getModelCode());
        try {
            // 1. 调用平台并将标准响应信封转换为训练结果数据。
            ModelTrainApiResponse agentResponse = callTrainAgent(trainRequest);
            JsonNode trainResult = normalizeTrainResult(batchNo, agentResponse);
            trainStepRecorder.success(trainStep, "模型平台训练完成", null);
            trainStep = null;
            Long persistStep = startStep(batchNo, attemptNo, "PERSIST_RESULT", "保存训练结果", 6, "保存指标及回测明细");
            // 2. 异步线程不继承原事务，使用独立事务保存成功结果和回测明细。
            try {
                transactionTemplate.executeWithoutResult(status -> updateTrainResult(trainResult));
                int count = trainResult.path("rolling_backtest_results").isArray() ? trainResult.path("rolling_backtest_results").size() : 0;
                trainStepRecorder.success(persistStep, "训练指标和回测结果已入库", count);
            } catch (RuntimeException persistException) {
                trainStepRecorder.fail(persistStep, persistException);
                throw persistException;
            }
        } catch (RuntimeException e) {
            trainStepRecorder.fail(trainStep, e);
            // 3. 远程调用或结果处理失败时提取平台消息，并在独立事务中标记批次失败。
            LOGGER.error("model train failed batchNo={}", batchNo, e);
            String message = modelPlatformClient.extractErrorMessage(e, "模型训练失败");
            ObjectNode failedResult = objectMapper.createObjectNode().put("train_batch_no", batchNo).put("status", ModelTrainStatus.FAILED.getCode()).put("message", message);
            transactionTemplate.executeWithoutResult(status -> updateTrainDetailAfterSubmit(batchNo, ModelTrainStatus.FAILED, failedResult, message));
        }
    }

    private int currentAttemptNo(String batchNo) {
        if (!TextUtils.hasText(batchNo))
            return 1;
        ModelTrainRecordTb record = modelTrainDetailTbMapper
                .selectOne(Wrappers.<ModelTrainRecordTb>lambdaQuery().select(ModelTrainRecordTb::getRetryCount).eq(ModelTrainRecordTb::getBatchNo, batchNo).last("limit 1"));
        return record == null || record.getRetryCount() == null ? 1 : record.getRetryCount() + 1;
    }

    private Long startStep(String batchNo, int attemptNo, String code, String name, int order, String input) {
        return TextUtils.hasText(batchNo) ? trainStepRecorder.start(batchNo, attemptNo, code, name, order, input) : null;
    }

    private JsonNode normalizeTrainResult(String batchNo, ModelTrainApiResponse response) {
        // 1. 校验模型平台返回的标准响应对象。
        validateTrainAgentResponse(response);
        // 2. 校验响应批次，防止异步并发时将结果写入错误批次。
        if (!batchNo.equals(response.getData().getTrainBatchNo())) {
            throw new BusinessException("模型平台返回的训练批次号与请求不一致");
        }
        // 3. 后续业务只处理 data 内容，不继续依赖外层响应信封。
        return objectMapper.valueToTree(response.getData());
    }

    private void validateTrainAgentResponse(ModelTrainApiResponse response) {
        // 依次校验响应对象、平台状态码和结果中的训练批次号。
        if (response == null) {
            throw new BusinessException("模型平台返回的训练结果格式不正确");
        }
        if (response.getCode() == null || response.getCode() != 0) {
            throw new BusinessException(TextUtils.hasText(response.getMessage()) ? response.getMessage() : "模型平台训练失败");
        }
        if (response.getData() == null || !TextUtils.hasText(response.getData().getTrainBatchNo())) {
            throw new BusinessException("模型平台返回的训练结果缺少 train_batch_no");
        }
    }

    private void updateTrainDetailAfterSubmit(String batchNo, ModelTrainStatus status, JsonNode agentResponse, String errorMessage) {
        // 提交阶段和异步失败阶段共用训练记录更新逻辑。
        updateTrainDetail(agentResponse, batchNo, status, errorMessage);
    }

    private void updatePreparedTrainDetail(String batchNo, JsonNode requestPayload, ModelTrainStatus status, JsonNode result) {
        ModelTrainRecordTb detail = modelTrainDetailTbMapper.selectOne(Wrappers.<ModelTrainRecordTb>lambdaQuery().eq(ModelTrainRecordTb::getBatchNo, batchNo));
        if (detail == null) {
            throw new BusinessException("训练批次不存在：" + batchNo);
        }
        detail.setRequestParam(requestPayload.toString().getBytes(StandardCharsets.UTF_8));
        detail.setStatus(status.getCode());
        detail.setResultJson(jsonText(result));
        detail.setErrorMessage(null);
        detail.setUpdatedAt(new java.util.Date());
        modelTrainDetailTbMapper.updateById(detail);
    }

    private void saveTrainDetail(ModelTrainConfigTb trainConfig, String batchNo, ModelTrainStatus status, JsonNode payload, JsonNode result) {
        // 1. 创建训练记录并保存训练配置 ID、批次号和业务范围快照。
        java.util.Date now = new java.util.Date();
        ModelTrainRecordTb detail = new ModelTrainRecordTb();
        detail.setTrainConfigId(trainConfig.getId());
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
        // 2. 保存当前状态说明和完整平台请求，便于结果展示、重试与问题追踪。
        detail.setStatus(status.getCode());
        JsonNode resultJson = result == null ? objectMapper.createObjectNode().put("message", "训练任务已创建，等待模型系统回写结果。") : result;
        detail.setResultJson(jsonText(resultJson));
        detail.setRequestParam(payload == null ? null : payload.toString().getBytes(StandardCharsets.UTF_8));
        // 3. 记录操作人和时间后写入数据库。
        detail.setCreatedBy(String.valueOf(SecurityContextHolder.getUserId()));
        detail.setCreatedByName(SecurityContextHolder.getUserName());
        detail.setCreatedAt(now);
        detail.setUpdatedAt(now);
        modelTrainDetailTbMapper.insert(detail);
    }

    private void resetFailedTrainDetail(ModelTrainConfigTb trainConfig, String batchNo, ModelTrainStatus status, JsonNode payload) {
        // 1. 使用“失败状态”作为更新条件原子抢占批次，防止并发重复重试。
        java.util.Date now = new java.util.Date();
        int claimed = modelTrainDetailTbMapper.update(null,
                Wrappers.<ModelTrainRecordTb>lambdaUpdate().eq(ModelTrainRecordTb::getBatchNo, batchNo)
                        .in(ModelTrainRecordTb::getStatus, ModelTrainStatus.FAILED.getCode(), "FAILED_FINAL", "WAITING", "CLAIMED").set(ModelTrainRecordTb::getStatus, status.getCode())
                        .set(ModelTrainRecordTb::getErrorMessage, null).set(ModelTrainRecordTb::getUpdatedAt, now));
        // 2. 抢占失败时区分批次不存在和批次已被其他请求提交。
        if (claimed == 0) {
            ModelTrainRecordTb current = modelTrainDetailTbMapper.selectOne(Wrappers.<ModelTrainRecordTb>lambdaQuery().eq(ModelTrainRecordTb::getBatchNo, batchNo));
            if (current == null) {
                throw new BusinessException("重试训练批次不存在：" + batchNo);
            }
            throw new BusinessException("该训练批次已提交或正在运行，请勿重复提交");
        }

        // 3. 删除上次失败任务可能留下的回测明细，避免新旧结果混合。
        modelTrainBacktestTbMapper.delete(Wrappers.<ModelTrainBacktestTb>lambdaQuery().eq(ModelTrainBacktestTb::getTrainBatchNo, batchNo));
        // 4. 使用当前训练配置刷新范围快照，并清空旧训练指标和错误信息。
        ModelTrainRecordTb detail = modelTrainDetailTbMapper.selectOne(Wrappers.<ModelTrainRecordTb>lambdaQuery().eq(ModelTrainRecordTb::getBatchNo, batchNo));
        detail.setTrainConfigId(trainConfig.getId());
        detail.setAgentCode(defaultText(trainConfig.getAgentCode(), "unknown"));
        detail.setRegionCode(defaultText(trainConfig.getRegionCode(), "ALL"));
        detail.setRegionName(defaultText(trainConfig.getRegionName(), "全部区域"));
        detail.setCustomerCode(defaultText(trainConfig.getCustomerCode(), "ALL"));
        detail.setCustomerName(defaultText(trainConfig.getCustomerName(), "全部客户"));
        detail.setIndustryCode(defaultText(trainConfig.getIndustryCode(), "ALL"));
        detail.setIndustryName(defaultText(trainConfig.getIndustryName(), "全部行业"));
        detail.setTrainStartDate(defaultText(trainConfig.getTrainStartDate(), ""));
        detail.setTrainEndDate(defaultText(trainConfig.getTrainEndDate(), ""));
        detail.setStatus(status.getCode());
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
        // 5. 保存重新生成的请求快照和提交时间。
        detail.setResultJson(jsonText(objectMapper.createObjectNode().put("message", "失败批次已重新提交训练，等待模型系统回写结果。")));
        detail.setRequestParam(payload == null ? null : payload.toString().getBytes(StandardCharsets.UTF_8));
        detail.setUpdatedAt(now);
        modelTrainDetailTbMapper.updateById(detail);
    }

    private void updateTrainDetail(JsonNode result, String batchNo, ModelTrainStatus status, String errorMessage) {
        // 1. 按批次定位训练记录；回调找不到本地批次时忽略，防止新增脏记录。
        ModelTrainRecordTb detail = modelTrainDetailTbMapper.selectOne(Wrappers.<ModelTrainRecordTb>lambdaQuery().eq(ModelTrainRecordTb::getBatchNo, batchNo));
        if (detail == null) {
            return;
        }
        // 2. 更新批次状态，并严格按照 TrainResult 标准字段提取指标。
        java.util.Date now = new java.util.Date();
        detail.setStatus(status.getCode());
        if (result != null) {
            // 训练结果字段遵循模型平台 TrainResult 标准。
            detail.setResultJson(jsonText(result));
            detail.setBestModel(result.path("selected_model_name").asText(null));
            JsonNode metrics = result.path("metrics");
            detail.setMape(decimal(metrics, "mape"));
            detail.setWmape(decimal(metrics, "wmape"));
            detail.setSmape(decimal(metrics, "smape"));
            detail.setRmse(decimal(metrics, "rmse"));
            detail.setMae(decimal(metrics, "mae"));
            detail.setR2(decimal(metrics, "r2"));
            // 平台未返回耗时时，终态任务使用本地创建时间和完成时间估算。
            BigDecimal trainDurationSeconds = null;
            if (status.isTerminal() && detail.getCreatedAt() != null) {
                long durationMillis = Math.abs(now.getTime() - detail.getCreatedAt().getTime());
                trainDurationSeconds = BigDecimal.valueOf(durationMillis).divide(BigDecimal.valueOf(1000), 3, java.math.RoundingMode.HALF_UP);
            }
            detail.setTrainDurationSeconds(trainDurationSeconds);
            // 模型版本固定读取 metadata.model_version。
            JsonNode metadata = result.get("metadata");
            detail.setModelVersion(metadata == null ? null : metadata.path("model_version").asText(null));
        }
        // 3. 调用异常中提取到的错误信息优先于平台结果字段。
        if (TextUtils.hasText(errorMessage)) {
            detail.setErrorMessage(errorMessage);
        }
        // 4. 保存结果；非失败状态额外清除数据库中可能遗留的旧错误。
        detail.setUpdatedAt(now);
        modelTrainDetailTbMapper.updateById(detail);
        if (status != ModelTrainStatus.FAILED && !TextUtils.hasText(errorMessage)) {
            modelTrainDetailTbMapper.update(null, Wrappers.<ModelTrainRecordTb>lambdaUpdate().eq(ModelTrainRecordTb::getBatchNo, batchNo).set(ModelTrainRecordTb::getErrorMessage, null));
        }
    }

    private void replaceBacktestDetails(String batchNo, JsonNode reqDTO) {
        // 1. 按模型平台 TrainResult 标准读取回测数组；没有回测数据时不改数据库。
        JsonNode details = reqDTO.path("rolling_backtest_results");
        if (details == null || !details.isArray()) {
            return;
        }
        // 2. 先删除旧明细，保证同一批次重复回写时结果保持幂等。
        modelTrainBacktestTbMapper.delete(Wrappers.<ModelTrainBacktestTb>lambdaQuery().eq(ModelTrainBacktestTb::getTrainBatchNo, batchNo));
        java.util.Date now = new java.util.Date();
        for (JsonNode item : details) {
            // 3. 按标准字段读取数据，并跳过日期或数值不完整的无效回测点。
            java.util.Date trainDate = parseDate(item.path("stat_date").asText(null));
            BigDecimal actualValue = decimal(item, "actual_value");
            BigDecimal predictedValue = decimal(item, "predicted_value");
            if (trainDate == null || actualValue == null || predictedValue == null) {
                continue;
            }
            // 4. 将有效回测点逐条保存到批次明细表。
            ModelTrainBacktestTb backtest = new ModelTrainBacktestTb();
            backtest.setTrainBatchNo(batchNo);
            backtest.setTrainDate(trainDate);
            backtest.setActualValue(actualValue);
            backtest.setPredictedValue(predictedValue);
            backtest.setCreatedAt(now);
            modelTrainBacktestTbMapper.insert(backtest);
        }
    }

    private Integer datasetSize(Object configJson) {
        // 仅当请求快照包含数组形式的 dataset 时返回实际数据量。
        if (configJson instanceof JsonNode node && node.path("dataset").isArray()) {
            return node.path("dataset").size();
        }
        return null;
    }

    private String customerFailureMessage(ModelTrainRecordTb detail) {
        String error = detail.getErrorMessage();
        if (!TextUtils.hasText(error))
            return null;
        java.util.regex.Matcher requiredDays = java.util.regex.Pattern.compile("至少需要?(\\d+)天|至少需要\\s*(\\d+)\\s*条").matcher(error);
        if (requiredDays.find()) {
            int required = Integer.parseInt(requiredDays.group(1) != null ? requiredDays.group(1) : requiredDays.group(2));
            Integer actual = trainingDays(detail.getTrainStartDate(), detail.getTrainEndDate());
            if (actual != null) {
                int missing = Math.max(0, required - actual);
                return "暂时无法完成训练：当前训练范围只有" + actual + "天有效周期，模型至少需要" + required + "天，还需补充" + missing + "天数据。请扩大训练范围后重试。";
            }
            return "暂时无法完成训练：有效历史数据不足，模型至少需要" + required + "天数据。请补充数据后重试。";
        }
        if (error.matches(".*(insufficient data|数据不足|有效数据不足|连续.*数据).*"))
            return "暂时无法完成训练：该客户的有效历史数据不足或不连续。请检查销量数据完整性后重试。";
        if (error.matches(".*(401|403|Authentication|api key|连接|timeout|timed out|Connection).*"))
            return "预测服务暂时不可用，请稍后重试；如持续失败，请联系平台管理员。";
        return "本次训练未能完成。请检查该客户的历史数据和模型适用范围后重试；如仍失败，请将批次号提供给平台管理员。";
    }

    private Integer trainingDays(String start, String end) {
        if (!TextUtils.hasText(start) || !TextUtils.hasText(end))
            return null;
        try {
            return Math.toIntExact(java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(start), LocalDate.parse(end)) + 1);
        } catch (Exception ignored) {
            return null;
        }
    }

    private JsonNode parseRequestPayload(byte[] requestParam) {
        // 空快照直接返回，避免无意义的 JSON 解析。
        if (requestParam == null || requestParam.length == 0) {
            return null;
        }
        // 解析失败记录日志并返回空，保证历史异常数据不会影响列表查询。
        try {
            return objectMapper.readTree(requestParam);
        } catch (Exception e) {
            LOGGER.warn("train request param cannot parse", e);
            return null;
        }
    }

    private String jsonText(JsonNode node) {
        // 数据库使用文本保存 JSON，空节点保持为 null。
        return node == null ? null : node.toString();
    }

    private JsonNode parseJsonValue(Object value) {
        // Mapper 已返回 JsonNode 时直接复用，避免重复序列化。
        if (value instanceof JsonNode node) {
            return node;
        }
        // 其余类型先转换为非空文本，再尝试解析 JSON。
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

    private String defaultText(String value, String fallback) {
        // 业务范围快照不保存空文本，缺省范围统一写入明确的展示值。
        return TextUtils.hasText(value) ? value : fallback;
    }

    private String agentName(String agentCode) {
        // 将平台智能体编码转换为页面可读名称，未知编码原样返回。
        if (!TextUtils.hasText(agentCode)) {
            return null;
        }
        return ForecastAgentEnum.ofAgentCode(agentCode).map(ForecastAgentEnum::getAgentName).orElse(agentCode);
    }

    private BigDecimal decimal(JsonNode node, String name) {
        // 按模型平台标准字段读取数值，并安全转换为高精度类型。
        JsonNode value = node == null ? null : node.get(name);
        if (value == null || value.isNull() || !TextUtils.hasText(value.asText())) {
            return null;
        }
        try {
            return new BigDecimal(value.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private java.util.Date parseDate(String value) {
        // 平台可能返回日期时间，仅截取前十位 yyyy-MM-dd 作为回测日期。
        if (!TextUtils.hasText(value)) {
            return null;
        }
        try {
            return Date.valueOf(value.substring(0, Math.min(value.length(), 10)));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void putDecimal(ObjectNode node, String key, BigDecimal value) {
        // 只输出存在的数值，避免将缺失数据错误发送为零。
        if (value != null) {
            node.put(key, value);
        }
    }

    private void putDate(ObjectNode node, String key, java.util.Date value) {
        // 日期统一按东八区格式化为模型平台使用的 yyyy-MM-dd。
        if (value != null) {
            java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat("yyyy-MM-dd");
            formatter.setTimeZone(java.util.TimeZone.getTimeZone("GMT+8"));
            node.put(key, formatter.format(value));
        }
    }

    private <T> void eqIfText(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<T> query, SFunction<T, ?> column, String value) {
        // 查询值非空时才追加等值条件，避免把空字符串当作真实业务范围。
        if (TextUtils.hasText(value)) {
            query.eq(column, value);
        }
    }

    private Double readSourceValue(ModelTrainFeatureDataTb row, FeatureMapping feature) {
        if ("EXTREME_COLD_DAYS".equalsIgnoreCase(feature.getCalculationType()))
            return row.getFeature005();
        if ("HDD".equalsIgnoreCase(feature.getCalculationType()))
            return row.getFeature004();
        String sourceField = feature.getSourceField();
        return switch (sourceField == null ? "" : sourceField.trim().toLowerCase()) {
            case "gas_sales" -> row.getGasSales() == null ? null : row.getGasSales().doubleValue();
            case "temp_max" -> row.getFeature006();
            case "temp_min" -> row.getFeature007();
            case "temp_avg" -> row.getFeature008();
            case "feels_like_max" -> row.getFeature009();
            case "feels_like_min" -> row.getFeature010();
            case "feels_like_avg" -> row.getFeature011();
            case "dew_point" -> row.getFeature012();
            case "humidity" -> row.getFeature013();
            case "precipitation" -> row.getFeature014();
            case "wind_gust" -> row.getFeature015();
            case "wind_speed" -> row.getFeature016();
            case "sea_level_pressure" -> row.getFeature017();
            case "cloud_cover" -> row.getFeature018();
            case "visibility" -> row.getFeature019();
            case "solar_radiation" -> row.getFeature020();
            case "solar_energy" -> row.getFeature021();
            case "uv_index" -> row.getFeature022();
            case "hdd" -> row.getFeature004();
            case "extreme_cold_days" -> row.getFeature005();
            default -> throw new BusinessException("特征来源字段不存在或不支持：" + sourceField);
        };
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class FeatureMapping {
        /** 模型平台请求中的特征键。 */
        private String payloadKey;

        private String sourceField;
        private String calculationType;
        private String missingValueStrategy;
    }
}
