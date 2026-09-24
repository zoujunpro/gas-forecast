package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.dto.request.ModelTrainConfigCreateRequest;
import com.gas.forecast.business.dto.request.ModelTrainConfigPageRequest;
import com.gas.forecast.business.dto.request.ModelTrainConfigUpdateRequest;
import com.gas.forecast.business.dto.response.ModelTrainConfigResponse;
import com.gas.forecast.business.enums.ModelTrainMode;
import com.gas.forecast.business.enums.ModelTrainTimeGranularity;
import com.gas.forecast.business.enums.BaseCodeType;
import com.gas.forecast.business.service.BaseCodeGenerateService;
import com.gas.forecast.business.service.ModelTrainConfigService;
import com.gas.forecast.business.util.PageUtils;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.core.PageInfoDTO;
import com.gas.forecast.common.security.context.SecurityContextHolder;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.ModelConfigTb;
import com.gas.forecast.dao.domain.ModelForecastConfigTb;
import com.gas.forecast.dao.domain.ModelForecastRecordTb;
import com.gas.forecast.dao.domain.ModelTrainBacktestTb;
import com.gas.forecast.dao.domain.ModelTrainConfigTb;
import com.gas.forecast.dao.domain.ModelTrainRecordTb;
import com.gas.forecast.dao.mapper.ModelConfigTbMapper;
import com.gas.forecast.dao.mapper.ModelForecastConfigTbMapper;
import com.gas.forecast.dao.mapper.ModelForecastRecordTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainBacktestTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainConfigTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainRecordTbMapper;
import java.util.List;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ModelTrainConfigServiceImpl implements ModelTrainConfigService {

    private final ModelTrainConfigTbMapper modelTrainConfigTbMapper;
    private final ModelConfigTbMapper modelConfigTbMapper;
    private final ModelTrainRecordTbMapper modelTrainRecordTbMapper;
    private final ModelTrainBacktestTbMapper modelTrainBacktestTbMapper;
    private final ModelForecastConfigTbMapper modelForecastConfigTbMapper;
    private final ModelForecastRecordTbMapper modelForecastRecordTbMapper;
    private final BaseCodeGenerateService baseCodeGenerateService;

    @Override
    public PageInfoDTO<ModelTrainConfigResponse> listPage(ModelTrainConfigPageRequest reqDTO) {
        LambdaQueryWrapper<ModelTrainConfigTb> query = Wrappers.lambdaQuery();
        String keyword = reqDTO.getKeyword();
        if (TextUtils.hasText(keyword)) {
            query.and(wrapper -> wrapper.like(ModelTrainConfigTb::getTrainCode, keyword).or().like(ModelTrainConfigTb::getTrainName, keyword).or().like(ModelTrainConfigTb::getAgentCode, keyword).or()
                    .like(ModelTrainConfigTb::getModelId, keyword).or().like(ModelTrainConfigTb::getModelCode, keyword).or().like(ModelTrainConfigTb::getModelName, keyword).or()
                    .like(ModelTrainConfigTb::getRegionName, keyword).or().like(ModelTrainConfigTb::getIndustryName, keyword).or().like(ModelTrainConfigTb::getCustomerName, keyword));
        }
        if (TextUtils.hasText(reqDTO.getAgentCode())) {
            query.eq(ModelTrainConfigTb::getAgentCode, reqDTO.getAgentCode().trim());
        }
        if (TextUtils.hasText(reqDTO.getTimeGranularity())) {
            query.eq(ModelTrainConfigTb::getTimeGranularity, reqDTO.getTimeGranularity().trim());
        }
        query.orderByDesc(ModelTrainConfigTb::getUpdatedAt).orderByDesc(ModelTrainConfigTb::getId);
        int page = reqDTO.getPage() == null ? 1 : reqDTO.getPage();
        int size = reqDTO.getSize() == null ? 20 : reqDTO.getSize();
        IPage<ModelTrainConfigTb> result = modelTrainConfigTbMapper.selectPage(PageUtils.pageRequest(page, size), query);
        return PageUtils.toPage(result, result.getRecords().stream().map(this::toResp).toList());
    }

    @Override
    @Transactional
    public ModelTrainConfigResponse create(ModelTrainConfigCreateRequest reqDTO) {
        String trainCode = TextUtils.hasText(reqDTO.getTrainCode()) ? reqDTO.getTrainCode().trim() : generateTrainCode();
        ensureTrainCodeUnique(trainCode, null);
        ModelConfigTb modelConfig = requireModel(reqDTO.getModelId());
        ModelTrainConfigTb entity = toEntity(reqDTO, trainCode, modelConfig);
        Date now = new Date();
        entity.setId(null);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setCreatedBy(String.valueOf(SecurityContextHolder.getUserId()));
        entity.setCreatedByName(SecurityContextHolder.getUserName());
        modelTrainConfigTbMapper.insert(entity);
        return toResp(entity);
    }

    @Override
    @Transactional
    public ModelTrainConfigResponse update(ModelTrainConfigUpdateRequest reqDTO) {
        ModelTrainConfigTb exists = modelTrainConfigTbMapper.selectById(reqDTO.getId());
        if (exists == null) {
            throw new BusinessException("模型训练配置不存在");
        }
        ensureTrainCodeUnique(reqDTO.getTrainCode(), reqDTO.getId());
        ModelConfigTb modelConfig = requireModel(reqDTO.getModelId());
        ModelTrainConfigTb entity = toEntity(reqDTO, modelConfig);
        entity.setId(reqDTO.getId());
        entity.setCreatedAt(exists.getCreatedAt());
        entity.setCreatedBy(exists.getCreatedBy());
        entity.setCreatedByName(exists.getCreatedByName());
        entity.setUpdatedAt(new Date());
        modelTrainConfigTbMapper.updateById(entity);
        return toResp(modelTrainConfigTbMapper.selectById(reqDTO.getId()));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        ModelTrainConfigTb config = modelTrainConfigTbMapper.selectById(id);
        if (config == null) {
            throw new BusinessException("模型训练配置不存在");
        }

        List<ModelForecastConfigTb> forecastConfigs = modelForecastConfigTbMapper.selectList(Wrappers.<ModelForecastConfigTb>lambdaQuery().eq(ModelForecastConfigTb::getTrainConfigId, id));
        if (!forecastConfigs.isEmpty()) {
            List<Long> forecastIds = forecastConfigs.stream().map(ModelForecastConfigTb::getId).toList();
            long forecastRecordCount = modelForecastRecordTbMapper.selectCount(Wrappers.<ModelForecastRecordTb>lambdaQuery().in(ModelForecastRecordTb::getForecastId, forecastIds));
            if (forecastRecordCount > 0) {
                throw new BusinessException("该训练配置已经用于预测，不能删除");
            }
            throw new BusinessException("该训练配置已被预测配置引用，不能删除");
        }

        List<ModelTrainRecordTb> trainRecords = modelTrainRecordTbMapper.selectList(Wrappers.<ModelTrainRecordTb>lambdaQuery().eq(ModelTrainRecordTb::getTrainConfigId, id));
        List<String> batchNos = trainRecords.stream().map(ModelTrainRecordTb::getBatchNo).filter(TextUtils::hasText).distinct().toList();
        if (!batchNos.isEmpty()) {
            modelTrainBacktestTbMapper.delete(Wrappers.<ModelTrainBacktestTb>lambdaQuery().in(ModelTrainBacktestTb::getTrainBatchNo, batchNos));
        }
        modelTrainRecordTbMapper.delete(Wrappers.<ModelTrainRecordTb>lambdaQuery().eq(ModelTrainRecordTb::getTrainConfigId, id));
        modelTrainConfigTbMapper.deleteById(id);
    }

    private void ensureTrainCodeUnique(String trainCode, Long excludeId) {
        LambdaQueryWrapper<ModelTrainConfigTb> query = Wrappers.<ModelTrainConfigTb>lambdaQuery().eq(ModelTrainConfigTb::getTrainCode, trainCode);
        if (excludeId != null) {
            query.ne(ModelTrainConfigTb::getId, excludeId);
        }
        if (modelTrainConfigTbMapper.selectCount(query) > 0) {
            throw new BusinessException("训练配置编码已存在");
        }
    }

    private String generateTrainCode() {
        return baseCodeGenerateService.nextCode(BaseCodeType.MODEL_TRAIN_CONFIG);
    }

    private ModelTrainConfigTb toEntity(ModelTrainConfigCreateRequest reqDTO, String trainCode, ModelConfigTb modelConfig) {
        ModelTrainConfigTb entity = new ModelTrainConfigTb();
        fillEntity(entity, trainCode, reqDTO.getTrainName(), reqDTO.getAgentCode(), modelConfig, reqDTO.getRegionCode(), reqDTO.getRegionName(), reqDTO.getIndustryCode(), reqDTO.getIndustryName(),
                reqDTO.getCustomerCode(), reqDTO.getCustomerName(), reqDTO.getTrainStartDate(), reqDTO.getTrainEndDate(), reqDTO.getTrainMode(), reqDTO.getTimeGranularity(), reqDTO.getRecentPeriods(),
                reqDTO.getEnabled(), reqDTO.getRemark());
        return entity;
    }

    private ModelTrainConfigTb toEntity(ModelTrainConfigUpdateRequest reqDTO, ModelConfigTb modelConfig) {
        ModelTrainConfigTb entity = new ModelTrainConfigTb();
        fillEntity(entity, reqDTO.getTrainCode(), reqDTO.getTrainName(), reqDTO.getAgentCode(), modelConfig, reqDTO.getRegionCode(), reqDTO.getRegionName(), reqDTO.getIndustryCode(),
                reqDTO.getIndustryName(), reqDTO.getCustomerCode(), reqDTO.getCustomerName(), reqDTO.getTrainStartDate(), reqDTO.getTrainEndDate(), reqDTO.getTrainMode(), reqDTO.getTimeGranularity(),
                reqDTO.getRecentPeriods(), reqDTO.getEnabled(), reqDTO.getRemark());
        return entity;
    }

    private void fillEntity(ModelTrainConfigTb entity, String trainCode, String trainName, String agentCode, ModelConfigTb modelConfig, String regionCode, String regionName, String industryCode,
            String industryName, String customerCode, String customerName, String trainStartDate, String trainEndDate, String trainMode, String timeGranularity, Integer recentPeriods, Integer enabled,
            String remark) {
        entity.setTrainCode(trainCode);
        entity.setTrainName(trainName);
        entity.setAgentCode(agentCode);
        entity.setModelId(modelConfig.getId());
        entity.setModelCode(modelConfig.getModelCode());
        entity.setModelName(modelConfig.getModelName());
        entity.setRegionCode(regionCode);
        entity.setRegionName(regionName);
        entity.setIndustryCode(industryCode);
        entity.setIndustryName(industryName);
        entity.setCustomerCode(customerCode);
        entity.setCustomerName(customerName);
        ModelTrainMode normalizedTrainMode;
        try {
            normalizedTrainMode = TextUtils.hasText(trainMode) ? ModelTrainMode.fromCode(trainMode) : ModelTrainMode.RECENT;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(exception.getMessage());
        }
        entity.setTrainMode(normalizedTrainMode.name());
        entity.setTrainStartDate(normalizedTrainMode == ModelTrainMode.RANGE ? trainStartDate : null);
        entity.setTrainEndDate(normalizedTrainMode == ModelTrainMode.RANGE ? trainEndDate : null);
        ModelTrainTimeGranularity normalizedTimeGranularity;
        try {
            normalizedTimeGranularity = TextUtils.hasText(timeGranularity) ? ModelTrainTimeGranularity.fromCode(timeGranularity) : ModelTrainTimeGranularity.MONTH;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(exception.getMessage());
        }
        entity.setTimeGranularity(normalizedTimeGranularity.name());
        if (normalizedTrainMode == ModelTrainMode.RECENT && recentPeriods == null) {
            throw new BusinessException("最近周期数不能为空");
        }
        entity.setRecentPeriods(normalizedTrainMode == ModelTrainMode.RECENT ? recentPeriods : null);
        entity.setEnabled(enabled);
        entity.setRemark(remark);
    }

    private ModelTrainConfigResponse toResp(ModelTrainConfigTb entity) {
        if (entity == null) {
            return null;
        }
        return new ModelTrainConfigResponse(entity.getId(), entity.getTrainCode(), entity.getTrainName(), entity.getAgentCode(), entity.getModelId(), entity.getModelCode(), entity.getModelName(),
                entity.getRegionCode(), entity.getRegionName(), entity.getIndustryCode(), entity.getIndustryName(), entity.getCustomerCode(), entity.getCustomerName(), entity.getTrainStartDate(),
                entity.getTrainEndDate(), entity.getTrainMode(), entity.getTimeGranularity(), entity.getRecentPeriods(), entity.getEnabled(), entity.getRemark(), entity.getCreatedBy(),
                entity.getCreatedByName(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private ModelConfigTb requireModel(Long modelId) {
        if (modelId == null) {
            throw new BusinessException("所属模型不能为空");
        }
        ModelConfigTb modelConfig = modelConfigTbMapper.selectById(modelId);
        if (modelConfig == null) {
            throw new BusinessException("所属模型不存在");
        }
        return modelConfig;
    }
}
