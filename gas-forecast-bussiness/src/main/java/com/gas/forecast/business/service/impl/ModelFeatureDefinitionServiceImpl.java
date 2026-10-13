package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.dto.request.ModelFeatureDefinitionCreateRequest;
import com.gas.forecast.business.dto.request.ModelFeatureDefinitionPageRequest;
import com.gas.forecast.business.dto.request.ModelFeatureDefinitionUpdateRequest;
import com.gas.forecast.business.dto.response.ModelFeatureDefinitionResponse;
import com.gas.forecast.business.enums.FeatureSourceType;
import com.gas.forecast.business.enums.FeatureStatus;
import com.gas.forecast.business.service.ModelFeatureDefinitionService;
import com.gas.forecast.business.util.PageUtils;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.core.PageInfoDTO;
import com.gas.forecast.common.security.context.SecurityContextHolder;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.ModelFeatureDefinitionTb;
import com.gas.forecast.dao.mapper.ModelFeatureDefinitionTbMapper;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ModelFeatureDefinitionServiceImpl implements ModelFeatureDefinitionService {

    private final ModelFeatureDefinitionTbMapper modelFeatureDefinitionTbMapper;

    @Override
    public PageInfoDTO<ModelFeatureDefinitionResponse> listPage(ModelFeatureDefinitionPageRequest reqDTO) {
        LambdaQueryWrapper<ModelFeatureDefinitionTb> query = Wrappers.lambdaQuery();
        String keyword = reqDTO.keyword();
        if (TextUtils.hasText(keyword)) {
            query.and(wrapper -> wrapper.like(ModelFeatureDefinitionTb::getFeatureCode, keyword).or().like(ModelFeatureDefinitionTb::getFeatureName, keyword).or()
                    .like(ModelFeatureDefinitionTb::getSourceType, keyword).or().like(ModelFeatureDefinitionTb::getValueSource, keyword).or().like(ModelFeatureDefinitionTb::getSourceField, keyword)
                    .or().like(ModelFeatureDefinitionTb::getStatus, keyword).or().like(ModelFeatureDefinitionTb::getDescription, keyword));
        }
        if (TextUtils.hasText(reqDTO.sourceType())) {
            query.eq(ModelFeatureDefinitionTb::getSourceType, normalizeSourceType(reqDTO.sourceType()));
        }
        if (TextUtils.hasText(reqDTO.status())) {
            query.eq(ModelFeatureDefinitionTb::getStatus, normalizeStatus(reqDTO.status()));
        }
        query.orderByDesc(ModelFeatureDefinitionTb::getCreatedAt).orderByDesc(ModelFeatureDefinitionTb::getId);
        int page = reqDTO.page() == null ? 1 : reqDTO.page();
        int size = reqDTO.size() == null ? 20 : reqDTO.size();
        IPage<ModelFeatureDefinitionTb> result = modelFeatureDefinitionTbMapper.selectPage(PageUtils.pageRequest(page, size), query);
        return PageUtils.toPage(result, result.getRecords().stream().map(this::toResp).toList());
    }

    @Override
    @Transactional
    public ModelFeatureDefinitionResponse create(ModelFeatureDefinitionCreateRequest reqDTO) {
        ensureFeatureCodeUnique(reqDTO.featureCode(), null);
        ModelFeatureDefinitionTb entity = toEntity(reqDTO);
        entity.setId(null);
        entity.setCreatedAt(new Date());
        entity.setCreatedBy(SecurityContextHolder.getUserId());
        modelFeatureDefinitionTbMapper.insert(entity);
        return toResp(entity);
    }

    @Override
    @Transactional
    public ModelFeatureDefinitionResponse update(ModelFeatureDefinitionUpdateRequest reqDTO) {
        ModelFeatureDefinitionTb exists = modelFeatureDefinitionTbMapper.selectById(reqDTO.id());
        if (exists == null) {
            throw new BusinessException("特征定义不存在");
        }
        ensureFeatureCodeUnique(reqDTO.featureCode(), reqDTO.id());
        ModelFeatureDefinitionTb entity = toEntity(reqDTO);
        entity.setId(reqDTO.id());
        entity.setCreatedAt(exists.getCreatedAt());
        entity.setCreatedBy(exists.getCreatedBy());
        entity.setUpdatedByName(SecurityContextHolder.getUserName());
        modelFeatureDefinitionTbMapper.updateById(entity);
        return toResp(modelFeatureDefinitionTbMapper.selectById(reqDTO.id()));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        modelFeatureDefinitionTbMapper.deleteById(id);
    }

    private void ensureFeatureCodeUnique(String featureCode, Long excludeId) {
        LambdaQueryWrapper<ModelFeatureDefinitionTb> query = Wrappers.<ModelFeatureDefinitionTb>lambdaQuery().eq(ModelFeatureDefinitionTb::getFeatureCode, featureCode);
        if (excludeId != null) {
            query.ne(ModelFeatureDefinitionTb::getId, excludeId);
        }
        if (modelFeatureDefinitionTbMapper.selectCount(query) > 0) {
            throw new BusinessException("特征编号已存在");
        }
    }

    private ModelFeatureDefinitionResponse toResp(ModelFeatureDefinitionTb entity) {
        if (entity == null) {
            return null;
        }
        return new ModelFeatureDefinitionResponse(entity.getId(), entity.getFeatureCode(), entity.getFeatureName(), entity.getSourceType(), entity.getValueSource(), entity.getSourceField(),
                entity.getCalculationType(), entity.getCalculationParams(), entity.getAggregationMethod(), entity.getMissingValueStrategy(), entity.getStatus(), entity.getEnabled(),
                entity.getDescription(), entity.getCreatedAt(), entity.getCreatedBy(), entity.getUpdatedByName());
    }

    private ModelFeatureDefinitionTb toEntity(ModelFeatureDefinitionCreateRequest reqDTO) {
        ModelFeatureDefinitionTb entity = new ModelFeatureDefinitionTb();
        entity.setFeatureCode(reqDTO.featureCode());
        entity.setFeatureName(reqDTO.featureName());
        entity.setSourceType(normalizeSourceType(reqDTO.sourceType()));
        fillValueSource(entity, reqDTO.valueSource(), reqDTO.sourceField(), reqDTO.calculationType(), reqDTO.calculationParams(), reqDTO.aggregationMethod(), reqDTO.missingValueStrategy());
        entity.setStatus(normalizeStatus(reqDTO.status()));
        entity.setEnabled(reqDTO.enabled());
        entity.setDescription(reqDTO.description());
        return entity;
    }

    private ModelFeatureDefinitionTb toEntity(ModelFeatureDefinitionUpdateRequest reqDTO) {
        ModelFeatureDefinitionTb entity = new ModelFeatureDefinitionTb();
        entity.setFeatureCode(reqDTO.featureCode());
        entity.setFeatureName(reqDTO.featureName());
        entity.setSourceType(normalizeSourceType(reqDTO.sourceType()));
        fillValueSource(entity, reqDTO.valueSource(), reqDTO.sourceField(), reqDTO.calculationType(), reqDTO.calculationParams(), reqDTO.aggregationMethod(), reqDTO.missingValueStrategy());
        entity.setStatus(normalizeStatus(reqDTO.status()));
        entity.setEnabled(reqDTO.enabled());
        entity.setDescription(reqDTO.description());
        return entity;
    }

    private String normalizeSourceType(String code) {
        try {
            return FeatureSourceType.fromCode(code).name();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(exception.getMessage());
        }
    }

    private String normalizeStatus(String code) {
        try {
            return FeatureStatus.fromCode(code).name();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(exception.getMessage());
        }
    }

    private void fillValueSource(ModelFeatureDefinitionTb entity, String valueSource, String sourceField, String calculationType, String calculationParams, String aggregationMethod,
            String missingValueStrategy) {
        String normalizedSource = valueSource.trim().toUpperCase();
        java.util.Set<String> sources = java.util.Set.of("WEATHER_DAILY", "SALES_DAILY", "FEATURE_VALUE", "CALCULATED", "MODEL_INTERNAL");
        if (!sources.contains(normalizedSource))
            throw new BusinessException("不支持的取值来源：" + valueSource);
        if (("WEATHER_DAILY".equals(normalizedSource) || "SALES_DAILY".equals(normalizedSource) || "CALCULATED".equals(normalizedSource)) && !TextUtils.hasText(sourceField))
            throw new BusinessException("该取值来源必须配置来源字段");
        validateSourceField(normalizedSource, sourceField);
        if ("CALCULATED".equals(normalizedSource) && !TextUtils.hasText(calculationType))
            throw new BusinessException("计算特征必须配置计算类型");
        String missing = missingValueStrategy.trim().toUpperCase();
        if (!java.util.Set.of("ERROR", "IGNORE", "ZERO", "FORWARD_FILL", "INTERPOLATE").contains(missing))
            throw new BusinessException("不支持的缺失值策略：" + missingValueStrategy);
        String aggregation = TextUtils.hasText(aggregationMethod) ? aggregationMethod.trim().toUpperCase() : null;
        if (aggregation != null && !java.util.Set.of("AVG", "SUM", "MAX", "MIN", "COUNT", "LAST").contains(aggregation))
            throw new BusinessException("不支持的聚合方式：" + aggregationMethod);
        entity.setValueSource(normalizedSource);
        entity.setSourceField(TextUtils.hasText(sourceField) ? sourceField.trim() : null);
        entity.setCalculationType(TextUtils.hasText(calculationType) ? calculationType.trim().toUpperCase() : null);
        entity.setCalculationParams(TextUtils.hasText(calculationParams) ? calculationParams.trim() : null);
        entity.setAggregationMethod(aggregation);
        entity.setMissingValueStrategy(missing);
    }

    private void validateSourceField(String valueSource, String sourceField) {
        if (!TextUtils.hasText(sourceField))
            return;
        String field = sourceField.trim().toLowerCase();
        java.util.Set<String> allowed;
        if ("SALES_DAILY".equals(valueSource)) {
            allowed = java.util.Set.of("gas_sales");
        } else if ("WEATHER_DAILY".equals(valueSource)) {
            allowed = java.util.Set.of("temp_max", "temp_min", "temp_avg", "feels_like_max", "feels_like_min", "feels_like_avg", "dew_point", "humidity", "precipitation", "wind_gust", "wind_speed",
                    "sea_level_pressure", "cloud_cover", "visibility", "solar_radiation", "solar_energy", "uv_index", "hdd");
        } else if ("CALCULATED".equals(valueSource)) {
            allowed = java.util.Set.of("temp_max", "temp_min", "temp_avg", "hdd", "extreme_cold_days");
        } else {
            return;
        }
        if (!allowed.contains(field)) {
            throw new BusinessException("取值来源 " + valueSource + " 不存在或不支持字段：" + sourceField);
        }
    }

}
