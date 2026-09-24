package com.gas.forecast.business.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gas.forecast.business.component.ModelPlatformClient;
import com.gas.forecast.business.component.dto.ModelPredictApiRequest;
import com.gas.forecast.business.component.dto.ModelPredictApiResponse;
import com.gas.forecast.business.dto.request.ModelForecastBatchRequest;
import com.gas.forecast.business.dto.request.ModelForecastConfigCreateRequest;
import com.gas.forecast.business.dto.request.ModelForecastConfigPageRequest;
import com.gas.forecast.business.dto.request.ModelForecastConfigSaveRequest;
import com.gas.forecast.business.dto.request.ModelForecastConfigUpdateRequest;
import com.gas.forecast.business.dto.request.ModelForecastExecuteRequest;
import com.gas.forecast.business.dto.request.ModelForecastRecordPageRequest;
import com.gas.forecast.business.dto.request.ModelForecastResultPageRequest;
import com.gas.forecast.business.dto.response.ModelForecastExecuteResponse;
import com.gas.forecast.business.dto.response.ModelForecastHistoryPointResponse;
import com.gas.forecast.business.enums.ModelForecastStatus;
import com.gas.forecast.business.enums.ModelTrainTimeGranularity;
import com.gas.forecast.business.enums.ModelTrainStatus;
import com.gas.forecast.business.util.PageUtils;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.core.PageInfoDTO;
import com.gas.forecast.common.security.context.SecurityContextHolder;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.ModelFeatureDefinitionTb;
import com.gas.forecast.dao.domain.ModelFeatureRef;
import com.gas.forecast.dao.domain.ModelForecastConfigTb;
import com.gas.forecast.dao.domain.ModelForecastRecordTb;
import com.gas.forecast.dao.domain.ModelForecastResultTb;
import com.gas.forecast.dao.domain.ModelTrainBacktestTb;
import com.gas.forecast.dao.domain.ModelTrainConfigTb;
import com.gas.forecast.dao.domain.ModelTrainFeatureDataTb;
import com.gas.forecast.dao.domain.ModelTrainRecordTb;
import com.gas.forecast.dao.mapper.ModelFeatureDefinitionTbMapper;
import com.gas.forecast.dao.mapper.ModelFeatureRefMapper;
import com.gas.forecast.dao.mapper.ModelForecastConfigTbMapper;
import com.gas.forecast.dao.mapper.ModelForecastRecordTbMapper;
import com.gas.forecast.dao.mapper.ModelForecastResultTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainBacktestTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainConfigTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainFeatureDataTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainRecordTbMapper;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ModelForecastManagementService {
    private final ModelForecastConfigTbMapper configMapper;
    private final ModelForecastRecordTbMapper recordMapper;
    private final ModelForecastResultTbMapper resultMapper;
    private final ModelTrainConfigTbMapper trainConfigMapper;
    private final ModelTrainBacktestTbMapper trainBacktestMapper;
    private final ModelTrainRecordTbMapper trainDetailMapper;
    private final ModelFeatureRefMapper featureRefMapper;
    private final ModelFeatureDefinitionTbMapper featureDefinitionMapper;
    private final ModelTrainFeatureDataTbMapper featureDataMapper;
    private final ObjectMapper objectMapper;
    private final ModelPlatformClient modelPlatformClient;

    public PageInfoDTO<ModelForecastConfigTb> listConfigs(ModelForecastConfigPageRequest request) {
        var query = Wrappers.<ModelForecastConfigTb>lambdaQuery();
        String keyword = request.getKeyword();
        if (TextUtils.hasText(keyword)) {
            query.like(ModelForecastConfigTb::getForecastName, keyword);
        }
        eqText(query, ModelForecastConfigTb::getAgentCode, request.getAgentCode());
        if (request.getEnabled() != null)
            query.eq(ModelForecastConfigTb::getEnabled, request.getEnabled());
        query.orderByDesc(ModelForecastConfigTb::getUpdatedAt).orderByDesc(ModelForecastConfigTb::getId);
        int page = positive(request.getPage(), 1);
        int size = positive(request.getSize(), 20);
        IPage<ModelForecastConfigTb> result = configMapper.selectPage(PageUtils.pageRequest(page, size), query);
        return PageUtils.toPage(result, result.getRecords());
    }

    @Transactional
    public ModelForecastConfigTb saveConfig(ModelForecastConfigSaveRequest request) {
        Long id = request.getId();
        ModelForecastConfigTb entity = id == null ? new ModelForecastConfigTb() : configMapper.selectById(id);
        if (id != null && entity == null)
            throw new BusinessException("预测配置不存在");
        String name = request.getForecastName();
        if (!TextUtils.hasText(name))
            throw new BusinessException("预测名称不能为空");
        String forecastStartDate = request.getForecastStartDate();
        if (!TextUtils.hasText(forecastStartDate))
            throw new BusinessException("预测开始日期不能为空");
        try {
            java.time.LocalDate.parse(forecastStartDate);
        } catch (java.time.format.DateTimeParseException exception) {
            throw new BusinessException("预测开始日期格式必须为 yyyy-MM-dd");
        }
        ModelTrainConfigTb trainConfig = requireTrainConfig(null, request.getTrainConfigCode());
        String agentCode = trainConfig.getAgentCode();
        Date now = new Date();
        if (id == null) {
            entity.setCreatedAt(now);
            entity.setCreatedBy(String.valueOf(SecurityContextHolder.getUserId()));
            entity.setCreatedByName(SecurityContextHolder.getUserName());
        }
        entity.setForecastName(name);
        entity.setAgentCode(agentCode);
        entity.setScopeType(resolveScopeType(trainConfig));
        entity.setRegionCode(trainConfig.getRegionCode());
        entity.setRegionName(trainConfig.getRegionName());
        entity.setIndustryCode(trainConfig.getIndustryCode());
        entity.setIndustryName(trainConfig.getIndustryName());
        entity.setCustomerCode(trainConfig.getCustomerCode());
        entity.setCustomerName(trainConfig.getCustomerName());
        entity.setForecastStartDate(forecastStartDate);
        entity.setForecastHorizon(positive(request.getForecastHorizon(), 12));
        entity.setForecastFrequency(defaultText(request.getForecastFrequency(), "MANUAL"));
        entity.setAutoForecast(0);
        entity.setTrainConfigId(trainConfig.getId());
        entity.setTrainConfigCode(trainConfig.getTrainCode());
        entity.setEnabled(request.getEnabled() == null ? 1 : request.getEnabled());
        entity.setRemark(request.getRemark());
        entity.setUpdatedAt(now);
        if (id == null)
            configMapper.insert(entity);
        else
            configMapper.updateById(entity);
        return configMapper.selectById(entity.getId());
    }

    @Transactional
    public ModelForecastConfigTb createConfig(ModelForecastConfigCreateRequest request) {
        return saveConfig(objectMapper.convertValue(request, ModelForecastConfigSaveRequest.class));
    }

    @Transactional
    public ModelForecastConfigTb updateConfig(ModelForecastConfigUpdateRequest request) {
        return saveConfig(objectMapper.convertValue(request, ModelForecastConfigSaveRequest.class));
    }

    private ModelTrainConfigTb requireTrainConfig(Long trainConfigId, String trainConfigCode) {
        if (trainConfigId == null && !TextUtils.hasText(trainConfigCode))
            throw new BusinessException("请选择模型训练配置");
        ModelTrainConfigTb trainConfig = trainConfigId == null
                ? trainConfigMapper.selectOne(Wrappers.<ModelTrainConfigTb>lambdaQuery().eq(ModelTrainConfigTb::getTrainCode, trainConfigCode).last("limit 1"))
                : trainConfigMapper.selectById(trainConfigId);
        if (trainConfig == null)
            throw new BusinessException("模型训练配置不存在");
        if (trainConfig.getEnabled() != null && trainConfig.getEnabled() == 0)
            throw new BusinessException("模型训练配置已停用");
        return trainConfig;
    }

    private String resolveScopeType(ModelTrainConfigTb config) {
        if (TextUtils.hasText(config.getCustomerCode()))
            return "CUSTOMER";
        if (TextUtils.hasText(config.getIndustryCode()))
            return "INDUSTRY";
        if (TextUtils.hasText(config.getRegionCode()))
            return "REGION";
        return "ALL";
    }

    private String latestSuccessfulBatch(ModelTrainConfigTb config) {
        var query = Wrappers.<ModelTrainRecordTb>lambdaQuery().eq(ModelTrainRecordTb::getTrainConfigId, config.getId()).eq(ModelTrainRecordTb::getStatus, ModelTrainStatus.SUCCESS.getCode());
        query.orderByDesc(ModelTrainRecordTb::getUpdatedAt).orderByDesc(ModelTrainRecordTb::getId).last("limit 1");
        ModelTrainRecordTb detail = trainDetailMapper.selectOne(query);
        return detail == null ? null : detail.getBatchNo();
    }

    @Transactional
    public void deleteConfig(Long id) {
        configMapper.deleteById(id);
    }

    public ModelForecastExecuteResponse execute(ModelForecastExecuteRequest request) {
        Long forecastId = request.getForecastId();
        ModelForecastConfigTb config = configMapper.selectById(forecastId);
        if (config == null)
            throw new BusinessException("预测配置不存在");
        if (config.getEnabled() != null && config.getEnabled() == 0)
            throw new BusinessException("预测配置已停用");
        JsonNode dataset = objectMapper.valueToTree(request.getDataset());
        for (JsonNode item : dataset) {
            if (!item.isObject() || !TextUtils.hasText(item.path("date").asText())) {
                throw new BusinessException("每条特征数据都必须是对象并包含 date 字段");
            }
            try {
                LocalDate.parse(item.path("date").asText());
            } catch (java.time.format.DateTimeParseException exception) {
                throw new BusinessException("特征数据 date 字段格式必须为 yyyy-MM-dd");
            }
        }
        ModelTrainConfigTb trainConfig = requireTrainConfig(config.getTrainConfigId(), config.getTrainConfigCode());
        String trainBatchNo = latestSuccessfulBatch(trainConfig);
        if (!TextUtils.hasText(trainBatchNo))
            throw new BusinessException("当前训练配置没有成功的模型训练批次");
        ModelForecastRecordTb record = prepareForecastRecord(config, request.getRetryBatchNo());
        String forecastBatchNo = record.getForecastBatchNo();
        try {
            ModelPredictApiRequest payload = buildPredictPayload(config, trainConfig, trainBatchNo, forecastBatchNo, dataset);
            record.setFeatureSnapshot(payload.getDataset().toString().getBytes(StandardCharsets.UTF_8));
            record.setRequestParam(objectMapper.valueToTree(payload).toString().getBytes(StandardCharsets.UTF_8));
            record.setUpdatedAt(new Date());
            recordMapper.updateById(record);
            ModelPredictApiResponse response = modelPlatformClient.predict(payload);
            if (response.getPoints() == null || !response.getPoints().isArray())
                throw new BusinessException("模型平台预测响应缺少 points");
            saveForecastResults(forecastBatchNo, response.getPoints());
            record.setResponseParam(objectMapper.valueToTree(response).toString().getBytes(StandardCharsets.UTF_8));
            record.setStatus(ModelForecastStatus.SUCCESS.getCode());
            record.setForecastEndTime(java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            record.setUpdatedAt(new Date());
            recordMapper.updateById(record);
            return new ModelForecastExecuteResponse(forecastId, forecastBatchNo, response.getPoints().size());
        } catch (RuntimeException exception) {
            record.setStatus(ModelForecastStatus.FAILED.getCode());
            record.setResponseParam(exception.getMessage() == null ? null : exception.getMessage().getBytes(StandardCharsets.UTF_8));
            record.setForecastEndTime(java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            record.setUpdatedAt(new Date());
            recordMapper.updateById(record);
            throw exception;
        }
    }

    private ModelForecastRecordTb prepareForecastRecord(ModelForecastConfigTb config, String retryBatchNo) {
        if (!TextUtils.hasText(retryBatchNo))
            return createForecastRecord(config);
        Date now = new Date();
        int updated = recordMapper.update(null,
                Wrappers.<ModelForecastRecordTb>lambdaUpdate().eq(ModelForecastRecordTb::getForecastBatchNo, retryBatchNo).eq(ModelForecastRecordTb::getForecastId, config.getId())
                        .eq(ModelForecastRecordTb::getStatus, ModelForecastStatus.FAILED.getCode()).set(ModelForecastRecordTb::getStatus, ModelForecastStatus.RUNNING.getCode())
                        .set(ModelForecastRecordTb::getResponseParam, null).set(ModelForecastRecordTb::getForecastEndTime, null).set(ModelForecastRecordTb::getUpdatedAt, now));
        if (updated != 1) {
            throw new BusinessException("仅预测失败的批次可以重新预测，请刷新后重试");
        }
        ModelForecastRecordTb record = recordMapper.selectOne(
                Wrappers.<ModelForecastRecordTb>lambdaQuery().eq(ModelForecastRecordTb::getForecastBatchNo, retryBatchNo).eq(ModelForecastRecordTb::getForecastId, config.getId()).last("limit 1"));
        if (record == null)
            throw new BusinessException("原预测批次不存在：" + retryBatchNo);
        return record;
    }

    private ModelForecastRecordTb createForecastRecord(ModelForecastConfigTb config) {
        Date now = new Date();
        ModelForecastRecordTb record = new ModelForecastRecordTb();
        record.setForecastId(config.getId());
        record.setForecastStartDate(config.getForecastStartDate());
        record.setForecastHorizon(config.getForecastHorizon());
        record.setForecastFrequency(config.getForecastFrequency());
        record.setAgentCode(config.getAgentCode());
        record.setScopeType(config.getScopeType());
        record.setRegionCode(config.getRegionCode());
        record.setRegionName(config.getRegionName());
        record.setIndustryCode(config.getIndustryCode());
        record.setIndustryName(config.getIndustryName());
        record.setCustomerCode(config.getCustomerCode());
        record.setCustomerName(config.getCustomerName());
        record.setAutoForecast(config.getAutoForecast());
        record.setStatus(ModelForecastStatus.RUNNING.getCode());
        record.setFeatureSnapshot(null);
        record.setRequestParam(null);
        record.setResponseParam(null);
        record.setForecastEndTime(null);
        record.setRemark(config.getRemark());
        record.setCreatedBy(String.valueOf(SecurityContextHolder.getUserId()));
        record.setCreatedByName(SecurityContextHolder.getUserName());
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        recordMapper.insert(record);
        record.setForecastBatchNo("FORECAST-" + java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + String.format("%06d", record.getId()));
        recordMapper.updateById(record);
        return record;
    }

    private ModelPredictApiRequest buildPredictPayload(ModelForecastConfigTb config, ModelTrainConfigTb trainConfig, String trainBatchNo, String forecastBatchNo, JsonNode dataset) {
        ModelPredictApiRequest payload = new ModelPredictApiRequest();
        payload.setModelCode(trainConfig.getModelCode());
        payload.setTrainBatchNo(trainBatchNo);
        payload.setForecastBatchNo(forecastBatchNo);
        payload.setForecastHorizon(config.getForecastHorizon());
        payload.setForecastUnit(switch (config.getForecastFrequency()) {
            case "TENDAY" -> "tenday";
            case "MONTHLY" -> "month";
            default -> "day";
        });
        payload.setRegionCode(trainConfig.getRegionCode());
        payload.setParams(objectMapper.createObjectNode());
        payload.setDataset(dataset.deepCopy());
        return payload;
    }

    private ArrayNode buildFutureDataset(ModelForecastConfigTb config, ModelTrainConfigTb trainConfig) {
        var query = Wrappers.<ModelTrainFeatureDataTb>lambdaQuery().eq(ModelTrainFeatureDataTb::getTimeGranularity, frequencyToGranularity(config.getForecastFrequency()))
                .ge(ModelTrainFeatureDataTb::getStatDate, config.getForecastStartDate());
        eqText(query, ModelTrainFeatureDataTb::getRegionCode, trainConfig.getRegionCode());
        eqText(query, ModelTrainFeatureDataTb::getIndustryCode, trainConfig.getIndustryCode());
        eqText(query, ModelTrainFeatureDataTb::getCustomerCode, trainConfig.getCustomerCode());
        query.orderByAsc(ModelTrainFeatureDataTb::getStatDate).orderByAsc(ModelTrainFeatureDataTb::getId).last("limit " + config.getForecastHorizon());
        List<ModelTrainFeatureDataTb> rows = featureDataMapper.selectList(query);
        if (rows.size() < config.getForecastHorizon()) {
            throw new BusinessException("预测开始日期之后的特征数据不足，需要 " + config.getForecastHorizon() + " 条，实际 " + rows.size() + " 条");
        }
        List<ModelFeatureRef> refs = featureRefMapper
                .selectList(Wrappers.<ModelFeatureRef>lambdaQuery().eq(ModelFeatureRef::getModelId, trainConfig.getModelId()).orderByAsc(ModelFeatureRef::getFeatureOrder));
        Map<Long, ModelFeatureDefinitionTb> definitions = featureDefinitionMapper.selectBatchIds(refs.stream().map(ModelFeatureRef::getFeatureId).distinct().toList()).stream()
                .collect(Collectors.toMap(ModelFeatureDefinitionTb::getId, Function.identity()));
        ArrayNode dataset = objectMapper.createArrayNode();
        for (ModelTrainFeatureDataTb row : rows) {
            ObjectNode item = objectMapper.createObjectNode().put("date", row.getStatDate());
            for (ModelFeatureRef ref : refs) {
                ModelFeatureDefinitionTb definition = definitions.get(ref.getFeatureId());
                if (definition == null || !TextUtils.hasText(definition.getFeatureColumn()))
                    continue;
                Double value = readFeatureValue(row, definition.getFeatureColumn());
                if (value != null)
                    item.put(normalizeFeatureKey(definition.getFeatureCode()), value);
            }
            dataset.add(item);
        }
        return dataset;
    }

    private void saveForecastResults(String batchNo, JsonNode points) {
        resultMapper.delete(Wrappers.<ModelForecastResultTb>lambdaQuery().eq(ModelForecastResultTb::getForecastBatchNo, batchNo));
        Date now = new Date();
        for (JsonNode point : points) {
            ModelForecastResultTb result = new ModelForecastResultTb();
            result.setForecastBatchNo(batchNo);
            result.setForecastDate(point.path("forecast_date").asText());
            result.setForecastValue(decimal(point.get("prediction")));
            result.setCreatedAt(now);
            resultMapper.insert(result);
        }
    }

    private BigDecimal decimal(JsonNode value) {
        return value == null || value.isNull() ? null : value.decimalValue();
    }

    private String frequencyToGranularity(String frequency) {
        return switch (frequency) {
            case "TENDAY" -> ModelTrainTimeGranularity.TENDAY.name();
            case "MONTHLY" -> ModelTrainTimeGranularity.MONTH.name();
            default -> ModelTrainTimeGranularity.DAY.name();
        };
    }

    private Double readFeatureValue(ModelTrainFeatureDataTb row, String column) {
        try {
            String property = column.replaceAll("_([a-zA-Z0-9])", "$1");
            StringBuilder camel = new StringBuilder();
            boolean upper = false;
            for (char ch : column.toCharArray()) {
                if (ch == '_') {
                    upper = true;
                    continue;
                }
                camel.append(upper ? Character.toUpperCase(ch) : ch);
                upper = false;
            }
            Method getter = ModelTrainFeatureDataTb.class.getMethod("get" + Character.toUpperCase(camel.charAt(0)) + camel.substring(1));
            Object value = getter.invoke(row);
            return value instanceof Number number ? number.doubleValue() : null;
        } catch (Exception exception) {
            throw new BusinessException("无法读取预测特征字段：" + column);
        }
    }

    private String normalizeFeatureKey(String value) {
        return value == null ? "" : value.trim().toLowerCase().replace('-', '_').replace(' ', '_');
    }

    public PageInfoDTO<ModelForecastResultTb> listResults(ModelForecastResultPageRequest request) {
        var query = Wrappers.<ModelForecastResultTb>lambdaQuery();
        eqText(query, ModelForecastResultTb::getForecastBatchNo, request.getForecastBatchNo());
        query.orderByDesc(ModelForecastResultTb::getForecastDate).orderByDesc(ModelForecastResultTb::getId);
        int page = positive(request.getPage(), 1), size = positive(request.getSize(), 20);
        IPage<ModelForecastResultTb> result = resultMapper.selectPage(PageUtils.pageRequest(page, size), query);
        return PageUtils.toPage(result, result.getRecords());
    }

    public List<ModelForecastHistoryPointResponse> resultHistory(ModelForecastBatchRequest request) {
        String batchNo = request.getForecastBatchNo();
        if (!TextUtils.hasText(batchNo))
            throw new BusinessException("预测批次号不能为空");
        ModelForecastRecordTb record = recordMapper.selectOne(Wrappers.<ModelForecastRecordTb>lambdaQuery().eq(ModelForecastRecordTb::getForecastBatchNo, batchNo).last("limit 1"));
        if (record == null)
            throw new BusinessException("预测批次不存在");

        String granularity = switch (defaultText(record.getForecastFrequency(), "DAILY").toUpperCase()) {
            case "TENDAY" -> ModelTrainTimeGranularity.TENDAY.name();
            case "MONTHLY", "MONTH" -> ModelTrainTimeGranularity.MONTH.name();
            default -> ModelTrainTimeGranularity.DAY.name();
        };
        int historyPredictionSize = historyPredictionSize(record.getForecastFrequency(), record.getForecastHorizon());
        int historyActualSize = historyActualSize(record.getForecastFrequency());
        var query = Wrappers.<ModelTrainFeatureDataTb>lambdaQuery().eq(ModelTrainFeatureDataTb::getTimeGranularity, granularity)
                .lt(TextUtils.hasText(record.getForecastStartDate()), ModelTrainFeatureDataTb::getStatDate, record.getForecastStartDate()).isNotNull(ModelTrainFeatureDataTb::getGasSales);
        eqText(query, ModelTrainFeatureDataTb::getRegionCode, record.getRegionCode());
        eqText(query, ModelTrainFeatureDataTb::getIndustryCode, record.getIndustryCode());
        eqText(query, ModelTrainFeatureDataTb::getCustomerCode, record.getCustomerCode());
        query.orderByDesc(ModelTrainFeatureDataTb::getStatDate).orderByDesc(ModelTrainFeatureDataTb::getId).last("limit " + historyActualSize);

        List<ModelTrainFeatureDataTb> rows = new ArrayList<>(featureDataMapper.selectList(query));
        Collections.reverse(rows);
        String trainBatchNo = trainBatchNoForRecord(record);
        Map<String, BigDecimal> rollingPredictions = loadRollingPredictions(trainBatchNo, record.getForecastStartDate(), historyPredictionSize);
        // 历史实际值与滚动预测值用于同区间对比：存在滚动预测时，两条线必须从同一天开始。
        String comparisonStartDate = rollingPredictions.keySet().stream().findFirst().orElse(null);
        return rows.stream().filter(row -> comparisonStartDate == null || row.getStatDate().compareTo(comparisonStartDate) >= 0).map(row -> {
            return new ModelForecastHistoryPointResponse(row.getStatDate(), row.getGasSales(), rollingPredictions.get(row.getStatDate()));
        }).toList();
    }

    /** H = min(max(F, lower), upper), F 为未来预测步长，H 为历史滚动预测步长。 */
    private int historyPredictionSize(String frequency, Integer forecastHorizon) {
        int horizon = forecastHorizon == null ? 1 : Math.max(forecastHorizon, 1);
        return switch (defaultText(frequency, "DAILY").toUpperCase()) {
            case "MONTHLY", "MONTH" -> Math.min(Math.max(horizon, 6), 12);
            case "TENDAY" -> Math.min(Math.max(horizon, 6), 9);
            default -> Math.min(Math.max(horizon, 14), 30);
        };
    }

    private int historyActualSize(String frequency) {
        return switch (defaultText(frequency, "DAILY").toUpperCase()) {
            case "MONTHLY", "MONTH" -> 24;
            case "TENDAY" -> 18;
            default -> 90;
        };
    }

    private String trainBatchNoForRecord(ModelForecastRecordTb record) {
        if (record.getRequestParam() != null && record.getRequestParam().length > 0) {
            try {
                JsonNode request = objectMapper.readTree(record.getRequestParam());
                String batchNo = text(request, "train_batch_no");
                if (TextUtils.hasText(batchNo))
                    return batchNo;
            } catch (Exception ignored) {
                // 兼容没有请求快照的历史记录，继续按范围查找最近成功训练批次。
            }
        }
        var query = Wrappers.<ModelTrainRecordTb>lambdaQuery().eq(ModelTrainRecordTb::getAgentCode, record.getAgentCode()).eq(ModelTrainRecordTb::getStatus, ModelTrainStatus.SUCCESS.getCode());
        eqText(query, ModelTrainRecordTb::getRegionCode, record.getRegionCode());
        eqText(query, ModelTrainRecordTb::getIndustryCode, record.getIndustryCode());
        eqText(query, ModelTrainRecordTb::getCustomerCode, record.getCustomerCode());
        query.orderByDesc(ModelTrainRecordTb::getUpdatedAt).orderByDesc(ModelTrainRecordTb::getId).last("limit 1");
        ModelTrainRecordTb detail = trainDetailMapper.selectOne(query);
        return detail == null ? null : detail.getBatchNo();
    }

    private Map<String, BigDecimal> loadRollingPredictions(String trainBatchNo, String forecastStartDate, int size) {
        if (!TextUtils.hasText(trainBatchNo))
            return Collections.emptyMap();
        var query = Wrappers.<ModelTrainBacktestTb>lambdaQuery().eq(ModelTrainBacktestTb::getTrainBatchNo, trainBatchNo).isNotNull(ModelTrainBacktestTb::getPredictedValue);
        if (TextUtils.hasText(forecastStartDate)) {
            query.lt(ModelTrainBacktestTb::getTrainDate, java.sql.Date.valueOf(forecastStartDate));
        }
        query.orderByDesc(ModelTrainBacktestTb::getTrainDate).orderByDesc(ModelTrainBacktestTb::getId).last("limit " + size);
        List<ModelTrainBacktestTb> points = new ArrayList<>(trainBacktestMapper.selectList(query));
        Collections.reverse(points);
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (ModelTrainBacktestTb point : points) {
            if (point.getTrainDate() == null)
                continue;
            String date = new java.text.SimpleDateFormat("yyyy-MM-dd").format(point.getTrainDate());
            result.put(date, point.getPredictedValue());
        }
        return result;
    }

    public PageInfoDTO<ModelForecastRecordTb> listRecords(ModelForecastRecordPageRequest request) {
        var query = Wrappers.<ModelForecastRecordTb>lambdaQuery();
        if (request.getForecastId() != null)
            query.eq(ModelForecastRecordTb::getForecastId, request.getForecastId());
        String forecastBatchNo = request.getForecastBatchNo();
        if (TextUtils.hasText(forecastBatchNo))
            query.eq(ModelForecastRecordTb::getForecastBatchNo, forecastBatchNo);
        if (request.getStatus() != null)
            query.eq(ModelForecastRecordTb::getStatus, request.getStatus());
        query.orderByDesc(ModelForecastRecordTb::getCreatedAt).orderByDesc(ModelForecastRecordTb::getId);
        int page = positive(request.getPage(), 1), size = positive(request.getSize(), 20);
        IPage<ModelForecastRecordTb> result = recordMapper.selectPage(PageUtils.pageRequest(page, size), query);
        return PageUtils.toPage(result, result.getRecords());
    }

    public List<Map<String, Object>> recordFeatures(ModelForecastBatchRequest request) {
        String forecastBatchNo = request.getForecastBatchNo();
        if (!TextUtils.hasText(forecastBatchNo))
            throw new BusinessException("预测批次号不能为空");
        ModelForecastRecordTb record = recordMapper.selectOne(Wrappers.<ModelForecastRecordTb>lambdaQuery().select(ModelForecastRecordTb::getFeatureSnapshot, ModelForecastRecordTb::getRequestParam)
                .eq(ModelForecastRecordTb::getForecastBatchNo, forecastBatchNo).last("limit 1"));
        if (record == null)
            throw new BusinessException("预测批次不存在：" + forecastBatchNo);
        byte[] snapshot = record.getFeatureSnapshot();
        try {
            JsonNode features = snapshot == null || snapshot.length == 0 ? null : objectMapper.readTree(new String(snapshot, StandardCharsets.UTF_8));
            if ((features == null || !features.isArray()) && record.getRequestParam() != null && record.getRequestParam().length > 0) {
                JsonNode requestPayload = objectMapper.readTree(new String(record.getRequestParam(), StandardCharsets.UTF_8));
                features = requestPayload.path("dataset");
            }
            return features != null && features.isArray() ? objectMapper.convertValue(features, new TypeReference<List<Map<String, Object>>>() {
            }) : Collections.emptyList();
        } catch (Exception exception) {
            throw new BusinessException("预测特征快照解析失败");
        }
    }

    private String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText().trim() : null;
    }

    private int positive(Integer value, int fallback) {
        return value == null ? fallback : Math.max(value, 1);
    }

    private String defaultText(String value, String fallback) {
        return TextUtils.hasText(value) ? value : fallback;
    }

    private <T> void eqText(LambdaQueryWrapper<T> query, com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> field, String value) {
        if (TextUtils.hasText(value))
            query.eq(field, value);
    }
}
