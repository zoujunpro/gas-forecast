package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.dto.request.ModelTrainConfigCreateRequest;
import com.gas.forecast.business.dto.request.ModelTrainConfigDeleteRequest;
import com.gas.forecast.business.dto.request.ModelTrainConfigPageRequest;
import com.gas.forecast.business.dto.request.ModelTrainConfigUpdateRequest;
import com.gas.forecast.business.dto.response.ModelTrainConfigResponse;
import com.gas.forecast.business.enums.BaseCodeType;
import com.gas.forecast.business.service.BaseCodeGenerateService;
import com.gas.forecast.business.service.ModelTrainConfigService;
import com.gas.forecast.business.util.PageUtils;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.core.PageInfoDTO;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.ModelConfigTb;
import com.gas.forecast.dao.domain.ModelTrainConfigTb;
import com.gas.forecast.dao.mapper.ModelConfigTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainConfigTbMapper;
import java.util.Date;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelTrainConfigServiceImpl implements ModelTrainConfigService {

    private final ModelTrainConfigTbMapper modelTrainConfigTbMapper;
    private final ModelConfigTbMapper modelConfigTbMapper;
    private final BaseCodeGenerateService baseCodeGenerateService;

    public ModelTrainConfigServiceImpl(
            ModelTrainConfigTbMapper modelTrainConfigTbMapper,
            ModelConfigTbMapper modelConfigTbMapper,
            BaseCodeGenerateService baseCodeGenerateService) {
        this.modelTrainConfigTbMapper = modelTrainConfigTbMapper;
        this.modelConfigTbMapper = modelConfigTbMapper;
        this.baseCodeGenerateService = baseCodeGenerateService;
    }

    @Override
    public PageInfoDTO<ModelTrainConfigResponse> listPage(ModelTrainConfigPageRequest reqDTO) {
        LambdaQueryWrapper<ModelTrainConfigTb> query = Wrappers.lambdaQuery();
        String keyword = reqDTO.keyword();
        if (TextUtils.hasText(keyword)) {
            query.and(wrapper -> wrapper.like(ModelTrainConfigTb::getTrainCode, keyword)
                    .or()
                    .like(ModelTrainConfigTb::getTrainName, keyword)
                    .or()
                    .like(ModelTrainConfigTb::getAgentCode, keyword)
                    .or()
                    .like(ModelTrainConfigTb::getModelId, keyword)
                    .or()
                    .like(ModelTrainConfigTb::getModelCode, keyword)
                    .or()
                    .like(ModelTrainConfigTb::getModelName, keyword)
                    .or()
                    .like(ModelTrainConfigTb::getRegionName, keyword)
                    .or()
                    .like(ModelTrainConfigTb::getIndustryName, keyword)
                    .or()
                    .like(ModelTrainConfigTb::getCustomerName, keyword));
        }
        if (TextUtils.hasText(reqDTO.agentCode())) {
            query.eq(ModelTrainConfigTb::getAgentCode, reqDTO.agentCode().trim());
        }
        if (TextUtils.hasText(reqDTO.timeGranularity())) {
            query.eq(
                    ModelTrainConfigTb::getTimeGranularity,
                    reqDTO.timeGranularity().trim());
        }
        query.orderByDesc(ModelTrainConfigTb::getUpdatedAt).orderByDesc(ModelTrainConfigTb::getId);
        int page = reqDTO.page() == null ? 1 : reqDTO.page();
        int size = reqDTO.size() == null ? 10 : reqDTO.size();
        IPage<ModelTrainConfigTb> result =
                modelTrainConfigTbMapper.selectPage(PageUtils.pageRequest(page, size), query);
        return PageUtils.toPage(
                result, result.getRecords().stream().map(this::toResp).toList());
    }

    @Override
    @Transactional
    public ModelTrainConfigResponse create(ModelTrainConfigCreateRequest reqDTO) {
        String trainCode =
                TextUtils.hasText(reqDTO.trainCode()) ? reqDTO.trainCode().trim() : generateTrainCode();
        ensureTrainCodeUnique(trainCode, null);
        ModelConfigTb modelConfig = requireModel(reqDTO.modelId());
        ModelTrainConfigTb entity = toEntity(reqDTO, trainCode, modelConfig);
        Date now = new Date();
        entity.setId(null);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setCreatedBy("system");
        entity.setCreatedByName("系统");
        modelTrainConfigTbMapper.insert(entity);
        return toResp(entity);
    }

    @Override
    @Transactional
    public ModelTrainConfigResponse update(ModelTrainConfigUpdateRequest reqDTO) {
        ModelTrainConfigTb exists = modelTrainConfigTbMapper.selectById(reqDTO.id());
        if (exists == null) {
            throw new BusinessException("模型训练配置不存在");
        }
        ensureTrainCodeUnique(reqDTO.trainCode(), reqDTO.id());
        ModelConfigTb modelConfig = requireModel(reqDTO.modelId());
        ModelTrainConfigTb entity = toEntity(reqDTO, modelConfig);
        entity.setId(reqDTO.id());
        entity.setCreatedAt(exists.getCreatedAt());
        entity.setCreatedBy(exists.getCreatedBy());
        entity.setCreatedByName(exists.getCreatedByName());
        entity.setUpdatedAt(new Date());
        modelTrainConfigTbMapper.updateById(entity);
        return toResp(modelTrainConfigTbMapper.selectById(reqDTO.id()));
    }

    @Override
    @Transactional
    public void delete(ModelTrainConfigDeleteRequest reqDTO) {
        modelTrainConfigTbMapper.deleteById(reqDTO.id());
    }

    private void ensureTrainCodeUnique(String trainCode, Long excludeId) {
        LambdaQueryWrapper<ModelTrainConfigTb> query =
                Wrappers.<ModelTrainConfigTb>lambdaQuery().eq(ModelTrainConfigTb::getTrainCode, trainCode);
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

    private ModelTrainConfigTb toEntity(
            ModelTrainConfigCreateRequest reqDTO, String trainCode, ModelConfigTb modelConfig) {
        ModelTrainConfigTb entity = new ModelTrainConfigTb();
        fillEntity(
                entity,
                trainCode,
                reqDTO.trainName(),
                reqDTO.agentCode(),
                modelConfig,
                reqDTO.regionCode(),
                reqDTO.regionName(),
                reqDTO.industryCode(),
                reqDTO.industryName(),
                reqDTO.customerCode(),
                reqDTO.customerName(),
                reqDTO.trainStartDate(),
                reqDTO.trainEndDate(),
                reqDTO.trainMode(),
                reqDTO.timeGranularity(),
                reqDTO.recentPeriods(),
                reqDTO.enabled(),
                reqDTO.remark());
        return entity;
    }

    private ModelTrainConfigTb toEntity(ModelTrainConfigUpdateRequest reqDTO, ModelConfigTb modelConfig) {
        ModelTrainConfigTb entity = new ModelTrainConfigTb();
        fillEntity(
                entity,
                reqDTO.trainCode(),
                reqDTO.trainName(),
                reqDTO.agentCode(),
                modelConfig,
                reqDTO.regionCode(),
                reqDTO.regionName(),
                reqDTO.industryCode(),
                reqDTO.industryName(),
                reqDTO.customerCode(),
                reqDTO.customerName(),
                reqDTO.trainStartDate(),
                reqDTO.trainEndDate(),
                reqDTO.trainMode(),
                reqDTO.timeGranularity(),
                reqDTO.recentPeriods(),
                reqDTO.enabled(),
                reqDTO.remark());
        return entity;
    }

    private void fillEntity(
            ModelTrainConfigTb entity,
            String trainCode,
            String trainName,
            String agentCode,
            ModelConfigTb modelConfig,
            String regionCode,
            String regionName,
            String industryCode,
            String industryName,
            String customerCode,
            String customerName,
            String trainStartDate,
            String trainEndDate,
            String trainMode,
            String timeGranularity,
            Integer recentPeriods,
            Integer enabled,
            String remark) {
        entity.setTrainCode(trainCode);
        entity.setTrainName(trainName);
        entity.setAgentCode(agentCode);
        entity.setModelId(modelConfig.getId());
        entity.setModelCode(modelConfig.getModelCode());
        entity.setModelName(modelConfig.getModelName());
        entity.setScopeType("ALL");
        entity.setRegionCode(regionCode);
        entity.setRegionName(regionName);
        entity.setIndustryCode(industryCode);
        entity.setIndustryName(industryName);
        entity.setCustomerCode(customerCode);
        entity.setCustomerName(customerName);
        String normalizedTrainMode = TextUtils.hasText(trainMode) ? trainMode : "RECENT";
        entity.setTrainMode(normalizedTrainMode);
        entity.setTrainStartDate("RANGE".equals(normalizedTrainMode) ? trainStartDate : null);
        entity.setTrainEndDate("RANGE".equals(normalizedTrainMode) ? trainEndDate : null);
        entity.setTimeGranularity(TextUtils.hasText(timeGranularity) ? timeGranularity : "MONTH");
        entity.setRecentPeriods(
                "RECENT".equals(normalizedTrainMode) ? (recentPeriods == null ? 36 : recentPeriods) : null);
        entity.setEnabled(enabled);
        entity.setRemark(remark);
    }

    private ModelTrainConfigResponse toResp(ModelTrainConfigTb entity) {
        if (entity == null) {
            return null;
        }
        return new ModelTrainConfigResponse(
                entity.getId(),
                entity.getTrainCode(),
                entity.getTrainName(),
                entity.getAgentCode(),
                entity.getModelId(),
                entity.getModelCode(),
                entity.getModelName(),
                entity.getRegionCode(),
                entity.getRegionName(),
                entity.getIndustryCode(),
                entity.getIndustryName(),
                entity.getCustomerCode(),
                entity.getCustomerName(),
                entity.getTrainStartDate(),
                entity.getTrainEndDate(),
                entity.getTrainMode(),
                entity.getTimeGranularity(),
                entity.getRecentPeriods(),
                entity.getEnabled(),
                entity.getRemark(),
                entity.getCreatedBy(),
                entity.getCreatedByName(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
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
