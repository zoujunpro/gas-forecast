package com.gas.forecast.business.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import com.gas.forecast.dao.domain.ModelForecastConfigTb;
import com.gas.forecast.dao.domain.DataRegionWeatherDailyTb;
import com.gas.forecast.dao.domain.ModelForecastRecordTb;
import com.gas.forecast.dao.domain.ModelForecastResultTb;
import com.gas.forecast.dao.domain.ModelTrainBacktestTb;
import com.gas.forecast.dao.domain.ModelTrainConfigTb;
import com.gas.forecast.dao.domain.ModelTrainFeatureDataTb;
import com.gas.forecast.dao.domain.ModelTrainRecordTb;
import com.gas.forecast.dao.domain.DataDailySalesTb;
import com.gas.forecast.dao.domain.BaseCustomerTb;
import com.gas.forecast.dao.mapper.ModelFeatureDefinitionTbMapper;
import com.gas.forecast.dao.mapper.ModelFeatureRefMapper;
import com.gas.forecast.dao.mapper.ModelForecastConfigTbMapper;
import com.gas.forecast.dao.mapper.ModelForecastRecordTbMapper;
import com.gas.forecast.dao.mapper.ModelForecastResultTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainBacktestTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainConfigTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainRecordTbMapper;
import com.gas.forecast.dao.mapper.DataRegionWeatherDailyTbMapper;
import com.gas.forecast.dao.mapper.DataDailySalesTbMapper;
import com.gas.forecast.dao.mapper.BaseCustomerTbMapper;
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
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelForecastManagementService {
    private final ModelForecastConfigTbMapper configMapper;
    private final ModelForecastRecordTbMapper recordMapper;
    private final ModelForecastResultTbMapper resultMapper;
    private final ModelTrainConfigTbMapper trainConfigMapper;
    private final ModelTrainBacktestTbMapper trainBacktestMapper;
    private final ModelTrainRecordTbMapper trainDetailMapper;
    private final DataRegionWeatherDailyTbMapper weatherMapper;
    private final ModelFeatureRefMapper featureRefMapper;
    private final ModelFeatureDefinitionTbMapper featureDefinitionMapper;
    private final DataDailySalesTbMapper dailySalesMapper;
    private final BaseCustomerTbMapper customerMapper;
    private final TaskForecastCompletionService taskForecastCompletionService;
    private final ObjectMapper objectMapper;
    private final ModelPlatformClient modelPlatformClient;
    private final ForecastAnalysisService forecastAnalysisService;
    private final @Qualifier("modelForecastTaskExecutor") Executor modelForecastTaskExecutor;

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
        Map<String, ModelForecastConfigTb> deduplicated = new LinkedHashMap<>();
        for (ModelForecastConfigTb config : configMapper.selectList(query)) {
            deduplicated.putIfAbsent(forecastScopeKey(config), config);
        }
        List<ModelForecastConfigTb> all = new ArrayList<>(deduplicated.values());
        int fromIndex = Math.min((page - 1) * size, all.size());
        int toIndex = Math.min(fromIndex + size, all.size());
        return PageInfoDTO.of(all.subList(fromIndex, toIndex), all.size(), page, size);
    }

    @Transactional
    public ModelForecastConfigTb saveConfig(ModelForecastConfigSaveRequest request) {
        Long id = request.getId();
        ModelForecastConfigTb entity = id == null ? null : configMapper.selectById(id);
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
        if ("short-term".equals(agentCode) && !TextUtils.hasText(request.getCustomerCode())) {
            throw new BusinessException("短期预测必须选择具体客户");
        }
        BaseCustomerTb forecastCustomer = null;
        if (TextUtils.hasText(request.getCustomerCode())) {
            forecastCustomer = customerMapper.selectOne(Wrappers.<BaseCustomerTb>lambdaQuery().eq(BaseCustomerTb::getCustomerCode, request.getCustomerCode()).last("limit 1"));
            if (forecastCustomer == null)
                throw new BusinessException("预测客户不存在：" + request.getCustomerCode());
            if (!sameScopeValue(trainConfig.getRegionCode(), forecastCustomer.getRegionCode())
                    || !sameScopeValue(trainConfig.getIndustryCode(), forecastCustomer.getIndustryCode())
                    || !sameScopeValue(trainConfig.getCustomerCode(), forecastCustomer.getCustomerCode()))
                throw new BusinessException("预测与训练的地区、行业、客户必须完全一致");
        }
        String scopeType = forecastCustomer == null ? resolveScopeType(trainConfig) : "CUSTOMER";
        String regionCode = forecastCustomer == null ? trainConfig.getRegionCode() : forecastCustomer.getRegionCode();
        String regionName = forecastCustomer == null ? trainConfig.getRegionName() : forecastCustomer.getRegionName();
        String industryCode = forecastCustomer == null ? trainConfig.getIndustryCode() : forecastCustomer.getIndustryCode();
        String industryName = forecastCustomer == null ? trainConfig.getIndustryName() : forecastCustomer.getIndustryName();
        String customerCode = forecastCustomer == null ? trainConfig.getCustomerCode() : forecastCustomer.getCustomerCode();
        String customerName = forecastCustomer == null ? trainConfig.getCustomerName() : forecastCustomer.getCustomerName();
        ModelForecastConfigTb existing = findSameForecastScope(agentCode, regionCode, industryCode, customerCode);
        if (id == null) {
            entity = existing == null ? new ModelForecastConfigTb() : existing;
        } else if (existing != null && !existing.getId().equals(id)) {
            throw new BusinessException("同一智能体、地区、行业、客户只能保留一个预测配置");
        }
        Date now = new Date();
        if (entity.getId() == null) {
            entity.setCreatedAt(now);
            entity.setCreatedBy(String.valueOf(SecurityContextHolder.getUserId()));
            entity.setCreatedByName(SecurityContextHolder.getUserName());
        }
        entity.setForecastName(name);
        entity.setAgentCode(agentCode);
        entity.setScopeType(scopeType);
        entity.setRegionCode(regionCode);
        entity.setRegionName(regionName);
        entity.setIndustryCode(industryCode);
        entity.setIndustryName(industryName);
        entity.setCustomerCode(customerCode);
        entity.setCustomerName(customerName);
        entity.setForecastStartDate(forecastStartDate);
        entity.setForecastHorizon(positive(request.getForecastHorizon(), 12));
        entity.setForecastFrequency(defaultText(request.getForecastFrequency(), "MANUAL"));
        entity.setAutoForecast(0);
        entity.setTrainConfigId(trainConfig.getId());
        entity.setTrainConfigCode(trainConfig.getTrainCode());
        entity.setEnabled(request.getEnabled() == null ? 1 : request.getEnabled());
        entity.setRemark(request.getRemark());
        entity.setUpdatedAt(now);
        if (entity.getId() == null)
            configMapper.insert(entity);
        else
            configMapper.updateById(entity);
        return configMapper.selectById(entity.getId());
    }

    private ModelForecastConfigTb findSameForecastScope(String agentCode, String regionCode, String industryCode, String customerCode) {
        var query = Wrappers.<ModelForecastConfigTb>lambdaQuery().eq(ModelForecastConfigTb::getAgentCode, agentCode);
        scopeEq(query, ModelForecastConfigTb::getRegionCode, regionCode);
        scopeEq(query, ModelForecastConfigTb::getIndustryCode, industryCode);
        scopeEq(query, ModelForecastConfigTb::getCustomerCode, customerCode);
        return configMapper.selectOne(query.orderByDesc(ModelForecastConfigTb::getUpdatedAt).orderByDesc(ModelForecastConfigTb::getId).last("limit 1"));
    }

    private String forecastScopeKey(ModelForecastConfigTb config) {
        return scopeKey(config.getAgentCode()) + "\t" + scopeKey(config.getRegionCode()) + "\t" + scopeKey(config.getIndustryCode()) + "\t" + scopeKey(config.getCustomerCode());
    }

    private String scopeKey(String value) {
        return !TextUtils.hasText(value) || "ALL".equalsIgnoreCase(value) ? "ALL" : value.trim();
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
        PreparedForecast prepared = prepare(request);
        return executePrepared(prepared);
    }

    /** 创建预测批次后立即返回，模型平台调用在线程池中执行。 */
    public ModelForecastExecuteResponse submit(ModelForecastExecuteRequest request) {
        PreparedForecast prepared = prepare(request);
        try {
            modelForecastTaskExecutor.execute(() -> {
                try {
                    executePrepared(prepared);
                } catch (RuntimeException exception) {
                    log.error("异步预测失败, forecastBatchNo={}", prepared.record().getForecastBatchNo(), exception);
                }
            });
        } catch (RuntimeException exception) {
            markForecastFailed(prepared.record(), "预测任务提交失败：" + safeMessage(exception));
            throw new BusinessException("预测任务繁忙，暂时无法提交，请稍后重试");
        }
        return new ModelForecastExecuteResponse(prepared.config().getId(), prepared.record().getForecastBatchNo(), 0);
    }

    private PreparedForecast prepare(ModelForecastExecuteRequest request) {
        Long forecastId = request.getForecastId();
        ModelForecastConfigTb config = configMapper.selectById(forecastId);
        if (config == null)
            throw new BusinessException("预测配置不存在");
        if (config.getEnabled() != null && config.getEnabled() == 0)
            throw new BusinessException("预测配置已停用");
        applyExecutionParams(config, request);
        ModelTrainConfigTb trainConfig = requireTrainConfig(config.getTrainConfigId(), config.getTrainConfigCode());
        validateMatchingScope(config, trainConfig);
        JsonNode dataset = request.getDataset() == null || request.getDataset().isEmpty() ? buildFutureDataset(config, trainConfig) : enrichDatasetWithWeather(config, trainConfig, objectMapper.valueToTree(request.getDataset()));
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
        String trainBatchNo = latestSuccessfulBatch(trainConfig);
        if (!TextUtils.hasText(trainBatchNo))
            throw new BusinessException("当前训练配置没有成功的模型训练批次");
        ModelForecastRecordTb record = prepareForecastRecord(config, request.getRetryBatchNo());
        ModelPredictApiRequest payload = buildPredictPayload(config, trainConfig, trainBatchNo, record.getForecastBatchNo(), dataset);
        record.setFeatureSnapshot(payload.getDataset().toString().getBytes(StandardCharsets.UTF_8));
        record.setRequestParam(objectMapper.valueToTree(payload).toString().getBytes(StandardCharsets.UTF_8));
        record.setUpdatedAt(new Date());
        recordMapper.updateById(record);
        return new PreparedForecast(config, record, payload);
    }

    private void validateMatchingScope(ModelForecastConfigTb forecastConfig, ModelTrainConfigTb trainConfig) {
        if (!sameScopeValue(forecastConfig.getRegionCode(), trainConfig.getRegionCode())
                || !sameScopeValue(forecastConfig.getIndustryCode(), trainConfig.getIndustryCode())
                || !sameScopeValue(forecastConfig.getCustomerCode(), trainConfig.getCustomerCode()))
            throw new BusinessException("预测与训练的地区、行业、客户必须完全一致");
    }

    private boolean sameScopeValue(String left, String right) {
        return Objects.equals(normalizeScopeValue(left), normalizeScopeValue(right));
    }

    private String normalizeScopeValue(String value) {
        return TextUtils.hasText(value) ? value.trim() : null;
    }

    private void applyExecutionParams(ModelForecastConfigTb config, ModelForecastExecuteRequest request) {
        if (TextUtils.hasText(request.getForecastStartDate())) {
            try {
                LocalDate.parse(request.getForecastStartDate());
            } catch (java.time.format.DateTimeParseException exception) {
                throw new BusinessException("预测开始日期格式必须为 yyyy-MM-dd");
            }
            config.setForecastStartDate(request.getForecastStartDate());
        }
        if (request.getForecastHorizon() != null) {
            config.setForecastHorizon(Math.max(1, Math.min(request.getForecastHorizon(), 366)));
        }
        if (TextUtils.hasText(request.getForecastFrequency())) {
            config.setForecastFrequency(request.getForecastFrequency());
        }
    }

    private ModelForecastExecuteResponse executePrepared(PreparedForecast prepared) {
        ModelForecastConfigTb config = prepared.config();
        ModelForecastRecordTb record = prepared.record();
        String forecastBatchNo = record.getForecastBatchNo();
        try {
            ModelPredictApiResponse response = modelPlatformClient.predict(prepared.payload());
            if (response.getPoints() == null || !response.getPoints().isArray())
                throw new BusinessException("模型平台预测响应缺少 points");
            saveForecastResults(forecastBatchNo, response.getPoints());
            record.setResponseParam(objectMapper.valueToTree(response).toString().getBytes(StandardCharsets.UTF_8));
            record.setStatus(ModelForecastStatus.SUCCESS.getCode());
            record.setForecastEndTime(java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            record.setUpdatedAt(new Date());
            recordMapper.updateById(record);
            taskForecastCompletionService.markSuccess(forecastBatchNo);
            try {
                forecastAnalysisService.submit(forecastBatchNo);
            } catch (RuntimeException analysisException) {
                // 分析属于预测后的附加能力，提交失败不能把已成功的预测改成失败。
                log.error("预测批次 {} 的大模型分析任务提交失败", forecastBatchNo, analysisException);
            }
            return new ModelForecastExecuteResponse(config.getId(), forecastBatchNo, response.getPoints().size());
        } catch (RuntimeException exception) {
            markForecastFailed(record, safeMessage(exception));
            taskForecastCompletionService.markFailed(forecastBatchNo, safeMessage(exception));
            throw exception;
        }
    }

    private void markForecastFailed(ModelForecastRecordTb record, String message) {
        record.setStatus(ModelForecastStatus.FAILED.getCode());
        record.setResponseParam(TextUtils.hasText(message) ? message.getBytes(StandardCharsets.UTF_8) : null);
        record.setForecastEndTime(java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        record.setUpdatedAt(new Date());
        recordMapper.updateById(record);
    }

    private String safeMessage(RuntimeException exception) {
        return TextUtils.hasText(exception.getMessage()) ? exception.getMessage() : "模型平台预测失败";
    }

    private record PreparedForecast(ModelForecastConfigTb config, ModelForecastRecordTb record, ModelPredictApiRequest payload) {
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
        return enrichDatasetWithWeather(config, trainConfig, buildDateOnlyFutureDataset(config));
    }

    private ArrayNode buildDateOnlyFutureDataset(ModelForecastConfigTb config) {
        ArrayNode dataset = objectMapper.createArrayNode();
        LocalDate current = LocalDate.parse(config.getForecastStartDate());
        int horizon = positive(config.getForecastHorizon(), 12);
        String frequency = defaultText(config.getForecastFrequency(), "DAILY");
        for (int index = 0; index < horizon; index++) {
            dataset.addObject().put("date", current.toString());
            current = switch (frequency) {
                case "MONTHLY" -> current.plusMonths(1);
                case "TENDAY" -> nextTenday(current);
                default -> current.plusDays(1);
            };
        }
        log.info("自动生成预测日期数据集: forecastId={}, startDate={}, frequency={}, horizon={}", config.getId(), config.getForecastStartDate(), frequency, horizon);
        return dataset;
    }

    private ArrayNode enrichDatasetWithWeather(ModelForecastConfigTb config, ModelTrainConfigTb trainConfig, JsonNode sourceDataset) {
        ArrayNode dataset = objectMapper.createArrayNode();
        if (sourceDataset == null || !sourceDataset.isArray())
            throw new BusinessException("预测特征数据必须是数组");
        List<LocalDate> dates = new ArrayList<>();
        for (JsonNode item : sourceDataset) {
            if (TextUtils.hasText(item.path("date").asText()))
                dates.add(LocalDate.parse(item.path("date").asText()));
        }
        if (dates.isEmpty())
            return dataset;
        String regionCode = defaultText(config.getRegionCode(), trainConfig.getRegionCode());
        if (!TextUtils.hasText(regionCode)) {
            sourceDataset.forEach(item -> dataset.add(item.deepCopy()));
            return dataset;
        }
        String frequency = defaultText(config.getForecastFrequency(), "DAILY");
        LocalDate min = dates.stream().min(LocalDate::compareTo).orElseThrow();
        LocalDate max = dates.stream().map(date -> periodEnd(date, frequency)).max(LocalDate::compareTo).orElseThrow();
        Map<LocalDate, DataRegionWeatherDailyTb> weatherByDate = loadWeather(regionCode, min, max);
        for (JsonNode item : sourceDataset) {
            ObjectNode row = item.isObject() ? ((ObjectNode) item).deepCopy() : objectMapper.createObjectNode();
            LocalDate date = LocalDate.parse(row.path("date").asText());
            WeatherAggregate weather = aggregateWeather(weatherByDate, date, periodEnd(date, frequency));
            if (weather.hasValues()) {
                fillWeather(row, weather);
            }
            dataset.add(row);
        }
        log.info("预测数据集天气特征补充完成: forecastId={}, regionCode={}, rows={}", config.getId(), regionCode, dataset.size());
        return dataset;
    }

    private Map<LocalDate, DataRegionWeatherDailyTb> loadWeather(String regionCode, LocalDate start, LocalDate end) {
        List<DataRegionWeatherDailyTb> rows = weatherMapper.selectList(Wrappers.<DataRegionWeatherDailyTb>lambdaQuery().eq(DataRegionWeatherDailyTb::getRegionCode, regionCode)
                .in(DataRegionWeatherDailyTb::getWeatherType, Set.of("ACTUAL", "FORECAST")).ge(DataRegionWeatherDailyTb::getStatDate, start).le(DataRegionWeatherDailyTb::getStatDate, end)
                .orderByAsc(DataRegionWeatherDailyTb::getStatDate).orderByAsc(DataRegionWeatherDailyTb::getWeatherType).orderByAsc(DataRegionWeatherDailyTb::getForecastIssueTime)
                .orderByAsc(DataRegionWeatherDailyTb::getId));
        Map<LocalDate, DataRegionWeatherDailyTb> result = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (DataRegionWeatherDailyTb row : rows) {
            LocalDate date = new java.sql.Date(row.getStatDate().getTime()).toLocalDate();
            DataRegionWeatherDailyTb existing = result.get(date);
            if (existing == null || preferWeather(row, existing, date, today)) {
                result.put(date, row);
            }
        }
        return result;
    }

    /** 历史日期使用实况天气，未来日期使用预报天气；首选类型缺失时允许另一类型兜底。 */
    static boolean preferWeather(DataRegionWeatherDailyTb candidate, DataRegionWeatherDailyTb existing, LocalDate weatherDate, LocalDate today) {
        String preferredType = weatherDate.isAfter(today) ? "FORECAST" : "ACTUAL";
        boolean candidatePreferred = preferredType.equalsIgnoreCase(candidate.getWeatherType());
        boolean existingPreferred = preferredType.equalsIgnoreCase(existing.getWeatherType());
        if (candidatePreferred != existingPreferred)
            return candidatePreferred;
        Date candidateIssue = candidate.getForecastIssueTime();
        Date existingIssue = existing.getForecastIssueTime();
        if (candidateIssue != null && existingIssue != null && !candidateIssue.equals(existingIssue))
            return candidateIssue.after(existingIssue);
        if (candidateIssue != null && existingIssue == null)
            return true;
        if (candidateIssue == null && existingIssue != null)
            return false;
        return candidate.getId() != null && existing.getId() != null && candidate.getId() > existing.getId();
    }

    private WeatherAggregate aggregateWeather(Map<LocalDate, DataRegionWeatherDailyTb> weatherByDate, LocalDate start, LocalDate end) {
        WeatherAggregate aggregate = new WeatherAggregate();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            DataRegionWeatherDailyTb weather = weatherByDate.get(date);
            if (weather != null)
                aggregate.add(weather);
        }
        return aggregate;
    }

    private LocalDate periodEnd(LocalDate date, String frequency) {
        return switch (defaultText(frequency, "DAILY")) {
            case "MONTHLY" -> date.withDayOfMonth(date.lengthOfMonth());
            case "TENDAY" -> date.getDayOfMonth() <= 10 ? date.withDayOfMonth(10) : date.getDayOfMonth() <= 20 ? date.withDayOfMonth(20) : date.withDayOfMonth(date.lengthOfMonth());
            default -> date;
        };
    }

    private void fillWeather(ObjectNode row, WeatherAggregate weather) {
        putDouble(row, "temp", weather.avg("tempAvg"));
        putDouble(row, "temp_avg", weather.avg("tempAvg"));
        putDouble(row, "tempmax", weather.max("tempMax"));
        putDouble(row, "temp_max", weather.max("tempMax"));
        putDouble(row, "tempmin", weather.min("tempMin"));
        putDouble(row, "temp_min", weather.min("tempMin"));
        putDouble(row, "hdd", weather.sum("hdd"));
        Double tempAvg = weather.avg("tempAvg");
        putDouble(row, "cdd", tempAvg == null ? null : Math.max(tempAvg - 26D, 0D));
        Double tempMax = weather.max("tempMax");
        Double tempMin = weather.min("tempMin");
        putDouble(row, "temp_range", tempMax == null || tempMin == null ? null : tempMax - tempMin);
        putDouble(row, "humidity", weather.avg("humidity"));
        putDouble(row, "precip", weather.sum("precipitation"));
        putDouble(row, "precipitation", weather.sum("precipitation"));
        putDouble(row, "windgust", weather.max("windGust"));
        putDouble(row, "wind_gust", weather.max("windGust"));
        putDouble(row, "windspeed", weather.avg("windSpeed"));
        putDouble(row, "wind_speed", weather.avg("windSpeed"));
        putDouble(row, "cloudcover", weather.avg("cloudCover"));
        putDouble(row, "cloud_cover", weather.avg("cloudCover"));
        putDouble(row, "sealevelpressure", weather.avg("seaLevelPressure"));
        putDouble(row, "sea_level_pressure", weather.avg("seaLevelPressure"));
        putDouble(row, "visibility", weather.avg("visibility"));
        putDouble(row, "uvindex", weather.max("uvIndex"));
        putDouble(row, "uv_index", weather.max("uvIndex"));
        row.put("weather_source", weather.source());
    }

    private void putDouble(ObjectNode row, String field, Double value) {
        if (value != null && !row.hasNonNull(field))
            row.put(field, value);
    }

    private LocalDate nextTenday(LocalDate date) {
        if (date.getDayOfMonth() <= 10)
            return date.withDayOfMonth(11);
        if (date.getDayOfMonth() <= 20)
            return date.withDayOfMonth(21);
        return date.plusMonths(1).withDayOfMonth(1);
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
        // 历史实际值必须与预测作用范围保持同一统计口径。客户为空时，一天会命中该行业下的多个客户，
        // 因此先按日期汇总销量，再截取最近 N 个周期；否则 LIMIT 会截取客户明细而不是日期。
        String periodExpression = switch (granularity) {
            case "MONTH" -> "concat(stat_month, '-01')";
            case "TENDAY" -> "concat(left(stat_tenday, 7), '-', case right(stat_tenday, 1) when '1' then '01' when '2' then '11' else '21' end)";
            default -> "stat_date";
        };
        QueryWrapper<DataDailySalesTb> query = Wrappers.<DataDailySalesTb>query().select(periodExpression + " AS stat_date", "SUM(CAST(gas_sales AS DECIMAL(18,4))) AS gas_sales")
                .lt(TextUtils.hasText(record.getForecastStartDate()), "stat_date", record.getForecastStartDate()).isNotNull("gas_sales");
        if (TextUtils.hasText(record.getRegionCode()))
            query.eq("region_code", record.getRegionCode());
        if (TextUtils.hasText(record.getIndustryCode()))
            query.eq("industry_code", record.getIndustryCode());
        if (TextUtils.hasText(record.getCustomerCode()))
            query.eq("customer_code", record.getCustomerCode());
        query.groupBy(periodExpression).orderByDesc(periodExpression).last("limit " + historyActualSize);

        // 聚合字段使用 Map 接收，避免 MyBatis 将 SUM(gas_sales) 映射为实体时丢失实际销量。
        List<Map<String, Object>> rows = new ArrayList<>(dailySalesMapper.selectMaps(query));
        Collections.reverse(rows);
        String trainBatchNo = trainBatchNoForRecord(record);
        Map<String, BigDecimal> rollingPredictions = loadRollingPredictions(trainBatchNo, record.getForecastStartDate(), historyPredictionSize);
        // 历史实际值与滚动预测值用于同区间对比：存在滚动预测时，两条线必须从同一天开始。
        String comparisonStartDate = rollingPredictions.keySet().stream().findFirst().orElse(null);
        List<ModelForecastHistoryPointResponse> history = rows.stream().map(row -> {
            String date = String.valueOf(mapValue(row, "stat_date"));
            Object actual = mapValue(row, "gas_sales");
            BigDecimal actualValue = actual == null ? null : new BigDecimal(actual.toString());
            return new ModelForecastHistoryPointResponse(date, actualValue, rollingPredictions.get(date), null);
        }).filter(row -> comparisonStartDate == null || row.getDate().compareTo(comparisonStartDate) >= 0).toList();
        if (!ModelTrainTimeGranularity.DAY.name().equals(granularity))
            return history;

        List<String> timelineDates = new ArrayList<>(history.stream().map(ModelForecastHistoryPointResponse::getDate).toList());
        resultMapper.selectList(Wrappers.<ModelForecastResultTb>lambdaQuery().eq(ModelForecastResultTb::getForecastBatchNo, batchNo).orderByAsc(ModelForecastResultTb::getForecastDate)).stream()
                .map(ModelForecastResultTb::getForecastDate).filter(TextUtils::hasText).forEach(timelineDates::add);
        Map<String, BigDecimal> lastYearValues = loadLastYearActuals(record, timelineDates);
        List<ModelForecastHistoryPointResponse> result = new ArrayList<>();
        history.forEach(point -> {
            point.setLastYearValue(lastYearValues.get(point.getDate()));
            result.add(point);
        });
        timelineDates.stream().filter(date -> history.stream().noneMatch(point -> date.equals(point.getDate()))).distinct()
                .forEach(date -> result.add(new ModelForecastHistoryPointResponse(date, null, null, lastYearValues.get(date))));
        return result;
    }

    private Map<String, BigDecimal> loadLastYearActuals(ModelForecastRecordTb record, List<String> timelineDates) {
        Map<String, String> sourceToDisplay = timelineDates.stream().filter(TextUtils::hasText).distinct()
                .collect(Collectors.toMap(date -> LocalDate.parse(date).minusYears(1).toString(), Function.identity(), (left, right) -> left, LinkedHashMap::new));
        if (sourceToDisplay.isEmpty())
            return Collections.emptyMap();
        QueryWrapper<DataDailySalesTb> query = Wrappers.<DataDailySalesTb>query().select("stat_date", "SUM(CAST(gas_sales AS DECIMAL(18,4))) AS gas_sales").in("stat_date", sourceToDisplay.keySet())
                .isNotNull("gas_sales");
        if (TextUtils.hasText(record.getRegionCode()))
            query.eq("region_code", record.getRegionCode());
        if (TextUtils.hasText(record.getIndustryCode()))
            query.eq("industry_code", record.getIndustryCode());
        if (TextUtils.hasText(record.getCustomerCode()))
            query.eq("customer_code", record.getCustomerCode());
        query.groupBy("stat_date");
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        dailySalesMapper.selectMaps(query).forEach(row -> {
            String sourceDate = String.valueOf(mapValue(row, "stat_date"));
            Object value = mapValue(row, "gas_sales");
            if (value != null && sourceToDisplay.containsKey(sourceDate))
                result.put(sourceToDisplay.get(sourceDate), new BigDecimal(value.toString()));
        });
        return result;
    }

    private Object mapValue(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (key.equalsIgnoreCase(entry.getKey()))
                return entry.getValue();
        }
        return null;
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

    private <T> void scopeEq(LambdaQueryWrapper<T> query, com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> field, String value) {
        if (!TextUtils.hasText(value) || "ALL".equalsIgnoreCase(value)) {
            query.and(q -> q.isNull(field).or().eq(field, "").or().eq(field, "ALL"));
        } else {
            query.eq(field, value);
        }
    }

    private static final class WeatherAggregate {
        private final Map<String, List<Double>> values = new LinkedHashMap<>();
        private final Set<String> sources = new java.util.LinkedHashSet<>();

        private void add(DataRegionWeatherDailyTb weather) {
            put("tempAvg", weather.getTempAvg());
            put("tempMax", weather.getTempMax());
            put("tempMin", weather.getTempMin());
            put("hdd", weather.getHdd());
            put("humidity", weather.getHumidity());
            put("precipitation", weather.getPrecipitation());
            put("windGust", weather.getWindGust());
            put("windSpeed", weather.getWindSpeed());
            put("cloudCover", weather.getCloudCover());
            put("seaLevelPressure", weather.getSeaLevelPressure());
            put("visibility", weather.getVisibility());
            put("uvIndex", weather.getUvIndex());
            String type = weather.getWeatherType();
            String source = weather.getSourceCode();
            if (TextUtils.hasText(type) || TextUtils.hasText(source))
                sources.add(defaultSource(type, source));
        }

        private static String defaultSource(String type, String source) {
            if (TextUtils.hasText(type) && TextUtils.hasText(source))
                return type + ":" + source;
            return TextUtils.hasText(type) ? type : source;
        }

        private void put(String key, BigDecimal value) {
            if (value != null)
                values.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value.doubleValue());
        }

        private boolean hasValues() {
            return !values.isEmpty();
        }

        private Double avg(String key) {
            List<Double> numbers = values.get(key);
            return numbers == null || numbers.isEmpty() ? null : numbers.stream().mapToDouble(Double::doubleValue).average().orElse(0D);
        }

        private Double sum(String key) {
            List<Double> numbers = values.get(key);
            return numbers == null || numbers.isEmpty() ? null : numbers.stream().mapToDouble(Double::doubleValue).sum();
        }

        private Double max(String key) {
            List<Double> numbers = values.get(key);
            return numbers == null || numbers.isEmpty() ? null : numbers.stream().mapToDouble(Double::doubleValue).max().orElse(0D);
        }

        private Double min(String key) {
            List<Double> numbers = values.get(key);
            return numbers == null || numbers.isEmpty() ? null : numbers.stream().mapToDouble(Double::doubleValue).min().orElse(0D);
        }

        private String source() {
            return String.join(",", sources);
        }
    }
}
