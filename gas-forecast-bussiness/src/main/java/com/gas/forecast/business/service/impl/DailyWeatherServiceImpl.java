package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.dto.request.DailyWeatherCreateRequest;
import com.gas.forecast.business.dto.request.DailyWeatherPageRequest;
import com.gas.forecast.business.dto.request.DailyWeatherUpdateRequest;
import com.gas.forecast.business.dto.response.DailyWeatherResponse;
import com.gas.forecast.business.service.DailyWeatherService;
import com.gas.forecast.business.util.PageUtils;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.core.PageInfoDTO;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.BaseRegionTb;
import com.gas.forecast.dao.domain.DataRegionWeatherDailyTb;
import com.gas.forecast.dao.mapper.BaseRegionTbMapper;
import com.gas.forecast.dao.mapper.DataRegionWeatherDailyTbMapper;
import java.text.SimpleDateFormat;
import java.math.BigDecimal;
import java.util.Date;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DailyWeatherServiceImpl implements DailyWeatherService {
    private static final Set<String> WEATHER_TYPES = Set.of("ACTUAL", "FORECAST");
    private static final Set<String> QUALITY_STATUSES = Set.of("VALID", "MISSING", "ABNORMAL");

    private final DataRegionWeatherDailyTbMapper mapper;
    private final BaseRegionTbMapper regionMapper;

    @Override
    public PageInfoDTO<DailyWeatherResponse> listPage(DailyWeatherPageRequest request) {
        LambdaQueryWrapper<DataRegionWeatherDailyTb> query = Wrappers.lambdaQuery();
        if (TextUtils.hasText(request.getKeyword())) {
            String keyword = request.getKeyword().trim();
            query.and(w -> w.like(DataRegionWeatherDailyTb::getRegionName, keyword).or().like(DataRegionWeatherDailyTb::getRegionCode, keyword).or()
                    .like(DataRegionWeatherDailyTb::getSourceCode, keyword).or().like(DataRegionWeatherDailyTb::getWeatherVersion, keyword));
        }
        if (TextUtils.hasText(request.getRegionCode()))
            query.eq(DataRegionWeatherDailyTb::getRegionCode, request.getRegionCode().trim());
        if (TextUtils.hasText(request.getWeatherType()))
            query.eq(DataRegionWeatherDailyTb::getWeatherType, request.getWeatherType().trim().toUpperCase());
        if (TextUtils.hasText(request.getQualityStatus()))
            query.eq(DataRegionWeatherDailyTb::getQualityStatus, request.getQualityStatus().trim().toUpperCase());
        if (TextUtils.hasText(request.getStatDateStart()))
            query.ge(DataRegionWeatherDailyTb::getStatDate, request.getStatDateStart());
        if (TextUtils.hasText(request.getStatDateEnd()))
            query.le(DataRegionWeatherDailyTb::getStatDate, request.getStatDateEnd());
        query.orderByDesc(DataRegionWeatherDailyTb::getStatDate).orderByDesc(DataRegionWeatherDailyTb::getForecastIssueTime).orderByDesc(DataRegionWeatherDailyTb::getId);
        int page = request.getPage() == null ? 1 : request.getPage();
        int size = request.getSize() == null ? 20 : request.getSize();
        IPage<DataRegionWeatherDailyTb> result = mapper.selectPage(PageUtils.pageRequest(page, size), query);
        return PageUtils.toPage(result, result.getRecords().stream().map(this::toResponse).toList());
    }

    @Override
    @Transactional
    public DailyWeatherResponse create(DailyWeatherCreateRequest request) {
        DataRegionWeatherDailyTb entity = toEntity(request);
        ensureUnique(entity, null);
        Date now = new Date();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        mapper.insert(entity);
        return toResponse(entity);
    }

    @Override
    @Transactional
    public DailyWeatherResponse update(DailyWeatherUpdateRequest request) {
        if (mapper.selectById(request.getId()) == null)
            throw new BusinessException("天气记录不存在");
        DataRegionWeatherDailyTb entity = toEntity(request);
        entity.setId(request.getId());
        ensureUnique(entity, request.getId());
        entity.setUpdatedAt(new Date());
        mapper.updateById(entity);
        return toResponse(mapper.selectById(request.getId()));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    private DataRegionWeatherDailyTb toEntity(DailyWeatherCreateRequest request) {
        String weatherType = request.getWeatherType().trim().toUpperCase();
        String qualityStatus = request.getQualityStatus().trim().toUpperCase();
        if (!WEATHER_TYPES.contains(weatherType))
            throw new BusinessException("天气类型仅支持ACTUAL或FORECAST");
        if (!QUALITY_STATUSES.contains(qualityStatus))
            throw new BusinessException("数据质量状态不正确");
        if (request.getTempMin() != null && request.getTempMax() != null && request.getTempMin().compareTo(request.getTempMax()) > 0)
            throw new BusinessException("最低气温不能高于最高气温");

        BaseRegionTb region = regionMapper.selectOne(Wrappers.<BaseRegionTb>lambdaQuery().eq(BaseRegionTb::getRegionCode, request.getRegionCode().trim()).last("limit 1"));
        if (region == null)
            throw new BusinessException("地区不存在：" + request.getRegionCode());
        if ("FORECAST".equals(weatherType) && request.getForecastIssueTime() == null)
            throw new BusinessException("预报天气必须填写预报发布时间");

        DataRegionWeatherDailyTb entity = new DataRegionWeatherDailyTb();
        BeanUtils.copyProperties(request, entity);
        if (entity.getHdd() == null && entity.getTempAvg() != null) {
            entity.setHdd(BigDecimal.valueOf(18).subtract(entity.getTempAvg()).max(BigDecimal.ZERO));
        }
        entity.setRegionCode(region.getRegionCode());
        entity.setRegionName(region.getRegionName());
        entity.setWeatherType(weatherType);
        entity.setQualityStatus(qualityStatus);
        entity.setSourceCode(request.getSourceCode().trim().toUpperCase());
        if ("ACTUAL".equals(weatherType)) {
            entity.setWeatherVersion("ACTUAL");
            entity.setForecastIssueTime(null);
        } else if (TextUtils.hasText(request.getWeatherVersion())) {
            entity.setWeatherVersion(request.getWeatherVersion().trim());
        } else {
            entity.setWeatherVersion(new SimpleDateFormat("yyyyMMddHHmmss").format(request.getForecastIssueTime()));
        }
        return entity;
    }

    private void ensureUnique(DataRegionWeatherDailyTb entity, Long excludeId) {
        LambdaQueryWrapper<DataRegionWeatherDailyTb> query = Wrappers.<DataRegionWeatherDailyTb>lambdaQuery().eq(DataRegionWeatherDailyTb::getRegionCode, entity.getRegionCode())
                .eq(DataRegionWeatherDailyTb::getStatDate, entity.getStatDate()).eq(DataRegionWeatherDailyTb::getWeatherType, entity.getWeatherType())
                .eq(DataRegionWeatherDailyTb::getWeatherVersion, entity.getWeatherVersion()).eq(DataRegionWeatherDailyTb::getSourceCode, entity.getSourceCode());
        if (excludeId != null)
            query.ne(DataRegionWeatherDailyTb::getId, excludeId);
        if (mapper.selectCount(query) > 0)
            throw new BusinessException("相同地区、日期、类型、版本和来源的天气记录已存在");
    }

    private DailyWeatherResponse toResponse(DataRegionWeatherDailyTb entity) {
        DailyWeatherResponse response = new DailyWeatherResponse();
        BeanUtils.copyProperties(entity, response);
        return response;
    }
}
