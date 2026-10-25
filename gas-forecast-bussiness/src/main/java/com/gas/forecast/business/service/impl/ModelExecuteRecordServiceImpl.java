package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gas.forecast.business.dto.request.ModelExecuteRecordPageRequest;
import com.gas.forecast.business.application.forecast.ForecastLastYearComparisonService;
import com.gas.forecast.business.service.ModelExecuteRecordService;
import com.gas.forecast.common.core.PageInfoDTO;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.ModelExecuteRecordTb;
import com.gas.forecast.dao.mapper.ModelExecuteRecordTbMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 模型预测记录的查询实现。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ModelExecuteRecordServiceImpl implements ModelExecuteRecordService {
    private final ModelExecuteRecordTbMapper modelExecuteRecordTbMapper;
    private final ForecastLastYearComparisonService forecastLastYearComparisonService;

    @Override
    public PageInfoDTO<ModelExecuteRecordTb> listPage(ModelExecuteRecordPageRequest request) {
        var query = Wrappers.<ModelExecuteRecordTb>lambdaQuery();
        if (TextUtils.hasText(request.getForecastNo())) query.like(ModelExecuteRecordTb::getForecastNo, request.getForecastNo());
        if (TextUtils.hasText(request.getAgentCode())) query.eq(ModelExecuteRecordTb::getAgentCode, request.getAgentCode());
        if (TextUtils.hasText(request.getStatus())) query.eq(ModelExecuteRecordTb::getStatus, request.getStatus());
        if (TextUtils.hasText(request.getRegionCode())) query.and(wrapper -> wrapper.eq(ModelExecuteRecordTb::getRegionCode, request.getRegionCode()).or().like(ModelExecuteRecordTb::getRegionName, request.getRegionCode()));
        if (TextUtils.hasText(request.getIndustryCode())) query.and(wrapper -> wrapper.eq(ModelExecuteRecordTb::getIndustryCode, request.getIndustryCode()).or().like(ModelExecuteRecordTb::getIndustryName, request.getIndustryCode()));
        if (TextUtils.hasText(request.getCustomerCode())) query.and(wrapper -> wrapper.eq(ModelExecuteRecordTb::getCustomerCode, request.getCustomerCode()).or().like(ModelExecuteRecordTb::getCustomerName, request.getCustomerCode()));
        IPage<ModelExecuteRecordTb> page = modelExecuteRecordTbMapper.selectPage(
                new Page<>(request.getPage(), request.getSize()), query.orderByDesc(ModelExecuteRecordTb::getCreatedAt));
        page.getRecords().forEach(record -> record.setLastYearPoints(forecastLastYearComparisonService.lastYearPoints(record)));
        return PageInfoDTO.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public ModelExecuteRecordTb detail(String forecastNo) {
        ModelExecuteRecordTb record = modelExecuteRecordTbMapper.selectOne(Wrappers.<ModelExecuteRecordTb>lambdaQuery()
                .eq(ModelExecuteRecordTb::getForecastNo, forecastNo).last("limit 1"));
        if (record != null) record.setLastYearPoints(forecastLastYearComparisonService.lastYearPoints(record));
        return record;
    }
}
