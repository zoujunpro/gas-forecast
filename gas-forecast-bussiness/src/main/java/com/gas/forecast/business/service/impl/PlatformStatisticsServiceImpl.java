package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.dto.response.PlatformStatisticsResponse;
import com.gas.forecast.business.service.PlatformStatisticsService;
import com.gas.forecast.dao.domain.ModelForecastRecordTb;
import com.gas.forecast.dao.mapper.BaseCustomerTbMapper;
import com.gas.forecast.dao.mapper.BaseIndustryTbMapper;
import com.gas.forecast.dao.mapper.BaseRegionTbMapper;
import com.gas.forecast.dao.mapper.DataDailySalesTbMapper;
import com.gas.forecast.dao.mapper.DataMonthlySalesTbMapper;
import com.gas.forecast.dao.mapper.ModelConfigTbMapper;
import com.gas.forecast.dao.mapper.ModelForecastRecordTbMapper;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlatformStatisticsServiceImpl implements PlatformStatisticsService {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final BaseRegionTbMapper baseRegionTbMapper;
    private final BaseIndustryTbMapper baseIndustryTbMapper;
    private final BaseCustomerTbMapper baseCustomerTbMapper;
    private final DataDailySalesTbMapper dataDailySalesTbMapper;
    private final DataMonthlySalesTbMapper dataMonthlySalesTbMapper;
    private final ModelConfigTbMapper modelConfigTbMapper;
    private final ModelForecastRecordTbMapper modelForecastRecordTbMapper;

    @Override
    public PlatformStatisticsResponse getStatistics() {
        long dailySales = dataDailySalesTbMapper.selectCount(null);
        long monthlySales = dataMonthlySalesTbMapper.selectCount(null);
        ModelForecastRecordTb latestRecord = modelForecastRecordTbMapper
                .selectOne(Wrappers.<ModelForecastRecordTb>lambdaQuery().select(ModelForecastRecordTb::getCreatedAt, ModelForecastRecordTb::getUpdatedAt)
                        .orderByDesc(ModelForecastRecordTb::getUpdatedAt).orderByDesc(ModelForecastRecordTb::getCreatedAt).orderByDesc(ModelForecastRecordTb::getId).last("LIMIT 1"));

        return new PlatformStatisticsResponse(baseRegionTbMapper.selectCount(null), baseIndustryTbMapper.selectCount(null), baseCustomerTbMapper.selectCount(null), dailySales + monthlySales,
                modelConfigTbMapper.selectCount(null), formatLatestUpdate(latestRecord));
    }

    private String formatLatestUpdate(ModelForecastRecordTb record) {
        if (record == null) {
            return "";
        }
        Date latest = record.getUpdatedAt() == null ? record.getCreatedAt() : record.getUpdatedAt();
        return latest == null ? "" : latest.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FORMATTER);
    }
}
