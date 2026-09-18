package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gas.forecast.business.dto.req.ModelTrainExecuteReqDTO;
import com.gas.forecast.business.dto.resp.ModelTrainExecuteRespDTO;
import com.gas.forecast.business.enums.BaseCodeType;
import com.gas.forecast.business.service.BaseCodeGenerateService;
import com.gas.forecast.business.service.ModelTrainExecutionService;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.util.HttpUtil;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.ModelConfigScopeTb;
import com.gas.forecast.dao.domain.ModelConfigTb;
import com.gas.forecast.dao.domain.ModelFeatureDefinitionTb;
import com.gas.forecast.dao.domain.ModelFeatureRef;
import com.gas.forecast.dao.domain.ModelTrainBatchTb;
import com.gas.forecast.dao.domain.ModelTrainConfigTb;
import com.gas.forecast.dao.domain.ModelTrainFeatureDataTb;
import com.gas.forecast.dao.mapper.ModelConfigScopeTbMapper;
import com.gas.forecast.dao.mapper.ModelConfigTbMapper;
import com.gas.forecast.dao.mapper.ModelFeatureDefinitionTbMapper;
import com.gas.forecast.dao.mapper.ModelFeatureRefMapper;
import com.gas.forecast.dao.mapper.ModelTrainBatchTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainConfigTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainFeatureDataTbMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ModelTrainExecutionServiceImpl implements ModelTrainExecutionService {

    private final ModelTrainConfigTbMapper modelTrainConfigTbMapper;
    private final ModelConfigTbMapper modelConfigTbMapper;
    private final ModelConfigScopeTbMapper modelConfigScopeTbMapper;
    private final ModelFeatureRefMapper modelFeatureRefMapper;
    private final ModelFeatureDefinitionTbMapper modelFeatureDefinitionTbMapper;
    private final ModelTrainFeatureDataTbMapper modelTrainFeatureDataTbMapper;
    private final ModelTrainBatchTbMapper modelTrainBatchTbMapper;
    private final BaseCodeGenerateService baseCodeGenerateService;
    private final ObjectMapper objectMapper;

    @Value("${gas.agent.train-url:http://127.0.0.1:8090/api/v1/train}")
    private String trainUrl;

    public ModelTrainExecutionServiceImpl(ModelTrainConfigTbMapper modelTrainConfigTbMapper,
                                          ModelConfigTbMapper modelConfigTbMapper,
                                          ModelConfigScopeTbMapper modelConfigScopeTbMapper,
                                          ModelFeatureRefMapper modelFeatureRefMapper,
                                          ModelFeatureDefinitionTbMapper modelFeatureDefinitionTbMapper,
                                          ModelTrainFeatureDataTbMapper modelTrainFeatureDataTbMapper,
                                          ModelTrainBatchTbMapper modelTrainBatchTbMapper,
                                          BaseCodeGenerateService baseCodeGenerateService,
                                          ObjectMapper objectMapper) {
        this.modelTrainConfigTbMapper = modelTrainConfigTbMapper;
        this.modelConfigTbMapper = modelConfigTbMapper;
        this.modelConfigScopeTbMapper = modelConfigScopeTbMapper;
        this.modelFeatureRefMapper = modelFeatureRefMapper;
        this.modelFeatureDefinitionTbMapper = modelFeatureDefinitionTbMapper;
        this.modelTrainFeatureDataTbMapper = modelTrainFeatureDataTbMapper;
        this.modelTrainBatchTbMapper = modelTrainBatchTbMapper;
        this.baseCodeGenerateService = baseCodeGenerateService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public ModelTrainExecuteRespDTO execute(ModelTrainExecuteReqDTO reqDTO) {
        ModelTrainConfigTb trainConfig = modelTrainConfigTbMapper.selectOne(Wrappers.<ModelTrainConfigTb>lambdaQuery()
                .eq(ModelTrainConfigTb::getConfigCode, reqDTO.configCode()));
        if (trainConfig == null) {
            throw new BusinessException("训练配置不存在");
        }
        if (!TextUtils.hasText(trainConfig.getModelCode())) {
            throw new BusinessException("训练配置未配置所属模型");
        }

        ModelConfigTb modelConfig = modelConfigTbMapper.selectOne(Wrappers.<ModelConfigTb>lambdaQuery()
                .eq(ModelConfigTb::getModelCode, trainConfig.getModelCode()));
        if (modelConfig == null) {
            throw new BusinessException("所属模型不存在");
        }

        List<FeatureMapping> features = loadFeatureMappings(modelConfig.getId());
        List<ModelTrainFeatureDataTb> trainData = loadTrainData(trainConfig, modelConfig, features);
        if (trainData.isEmpty()) {
            throw new BusinessException("训练时间范围和模型作用范围内没有可用训练数据");
        }

        String batchNo = baseCodeGenerateService.nextCode(BaseCodeType.TRAIN_BATCH);
        ObjectNode payload = buildPayload(modelConfig.getModelCode(), batchNo, trainData, features);
        JsonNode agentResponse = callTrainAgent(payload);
        String status = agentResponse == null ? "PENDING" : "RUNNING";
        saveBatch(trainConfig, modelConfig, batchNo, status, payload, agentResponse);
        return new ModelTrainExecuteRespDTO(
                trainConfig.getConfigCode(),
                batchNo,
                modelConfig.getModelCode(),
                trainData.size(),
                status,
                payload,
                agentResponse
        );
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
        String timeGranularity = resolveTimeGranularity(features);
        if (TextUtils.hasText(timeGranularity)) {
            query.eq(ModelTrainFeatureDataTb::getTimeGranularity, timeGranularity);
        }
        if (TextUtils.hasText(trainConfig.getTrainStartDate())) {
            query.ge(ModelTrainFeatureDataTb::getStatDate, trainConfig.getTrainStartDate());
        }
        if (TextUtils.hasText(trainConfig.getTrainEndDate())) {
            query.le(ModelTrainFeatureDataTb::getStatDate, trainConfig.getTrainEndDate());
        }
        applyTrainScope(query, trainConfig, modelConfig.getModelCode());
        query.orderByAsc(ModelTrainFeatureDataTb::getStatDate).orderByAsc(ModelTrainFeatureDataTb::getId);
        return modelTrainFeatureDataTbMapper.selectList(query);
    }

    private void applyTrainScope(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ModelTrainFeatureDataTb> query,
                                 ModelTrainConfigTb trainConfig,
                                 String modelCode) {
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
                .eq(ModelConfigScopeTb::getModelCode, modelCode));
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

    private ObjectNode buildPayload(String modelCode,
                                    String batchNo,
                                    List<ModelTrainFeatureDataTb> trainData,
                                    List<FeatureMapping> features) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("modelCode", modelCode);
        payload.put("train_batch_no", batchNo);
        ArrayNode dataset = objectMapper.createArrayNode();
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
        payload.set("dataset", dataset);
        return payload;
    }

    private JsonNode callTrainAgent(ObjectNode payload) {
        if (!TextUtils.hasText(trainUrl)) {
            return null;
        }
        return HttpUtil.postJson(trainUrl, payload);
    }

    private void saveBatch(ModelTrainConfigTb trainConfig,
                           ModelConfigTb modelConfig,
                           String batchNo,
                           String status,
                           JsonNode payload,
                           JsonNode agentResponse) {
        Date now = new Date();
        ModelTrainBatchTb batch = new ModelTrainBatchTb();
        batch.setBatchNo(batchNo);
        batch.setAgentCode(trainConfig.getAgentCode());
        batch.setRegionCode(trainConfig.getRegionCode());
        batch.setRegionName(trainConfig.getRegionName());
        batch.setCustomerCode(trainConfig.getCustomerCode());
        batch.setCustomerName(trainConfig.getCustomerName());
        batch.setIndustryCode(trainConfig.getIndustryCode());
        batch.setIndustryName(trainConfig.getIndustryName());
        batch.setTrainStartDate(trainConfig.getTrainStartDate());
        batch.setTrainEndDate(trainConfig.getTrainEndDate());
        batch.setStatus(status);
        batch.setConfigJson(payload);
        batch.setResultJson(agentResponse == null ? objectMapper.createObjectNode()
                .put("message", "未配置 gas.agent.train-url，已生成训练请求入参。")
                .put("model_code", modelConfig.getModelCode()) : agentResponse);
        batch.setCreatedAt(now);
        batch.setUpdatedAt(now);
        batch.setStartedAt(now);
        modelTrainBatchTbMapper.insert(batch);
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
