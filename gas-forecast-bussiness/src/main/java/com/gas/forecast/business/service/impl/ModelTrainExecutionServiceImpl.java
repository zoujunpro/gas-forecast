package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gas.forecast.business.component.ModelPlatformClient;
import com.gas.forecast.business.component.dto.ModelTrainApiRequest;
import com.gas.forecast.business.component.dto.ModelTrainApiResponse;
import com.gas.forecast.business.component.dto.ModelTrainResultApiResponse;
import com.gas.forecast.business.component.dto.ModelTrainingValidationApiResponse;
import com.gas.forecast.business.dto.request.ModelTrainExecuteRequest;
import com.gas.forecast.business.dto.request.ModelTrainResultRequest;
import com.gas.forecast.business.dto.response.ModelTrainExecuteResponse;
import com.gas.forecast.business.dto.response.ModelTrainRecordResponse;
import com.gas.forecast.business.dto.response.ModelTrainResultResponse;
import com.gas.forecast.business.enums.BaseCodeType;
import com.gas.forecast.business.enums.ModelTrainStatus;
import com.gas.forecast.business.enums.ModelTrainMode;
import com.gas.forecast.business.service.BaseCodeGenerateService;
import com.gas.forecast.business.service.ModelTrainExecutionService;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.enums.ForecastAgentEnum;
import com.gas.forecast.common.security.context.SecurityContextHolder;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.ModelConfigScopeTb;
import com.gas.forecast.dao.domain.ModelConfigTb;
import com.gas.forecast.dao.domain.ModelFeatureDefinitionTb;
import com.gas.forecast.dao.domain.ModelFeatureRef;
import com.gas.forecast.dao.domain.ModelTrainBacktestTb;
import com.gas.forecast.dao.domain.ModelTrainConfigTb;
import com.gas.forecast.dao.domain.ModelTrainFeatureDataTb;
import com.gas.forecast.dao.domain.ModelTrainRecordTb;
import com.gas.forecast.dao.mapper.ModelConfigScopeTbMapper;
import com.gas.forecast.dao.mapper.ModelConfigTbMapper;
import com.gas.forecast.dao.mapper.ModelFeatureDefinitionTbMapper;
import com.gas.forecast.dao.mapper.ModelFeatureRefMapper;
import com.gas.forecast.dao.mapper.ModelTrainBacktestTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainConfigTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainFeatureDataTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainRecordTbMapper;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
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
    private final ModelPlatformClient modelPlatformClient;

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

        // 3. 按模型特征定义、训练时间和业务范围读取本次训练数据。
        List<FeatureMapping> features = loadFeatureMappings(modelConfig.getId());
        List<ModelTrainFeatureDataTb> trainData = loadTrainData(trainConfig, modelConfig);
        if (trainData.isEmpty()) {
            throw new BusinessException("训练时间范围和模型作用范围内没有可用训练数据");
        }

        // 4. 正式创建批次前先调用模型平台校验数据，校验失败时不产生训练记录。
        ModelTrainApiRequest validationRequest = buildTrainRequest(trainConfig, modelConfig, null, trainData, features);
        modelPlatformClient.validateTrainingData(validationRequest);

        // 5. 新训练生成批次号；失败重试继续使用原批次号，便于覆盖原结果。
        String retryBatchNo = reqDTO.getRetryBatchNo();
        String batchNo = TextUtils.hasText(retryBatchNo) ? retryBatchNo : generateTrainBatchNo();
        ModelTrainApiRequest trainRequest = buildTrainRequest(trainConfig, modelConfig, batchNo, trainData, features);
        // 6. 保存完整请求快照。重试时先抢占失败记录，防止重复提交同一批次。
        JsonNode requestPayload = objectMapper.valueToTree(trainRequest);
        if (TextUtils.hasText(retryBatchNo)) {
            resetFailedTrainDetail(trainConfig, batchNo, ModelTrainStatus.PENDING, requestPayload);
        } else {
            saveTrainDetail(trainConfig, batchNo, ModelTrainStatus.PENDING, requestPayload, null);
        }
        // 7. 根据训练接口是否启用确定初始状态，并保存提交结果说明。
        ModelTrainStatus status = modelPlatformClient.isTrainingEnabled() ? ModelTrainStatus.RUNNING : ModelTrainStatus.PENDING;
        ObjectNode submitResponse = objectMapper.createObjectNode().put("message", modelPlatformClient.isTrainingEnabled() ? "训练任务已提交，后台等待模型系统返回结果。" : "未配置 gas.agent.train-url，已生成训练请求入参。")
                .put("model_code", trainRequest.getModelCode()).put("train_batch_no", batchNo);
        updateTrainDetailAfterSubmit(batchNo, status, submitResponse, null);
        // 8. 当前事务提交成功后异步调用模型平台，避免平台读取到尚未提交的数据。
        submitTrainAfterCommit(batchNo, trainRequest);
        // 9. 立即返回批次信息，实际训练结果由异步任务或平台回调更新。
        return new ModelTrainExecuteResponse(trainConfig.getTrainCode(), batchNo, trainRequest.getModelCode(), trainData.size(), status.getCode(), requestPayload, submitResponse);
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
        if (trainData.isEmpty()) {
            throw new BusinessException("训练时间范围和模型作用范围内没有可用训练数据");
        }
        // 4. 只调用平台预校验，不创建批次和训练记录。
        ModelTrainApiRequest validationRequest = buildTrainRequest(trainConfig, modelConfig, null, trainData, features);
        return modelPlatformClient.validateTrainingData(validationRequest);
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

        // 4. 从保存的请求快照恢复模型和数据量信息，组装接口响应。
        JsonNode requestPayload = parseRequestPayload(detail.getRequestParam());
        String modelCode = requestPayload == null ? null : requestPayload.path("model_code").asText(null);
        return new ModelTrainExecuteResponse(null, batchNo, modelCode, datasetSize(requestPayload), status.getCode(), requestPayload, trainResult);
    }

    @Override
    public ModelTrainResultResponse getTrainResult(ModelTrainResultRequest reqDTO) {
        // 1. 指定批次时查询完整详情；未指定批次时只查询轻量列表字段。
        var query = Wrappers.<ModelTrainRecordTb>lambdaQuery();
        String batchNo = reqDTO.getBatchNo();
        boolean loadDetail = TextUtils.hasText(batchNo);
        if (loadDetail) {
            query.eq(ModelTrainRecordTb::getBatchNo, batchNo);
        } else {
            // 请求和结果字段可能包含完整训练集，列表查询不读取大字段，选中批次后再按需加载。
            query.select(ModelTrainRecordTb::getId, ModelTrainRecordTb::getBatchNo, ModelTrainRecordTb::getAgentCode, ModelTrainRecordTb::getRegionCode, ModelTrainRecordTb::getRegionName,
                    ModelTrainRecordTb::getIndustryCode, ModelTrainRecordTb::getIndustryName, ModelTrainRecordTb::getCustomerCode, ModelTrainRecordTb::getCustomerName,
                    ModelTrainRecordTb::getTrainStartDate, ModelTrainRecordTb::getTrainEndDate, ModelTrainRecordTb::getStatus, ModelTrainRecordTb::getBestModel, ModelTrainRecordTb::getMape,
                    ModelTrainRecordTb::getWmape, ModelTrainRecordTb::getSmape, ModelTrainRecordTb::getRmse, ModelTrainRecordTb::getMae, ModelTrainRecordTb::getR2,
                    ModelTrainRecordTb::getTrainDurationSeconds, ModelTrainRecordTb::getModelVersion, ModelTrainRecordTb::getErrorMessage, ModelTrainRecordTb::getCreatedAt,
                    ModelTrainRecordTb::getUpdatedAt);
        }
        // 2. 追加页面传入的业务范围和训练时间过滤条件。
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
                item.setMessage(detail.getErrorMessage());
            }
            item.setCreatedAt(detail.getCreatedAt());
            item.setUpdatedAt(detail.getUpdatedAt());
            if (loadDetail) {
                // 5. 详情模式额外解析请求和结果 JSON，并限制请求数据集的预览数量。
                JsonNode requestJson = parseRequestPayload(detail.getRequestParam());
                if (requestJson != null) {
                    item.setRequestJson(objectMapper.convertValue(previewRequestPayload(requestJson), ModelTrainApiRequest.class));
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

    private JsonNode previewRequestPayload(JsonNode requestJson) {
        // 非对象或数据量未超限时直接返回原请求，不进行无意义复制。
        if (!requestJson.isObject()) {
            return requestJson;
        }
        JsonNode dataset = requestJson.get("dataset");
        if (dataset == null || !dataset.isArray() || dataset.size() <= RESULT_DATASET_PREVIEW_LIMIT) {
            return requestJson;
        }
        // 复制除 dataset 外的请求字段，保证预览仍包含完整配置上下文。
        ObjectNode preview = objectMapper.createObjectNode();
        requestJson.fields().forEachRemaining(entry -> {
            if (!"dataset".equals(entry.getKey())) {
                preview.set(entry.getKey(), entry.getValue());
            }
        });
        // dataset 只保留前 N 条，并附带原始总数和截断标记。
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
        List<FeatureMapping> mappings = refs.stream().map(ref -> definitions.get(ref.getFeatureId()))
                .filter(definition -> definition != null && TextUtils.hasText(definition.getFeatureCode()) && TextUtils.hasText(definition.getFeatureColumn()))
                .map(definition -> new FeatureMapping(definition.getFeatureCode(), definition.getFeatureColumn())).toList();
        if (mappings.isEmpty()) {
            throw new BusinessException("所属模型没有可用训练特征");
        }
        return mappings;
    }

    private List<ModelTrainFeatureDataTb> loadTrainData(ModelTrainConfigTb trainConfig, ModelConfigTb modelConfig) {
        // 1. 训练数据严格使用训练配置中已经校验通过的时间粒度。
        var query = Wrappers.<ModelTrainFeatureDataTb>lambdaQuery();
        String timeGranularity = trainConfig.getTimeGranularity();
        if (!TextUtils.hasText(timeGranularity)) {
            throw new BusinessException("训练配置的时间粒度不能为空");
        }
        query.eq(ModelTrainFeatureDataTb::getTimeGranularity, timeGranularity);
        // 2. 指定范围模式按起止日期过滤；最近模式由数量限制决定范围。
        boolean recentMode = ModelTrainMode.RECENT.name().equals(trainConfig.getTrainMode());
        if (!recentMode && TextUtils.hasText(trainConfig.getTrainStartDate())) {
            query.ge(ModelTrainFeatureDataTb::getStatDate, trainConfig.getTrainStartDate());
        }
        if (!recentMode && TextUtils.hasText(trainConfig.getTrainEndDate())) {
            query.le(ModelTrainFeatureDataTb::getStatDate, trainConfig.getTrainEndDate());
        }
        // 3. 叠加训练配置或模型默认作用范围。
        applyTrainScope(query, trainConfig, modelConfig.getId());
        if (recentMode) {
            // 4. 最近模式先倒序截取最新 N 条，再恢复为时间正序供模型训练。
            Integer recentPeriods = trainConfig.getRecentPeriods();
            if (recentPeriods == null || recentPeriods <= 0) {
                throw new BusinessException("训练配置的最近周期数无效，请先修改训练配置");
            }
            query.orderByDesc(ModelTrainFeatureDataTb::getStatDate).orderByDesc(ModelTrainFeatureDataTb::getId).last("limit " + recentPeriods);
            return modelTrainFeatureDataTbMapper.selectList(query).stream().sorted(Comparator.comparing(ModelTrainFeatureDataTb::getStatDate).thenComparing(ModelTrainFeatureDataTb::getId)).toList();
        }
        // 5. 指定范围模式直接按日期正序返回全部匹配数据。
        query.orderByAsc(ModelTrainFeatureDataTb::getStatDate).orderByAsc(ModelTrainFeatureDataTb::getId);
        return modelTrainFeatureDataTbMapper.selectList(query);
    }

    private void applyTrainScope(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ModelTrainFeatureDataTb> query, ModelTrainConfigTb trainConfig, Long modelId) {
        // 1. 训练配置明确指定范围时，优先使用配置自身的地区、行业和客户条件。
        if (TextUtils.hasText(trainConfig.getRegionCode())) {
            query.eq(ModelTrainFeatureDataTb::getRegionCode, trainConfig.getRegionCode());
        }
        if (TextUtils.hasText(trainConfig.getIndustryCode())) {
            query.eq(ModelTrainFeatureDataTb::getIndustryCode, trainConfig.getIndustryCode());
        }
        if (TextUtils.hasText(trainConfig.getCustomerCode())) {
            query.eq(ModelTrainFeatureDataTb::getCustomerCode, trainConfig.getCustomerCode());
        }
        // 任意一个范围条件存在即表示配置已明确范围，不再叠加模型默认范围。
        if (TextUtils.hasText(trainConfig.getRegionCode()) || TextUtils.hasText(trainConfig.getIndustryCode()) || TextUtils.hasText(trainConfig.getCustomerCode())) {
            return;
        }

        // 2. 配置未指定范围时，读取模型绑定的全部默认作用范围。
        List<ModelConfigScopeTb> scopes = modelConfigScopeTbMapper.selectList(Wrappers.<ModelConfigScopeTb>lambdaQuery().eq(ModelConfigScopeTb::getModelId, modelId));
        List<String> regionCodes = scopes.stream().map(ModelConfigScopeTb::getRegionCode).filter(TextUtils::hasText).distinct().toList();
        List<String> industryCodes = scopes.stream().map(ModelConfigScopeTb::getIndustryCode).filter(TextUtils::hasText).distinct().toList();
        List<String> customerCodes = scopes.stream().map(ModelConfigScopeTb::getCustomerCode).filter(TextUtils::hasText).distinct().toList();
        // 3. 每个维度只在存在有效值时追加 IN 条件。
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

    private ModelTrainApiRequest buildTrainRequest(ModelTrainConfigTb trainConfig, ModelConfigTb modelConfig, String batchNo, List<ModelTrainFeatureDataTb> trainData, List<FeatureMapping> features) {
        // 1. 将标准训练数据逐行转换成模型平台约定的 dataset 结构。
        List<JsonNode> dataset = new ArrayList<>();
        for (ModelTrainFeatureDataTb row : trainData) {
            // 固定输出日期和目标销量，再按照模型特征映射动态读取 feature_xxx 字段。
            ObjectNode item = objectMapper.createObjectNode();
            item.put("date", row.getStatDate());
            putDecimal(item, "gas_sales", row.getGasSales());
            for (FeatureMapping feature : features) {
                Double value = readFeatureValue(row, feature.getFeatureColumn());
                if (value != null) {
                    item.put(feature.getPayloadKey(), value);
                }
            }
            dataset.add(item);
        }
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
        request.setParams(objectMapper.createObjectNode());
        request.setDataset(dataset);
        return request;
    }

    private ModelTrainApiResponse callTrainAgent(ModelTrainApiRequest trainRequest) {
        // 统一通过模型平台服务发送请求，当前业务类不直接依赖 HTTP 地址和客户端。
        return modelPlatformClient.train(trainRequest);
    }

    private void submitTrainAfterCommit(String batchNo, ModelTrainApiRequest trainRequest) {
        // 1. 未配置训练接口时保留本地待处理记录，不启动异步任务。
        if (!modelPlatformClient.isTrainingEnabled()) {
            return;
        }
        // 2. 将远程训练包装为线程池任务，避免阻塞接口请求线程。
        Runnable submitTask = () -> modelTrainTaskExecutor.execute(() -> waitTrainResultAndPersist(batchNo, trainRequest));
        // 3. 存在事务时必须等事务提交成功后调用平台，事务回滚则不提交训练。
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submitTask.run();
                }
            });
            return;
        }
        // 4. 没有活动事务时直接提交异步任务。
        submitTask.run();
    }

    private void waitTrainResultAndPersist(String batchNo, ModelTrainApiRequest trainRequest) {
        try {
            // 1. 调用平台并将标准响应信封转换为训练结果数据。
            ModelTrainApiResponse agentResponse = callTrainAgent(trainRequest);
            JsonNode trainResult = normalizeTrainResult(batchNo, agentResponse);
            // 2. 异步线程不继承原事务，使用独立事务保存成功结果和回测明细。
            transactionTemplate.executeWithoutResult(status -> updateTrainResult(trainResult));
        } catch (RuntimeException e) {
            // 3. 远程调用或结果处理失败时提取平台消息，并在独立事务中标记批次失败。
            LOGGER.error("model train failed batchNo={}", batchNo, e);
            String message = modelPlatformClient.extractErrorMessage(e, "模型训练失败");
            ObjectNode failedResult = objectMapper.createObjectNode().put("train_batch_no", batchNo).put("status", ModelTrainStatus.FAILED.getCode()).put("message", message);
            transactionTemplate.executeWithoutResult(status -> updateTrainDetailAfterSubmit(batchNo, ModelTrainStatus.FAILED, failedResult, message));
        }
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
                Wrappers.<ModelTrainRecordTb>lambdaUpdate().eq(ModelTrainRecordTb::getBatchNo, batchNo).eq(ModelTrainRecordTb::getStatus, ModelTrainStatus.FAILED.getCode())
                        .set(ModelTrainRecordTb::getStatus, status.getCode()).set(ModelTrainRecordTb::getErrorMessage, null).set(ModelTrainRecordTb::getUpdatedAt, now));
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

    private Double readFeatureValue(ModelTrainFeatureDataTb row, String featureColumn) {
        // 1. 将数据库 feature_xxx 字段名转换为 Java Bean 属性名。
        String property = toFeatureProperty(featureColumn);
        try {
            // 2. 通过 Getter 动态读取模型配置指定的特征列。
            Method method = ModelTrainFeatureDataTb.class.getMethod("get" + property);
            Object value = method.invoke(row);
            return value instanceof Number number ? number.doubleValue() : null;
        } catch (ReflectiveOperationException e) {
            throw new BusinessException("训练特征字段不存在：" + featureColumn);
        }
    }

    private String toFeatureProperty(String featureColumn) {
        // 只允许 feature_加三位数字，防止错误配置访问任意实体方法。
        String column = featureColumn == null ? "" : featureColumn.trim().toLowerCase();
        if (!column.matches("feature_\\d{3}")) {
            throw new BusinessException("训练特征字段格式不合法：" + featureColumn);
        }
        return "Feature" + column.substring("feature_".length());
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class FeatureMapping {
        /** 模型平台请求中的特征键。 */
        private String payloadKey;

        /** 训练特征数据表中的物理字段。 */
        private String featureColumn;
    }
}
