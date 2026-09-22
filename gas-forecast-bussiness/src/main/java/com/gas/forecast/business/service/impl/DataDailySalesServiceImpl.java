package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.dto.request.DataSalesPageRequest;
import com.gas.forecast.business.dto.response.DataSalesResponse;
import com.gas.forecast.business.service.DataDailySalesService;
import com.gas.forecast.business.util.PageUtils;
import com.gas.forecast.common.core.PageInfoDTO;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.DataDailySalesTb;
import com.gas.forecast.dao.mapper.DataDailySalesTbMapper;
import org.springframework.stereotype.Service;

@Service
public class DataDailySalesServiceImpl implements DataDailySalesService {

    private final DataDailySalesTbMapper dataDailySalesTbMapper;

    public DataDailySalesServiceImpl(DataDailySalesTbMapper dataDailySalesTbMapper) {
        this.dataDailySalesTbMapper = dataDailySalesTbMapper;
    }

    @Override
    public PageInfoDTO<DataSalesResponse> listPage(DataSalesPageRequest reqDTO) {
        LambdaQueryWrapper<DataDailySalesTb> query = Wrappers.lambdaQuery();
        String keyword = reqDTO.keyword();
        if (TextUtils.hasText(keyword)) {
            query.and(wrapper -> wrapper.like(DataDailySalesTb::getRegionCode, keyword)
                    .or()
                    .like(DataDailySalesTb::getRegionName, keyword)
                    .or()
                    .like(DataDailySalesTb::getIndustryCode, keyword)
                    .or()
                    .like(DataDailySalesTb::getIndustryName, keyword)
                    .or()
                    .like(DataDailySalesTb::getCustomerCode, keyword)
                    .or()
                    .like(DataDailySalesTb::getCustomerName, keyword)
                    .or()
                    .like(DataDailySalesTb::getFileId, keyword));
        }
        if (TextUtils.hasText(reqDTO.startDate())) {
            query.ge(DataDailySalesTb::getStatDate, reqDTO.startDate());
        }
        if (TextUtils.hasText(reqDTO.endDate())) {
            query.le(DataDailySalesTb::getStatDate, reqDTO.endDate());
        }
        query.orderByDesc(DataDailySalesTb::getStatDate).orderByDesc(DataDailySalesTb::getId);
        int page = reqDTO.page() == null ? 1 : reqDTO.page();
        int size = reqDTO.size() == null ? 10 : reqDTO.size();
        IPage<DataDailySalesTb> result = dataDailySalesTbMapper.selectPage(PageUtils.pageRequest(page, size), query);
        return PageUtils.toPage(
                result, result.getRecords().stream().map(this::toResp).toList());
    }

    private DataSalesResponse toResp(DataDailySalesTb item) {
        if (item == null) {
            return null;
        }
        return new DataSalesResponse(
                item.getId(),
                item.getStatDate(),
                item.getRegionCode(),
                item.getRegionName(),
                item.getIndustryCode(),
                item.getIndustryName(),
                item.getCustomerCode(),
                item.getCustomerName(),
                item.getGasSales(),
                item.getFileId(),
                item.getCreatedAt());
    }
}
