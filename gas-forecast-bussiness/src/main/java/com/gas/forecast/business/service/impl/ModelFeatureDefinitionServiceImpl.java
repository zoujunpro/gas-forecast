package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.dto.request.ModelFeatureDefinitionCreateRequest;
import com.gas.forecast.business.dto.request.ModelFeatureDefinitionDeleteRequest;
import com.gas.forecast.business.dto.request.ModelFeatureDefinitionPageRequest;
import com.gas.forecast.business.dto.request.ModelFeatureDefinitionUpdateRequest;
import com.gas.forecast.business.dto.response.ModelFeatureDefinitionResponse;
import com.gas.forecast.business.service.ModelFeatureDefinitionService;
import com.gas.forecast.business.util.PageUtils;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.core.PageInfoDTO;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.ModelFeatureDefinitionTb;
import com.gas.forecast.dao.mapper.ModelFeatureDefinitionTbMapper;
import java.util.Date;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelFeatureDefinitionServiceImpl implements ModelFeatureDefinitionService {

    private final ModelFeatureDefinitionTbMapper modelFeatureDefinitionTbMapper;

    public ModelFeatureDefinitionServiceImpl(ModelFeatureDefinitionTbMapper modelFeatureDefinitionTbMapper) {
        this.modelFeatureDefinitionTbMapper = modelFeatureDefinitionTbMapper;
    }

    @Override
    public PageInfoDTO<ModelFeatureDefinitionResponse> listPage(ModelFeatureDefinitionPageRequest reqDTO) {
        LambdaQueryWrapper<ModelFeatureDefinitionTb> query = Wrappers.lambdaQuery();
        String keyword = reqDTO.keyword();
        if (TextUtils.hasText(keyword)) {
            query.and(wrapper -> wrapper.like(ModelFeatureDefinitionTb::getFeatureCode, keyword)
                    .or()
                    .like(ModelFeatureDefinitionTb::getFeatureName, keyword)
                    .or()
                    .like(ModelFeatureDefinitionTb::getFeatureColumn, keyword)
                    .or()
                    .like(ModelFeatureDefinitionTb::getTimeGranularity, keyword)
                    .or()
                    .like(ModelFeatureDefinitionTb::getDescription, keyword));
        }
        if (TextUtils.hasText(reqDTO.timeGranularity())) {
            query.eq(
                    ModelFeatureDefinitionTb::getTimeGranularity,
                    reqDTO.timeGranularity().trim());
        }
        query.orderByDesc(ModelFeatureDefinitionTb::getCreatedAt).orderByDesc(ModelFeatureDefinitionTb::getId);
        int page = reqDTO.page() == null ? 1 : reqDTO.page();
        int size = reqDTO.size() == null ? 10 : reqDTO.size();
        IPage<ModelFeatureDefinitionTb> result =
                modelFeatureDefinitionTbMapper.selectPage(PageUtils.pageRequest(page, size), query);
        return PageUtils.toPage(
                result, result.getRecords().stream().map(this::toResp).toList());
    }

    @Override
    @Transactional
    public ModelFeatureDefinitionResponse create(ModelFeatureDefinitionCreateRequest reqDTO) {
        ensureFeatureCodeUnique(reqDTO.featureCode(), reqDTO.timeGranularity(), null);
        ModelFeatureDefinitionTb entity = toEntity(reqDTO);
        entity.setId(null);
        entity.setFeatureColumn(resolveFeatureColumn(reqDTO.featureColumn(), null));
        entity.setCreatedAt(new Date());
        entity.setCreatedBy(0L);
        entity.setUpdatedByName("系统");
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
        ensureFeatureCodeUnique(reqDTO.featureCode(), reqDTO.timeGranularity(), reqDTO.id());
        ModelFeatureDefinitionTb entity = toEntity(reqDTO);
        entity.setId(reqDTO.id());
        entity.setFeatureColumn(
                TextUtils.hasText(reqDTO.featureColumn())
                        ? resolveFeatureColumn(reqDTO.featureColumn(), reqDTO.id())
                        : exists.getFeatureColumn());
        entity.setCreatedAt(exists.getCreatedAt());
        entity.setCreatedBy(exists.getCreatedBy());
        entity.setUpdatedByName("系统");
        modelFeatureDefinitionTbMapper.updateById(entity);
        return toResp(modelFeatureDefinitionTbMapper.selectById(reqDTO.id()));
    }

    @Override
    @Transactional
    public void delete(ModelFeatureDefinitionDeleteRequest reqDTO) {
        modelFeatureDefinitionTbMapper.deleteById(reqDTO.id());
    }

    private void ensureFeatureCodeUnique(String featureCode, String timeGranularity, Long excludeId) {
        LambdaQueryWrapper<ModelFeatureDefinitionTb> query = Wrappers.<ModelFeatureDefinitionTb>lambdaQuery()
                .eq(ModelFeatureDefinitionTb::getFeatureCode, featureCode)
                .eq(ModelFeatureDefinitionTb::getTimeGranularity, timeGranularity);
        if (excludeId != null) {
            query.ne(ModelFeatureDefinitionTb::getId, excludeId);
        }
        if (modelFeatureDefinitionTbMapper.selectCount(query) > 0) {
            throw new BusinessException("相同时间跨度下特征编号已存在");
        }
    }

    private String resolveFeatureColumn(String requestedColumn, Long excludeId) {
        if (TextUtils.hasText(requestedColumn)) {
            String normalized = requestedColumn.trim();
            if (!normalized.matches("^feature_\\d{3}$")) {
                throw new BusinessException("宽表字段格式必须为feature_001到feature_200");
            }
            int slot = Integer.parseInt(normalized.substring("feature_".length()));
            if (slot < 1 || slot > 200) {
                throw new BusinessException("宽表字段范围必须为feature_001到feature_200");
            }
            ensureFeatureColumnUnique(normalized, excludeId);
            return normalized;
        }

        for (int slot = 1; slot <= 200; slot++) {
            String candidate = String.format("feature_%03d", slot);
            if (modelFeatureDefinitionTbMapper.selectCount(Wrappers.<ModelFeatureDefinitionTb>lambdaQuery()
                            .eq(ModelFeatureDefinitionTb::getFeatureColumn, candidate))
                    == 0) {
                return candidate;
            }
        }
        throw new BusinessException("宽表特征槽位已用完");
    }

    private void ensureFeatureColumnUnique(String featureColumn, Long excludeId) {
        LambdaQueryWrapper<ModelFeatureDefinitionTb> query = Wrappers.<ModelFeatureDefinitionTb>lambdaQuery()
                .eq(ModelFeatureDefinitionTb::getFeatureColumn, featureColumn);
        if (excludeId != null) {
            query.ne(ModelFeatureDefinitionTb::getId, excludeId);
        }
        if (modelFeatureDefinitionTbMapper.selectCount(query) > 0) {
            throw new BusinessException("宽表字段已被使用");
        }
    }

    private ModelFeatureDefinitionResponse toResp(ModelFeatureDefinitionTb entity) {
        if (entity == null) {
            return null;
        }
        return new ModelFeatureDefinitionResponse(
                entity.getId(),
                entity.getFeatureCode(),
                entity.getFeatureName(),
                entity.getFeatureColumn(),
                entity.getTimeGranularity(),
                entity.getEnabled(),
                entity.getDescription(),
                entity.getCreatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedByName());
    }

    private ModelFeatureDefinitionTb toEntity(ModelFeatureDefinitionCreateRequest reqDTO) {
        ModelFeatureDefinitionTb entity = new ModelFeatureDefinitionTb();
        entity.setFeatureCode(reqDTO.featureCode());
        entity.setFeatureName(reqDTO.featureName());
        entity.setFeatureColumn(reqDTO.featureColumn());
        entity.setTimeGranularity(reqDTO.timeGranularity());
        entity.setEnabled(reqDTO.enabled());
        entity.setDescription(reqDTO.description());
        return entity;
    }

    private ModelFeatureDefinitionTb toEntity(ModelFeatureDefinitionUpdateRequest reqDTO) {
        ModelFeatureDefinitionTb entity = new ModelFeatureDefinitionTb();
        entity.setFeatureCode(reqDTO.featureCode());
        entity.setFeatureName(reqDTO.featureName());
        entity.setFeatureColumn(reqDTO.featureColumn());
        entity.setTimeGranularity(reqDTO.timeGranularity());
        entity.setEnabled(reqDTO.enabled());
        entity.setDescription(reqDTO.description());
        return entity;
    }
}
