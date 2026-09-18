package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.dto.req.ModelTrainFeatureDataCreateReqDTO;
import com.gas.forecast.business.dto.req.ModelTrainFeatureDataDeleteReqDTO;
import com.gas.forecast.business.dto.req.ModelTrainFeatureDataPageReqDTO;
import com.gas.forecast.business.dto.req.ModelTrainFeatureDataUpdateReqDTO;
import com.gas.forecast.business.dto.resp.ModelTrainFeatureDataRespDTO;
import com.gas.forecast.business.dto.resp.ModelTrainFeatureValueRespDTO;
import com.gas.forecast.business.service.ModelTrainFeatureDataService;
import com.gas.forecast.business.util.PageUtils;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.core.PageInfoDTO;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.ModelFeatureDefinitionTb;
import com.gas.forecast.dao.domain.ModelTrainFeatureDataTb;
import com.gas.forecast.dao.mapper.ModelFeatureDefinitionTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainFeatureDataTbMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ModelTrainFeatureDataServiceImpl implements ModelTrainFeatureDataService {

    private final ModelTrainFeatureDataTbMapper modelTrainFeatureDataTbMapper;
    private final ModelFeatureDefinitionTbMapper modelFeatureDefinitionTbMapper;

    public ModelTrainFeatureDataServiceImpl(ModelTrainFeatureDataTbMapper modelTrainFeatureDataTbMapper,
                                            ModelFeatureDefinitionTbMapper modelFeatureDefinitionTbMapper) {
        this.modelTrainFeatureDataTbMapper = modelTrainFeatureDataTbMapper;
        this.modelFeatureDefinitionTbMapper = modelFeatureDefinitionTbMapper;
    }

    @Override
    public PageInfoDTO<ModelTrainFeatureDataRespDTO> listPage(ModelTrainFeatureDataPageReqDTO reqDTO) {
        LambdaQueryWrapper<ModelTrainFeatureDataTb> query = Wrappers.lambdaQuery();
        String keyword = reqDTO.keyword();
        if (TextUtils.hasText(keyword)) {
            query.and(wrapper -> wrapper
                    .like(ModelTrainFeatureDataTb::getStatDate, keyword)
                    .or()
                    .like(ModelTrainFeatureDataTb::getTimeGranularity, keyword)
                    .or()
                    .like(ModelTrainFeatureDataTb::getRegionCode, keyword)
                    .or()
                    .like(ModelTrainFeatureDataTb::getRegionName, keyword)
                    .or()
                    .like(ModelTrainFeatureDataTb::getCustomerCode, keyword)
                    .or()
                    .like(ModelTrainFeatureDataTb::getCustomerName, keyword)
                    .or()
                    .like(ModelTrainFeatureDataTb::getIndustryCode, keyword)
                    .or()
                    .like(ModelTrainFeatureDataTb::getIndustryName, keyword));
        }
        if (TextUtils.hasText(reqDTO.timeGranularity())) {
            query.eq(ModelTrainFeatureDataTb::getTimeGranularity, reqDTO.timeGranularity().trim());
        }
        if (TextUtils.hasText(reqDTO.statDate())) {
            query.like(ModelTrainFeatureDataTb::getStatDate, reqDTO.statDate().trim());
        }
        if (TextUtils.hasText(reqDTO.regionCode())) {
            query.eq(ModelTrainFeatureDataTb::getRegionCode, reqDTO.regionCode().trim());
        }
        if (TextUtils.hasText(reqDTO.industryCode())) {
            query.eq(ModelTrainFeatureDataTb::getIndustryCode, reqDTO.industryCode().trim());
        }
        if (TextUtils.hasText(reqDTO.customerCode())) {
            query.eq(ModelTrainFeatureDataTb::getCustomerCode, reqDTO.customerCode().trim());
        }
        if (TextUtils.hasText(reqDTO.statDateStart())) {
            query.ge(ModelTrainFeatureDataTb::getStatDate, reqDTO.statDateStart().trim());
        }
        if (TextUtils.hasText(reqDTO.statDateEnd())) {
            query.le(ModelTrainFeatureDataTb::getStatDate, reqDTO.statDateEnd().trim());
        }
        applySort(query, reqDTO);
        int page = reqDTO.page() == null ? 1 : reqDTO.page();
        int size = reqDTO.size() == null ? 10 : reqDTO.size();
        IPage<ModelTrainFeatureDataTb> result = modelTrainFeatureDataTbMapper.selectPage(PageUtils.pageRequest(page, size), query);
        Map<String, String> featureCodeByColumn = featureCodeByColumn();
        return PageUtils.toPage(result, result.getRecords().stream().map(entity -> toResp(entity, featureCodeByColumn)).toList());
    }

    @Override
    @Transactional
    public ModelTrainFeatureDataRespDTO create(ModelTrainFeatureDataCreateReqDTO reqDTO) {
        ModelTrainFeatureDataTb entity = toEntity(reqDTO);
        Date now = new Date();
        entity.setId(null);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        modelTrainFeatureDataTbMapper.insert(entity);
        return toResp(entity, featureCodeByColumn());
    }

    @Override
    @Transactional
    public ModelTrainFeatureDataRespDTO update(ModelTrainFeatureDataUpdateReqDTO reqDTO) {
        ModelTrainFeatureDataTb exists = modelTrainFeatureDataTbMapper.selectById(reqDTO.id());
        if (exists == null) {
            throw new BusinessException("训练特征数据不存在");
        }
        ModelTrainFeatureDataTb entity = toEntity(reqDTO);
        entity.setId(reqDTO.id());
        entity.setCreateTime(exists.getCreateTime());
        entity.setUpdateTime(new Date());
        modelTrainFeatureDataTbMapper.updateById(entity);
        return toResp(modelTrainFeatureDataTbMapper.selectById(reqDTO.id()), featureCodeByColumn());
    }

    @Override
    @Transactional
    public void delete(ModelTrainFeatureDataDeleteReqDTO reqDTO) {
        modelTrainFeatureDataTbMapper.deleteById(reqDTO.id());
    }

    private ModelTrainFeatureDataRespDTO toResp(ModelTrainFeatureDataTb entity, Map<String, String> featureCodeByColumn) {
        if (entity == null) {
            return null;
        }
        Map<String, Double> featureValues = featureValues(entity);
        return new ModelTrainFeatureDataRespDTO(
                entity.getId(),
                entity.getStatDate(),
                entity.getTimeGranularity(),
                entity.getRegionCode(),
                entity.getRegionName(),
                entity.getCustomerCode(),
                entity.getCustomerName(),
                entity.getIndustryCode(),
                entity.getIndustryName(),
                entity.getGasSales(),
                entity.getFeature001(),
                entity.getFeature002(),
                entity.getFeature003(),
                entity.getFeature004(),
                entity.getFeature005(),
                entity.getFeature006(),
                entity.getFeature007(),
                entity.getFeature008(),
                entity.getFeature009(),
                entity.getFeature010(),
                featureValues,
                featureDetails(featureValues, featureCodeByColumn),
                entity.getCreateTime(),
                entity.getUpdateTime()
        );
    }

    private Map<String, String> featureCodeByColumn() {
        return modelFeatureDefinitionTbMapper.selectList(Wrappers.<ModelFeatureDefinitionTb>lambdaQuery()
                        .select(ModelFeatureDefinitionTb::getFeatureColumn, ModelFeatureDefinitionTb::getFeatureCode)
                        .eq(ModelFeatureDefinitionTb::getEnabled, 1))
                .stream()
                .filter(item -> TextUtils.hasText(item.getFeatureColumn()) && TextUtils.hasText(item.getFeatureCode()))
                .collect(Collectors.toMap(ModelFeatureDefinitionTb::getFeatureColumn, ModelFeatureDefinitionTb::getFeatureCode, (left, right) -> left));
    }

    private List<ModelTrainFeatureValueRespDTO> featureDetails(Map<String, Double> featureValues, Map<String, String> featureCodeByColumn) {
        return featureValues.entrySet().stream()
                .map(entry -> new ModelTrainFeatureValueRespDTO(
                        entry.getKey(),
                        featureCodeByColumn.getOrDefault(featureColumn(entry.getKey()), "-"),
                        entry.getValue()
                ))
                .toList();
    }

    private String featureColumn(String featureNo) {
        if (!TextUtils.hasText(featureNo) || !featureNo.matches("feature\\d{3}")) {
            return featureNo;
        }
        return "feature_" + featureNo.substring("feature".length());
    }

    private void applySort(LambdaQueryWrapper<ModelTrainFeatureDataTb> query, ModelTrainFeatureDataPageReqDTO reqDTO) {
        boolean asc = "asc".equalsIgnoreCase(reqDTO.sortOrder()) || "ascending".equalsIgnoreCase(reqDTO.sortOrder());
        boolean desc = "desc".equalsIgnoreCase(reqDTO.sortOrder()) || "descending".equalsIgnoreCase(reqDTO.sortOrder());
        String sortField = TextUtils.hasText(reqDTO.sortField()) ? reqDTO.sortField().trim() : "statDate";
        if (!asc && !desc) {
            desc = true;
        }
        switch (sortField) {
            case "statDate" -> query.orderBy(true, asc, ModelTrainFeatureDataTb::getStatDate);
            case "updateTime" -> query.orderBy(true, asc, ModelTrainFeatureDataTb::getUpdateTime);
            case "createTime" -> query.orderBy(true, asc, ModelTrainFeatureDataTb::getCreateTime);
            case "id" -> query.orderBy(true, asc, ModelTrainFeatureDataTb::getId);
            default -> query.orderByDesc(ModelTrainFeatureDataTb::getStatDate);
        }
        query.orderByDesc(ModelTrainFeatureDataTb::getId);
    }

    private Map<String, Double> featureValues(ModelTrainFeatureDataTb entity) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (int i = 1; i <= 200; i++) {
            String property = String.format("feature%03d", i);
            try {
                Object value = ModelTrainFeatureDataTb.class.getMethod("get" + Character.toUpperCase(property.charAt(0)) + property.substring(1)).invoke(entity);
                if (value instanceof Double doubleValue && doubleValue != null) {
                    values.put(property, doubleValue);
                }
            } catch (ReflectiveOperationException ignored) {
                break;
            }
        }
        return values;
    }

    private ModelTrainFeatureDataTb toEntity(ModelTrainFeatureDataCreateReqDTO reqDTO) {
        ModelTrainFeatureDataTb entity = new ModelTrainFeatureDataTb();
        entity.setStatDate(reqDTO.statDate());
        entity.setTimeGranularity(reqDTO.timeGranularity());
        entity.setRegionCode(reqDTO.regionCode());
        entity.setRegionName(reqDTO.regionName());
        entity.setCustomerCode(reqDTO.customerCode());
        entity.setCustomerName(reqDTO.customerName());
        entity.setIndustryCode(reqDTO.industryCode());
        entity.setIndustryName(reqDTO.industryName());
        entity.setGasSales(reqDTO.gasSales());
        entity.setFeature001(reqDTO.feature001());
        entity.setFeature002(reqDTO.feature002());
        entity.setFeature003(reqDTO.feature003());
        entity.setFeature004(reqDTO.feature004());
        entity.setFeature005(reqDTO.feature005());
        entity.setFeature006(reqDTO.feature006());
        entity.setFeature007(reqDTO.feature007());
        entity.setFeature008(reqDTO.feature008());
        entity.setFeature009(reqDTO.feature009());
        entity.setFeature010(reqDTO.feature010());
        return entity;
    }

    private ModelTrainFeatureDataTb toEntity(ModelTrainFeatureDataUpdateReqDTO reqDTO) {
        ModelTrainFeatureDataTb entity = new ModelTrainFeatureDataTb();
        entity.setStatDate(reqDTO.statDate());
        entity.setTimeGranularity(reqDTO.timeGranularity());
        entity.setRegionCode(reqDTO.regionCode());
        entity.setRegionName(reqDTO.regionName());
        entity.setCustomerCode(reqDTO.customerCode());
        entity.setCustomerName(reqDTO.customerName());
        entity.setIndustryCode(reqDTO.industryCode());
        entity.setIndustryName(reqDTO.industryName());
        entity.setGasSales(reqDTO.gasSales());
        entity.setFeature001(reqDTO.feature001());
        entity.setFeature002(reqDTO.feature002());
        entity.setFeature003(reqDTO.feature003());
        entity.setFeature004(reqDTO.feature004());
        entity.setFeature005(reqDTO.feature005());
        entity.setFeature006(reqDTO.feature006());
        entity.setFeature007(reqDTO.feature007());
        entity.setFeature008(reqDTO.feature008());
        entity.setFeature009(reqDTO.feature009());
        entity.setFeature010(reqDTO.feature010());
        return entity;
    }

}
