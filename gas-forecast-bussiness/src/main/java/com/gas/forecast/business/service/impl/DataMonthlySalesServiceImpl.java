package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.dto.req.DataSalesPageReqDTO;
import com.gas.forecast.business.dto.resp.DataSalesRespDTO;
import com.gas.forecast.business.service.DataMonthlySalesService;
import com.gas.forecast.business.util.PageUtils;
import com.gas.forecast.common.core.PageInfoDTO;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.DataMonthlySalesTb;
import com.gas.forecast.dao.mapper.DataMonthlySalesTbMapper;
import org.springframework.stereotype.Service;

@Service
public class DataMonthlySalesServiceImpl implements DataMonthlySalesService {

    private final DataMonthlySalesTbMapper dataMonthlySalesTbMapper;

    public DataMonthlySalesServiceImpl(DataMonthlySalesTbMapper dataMonthlySalesTbMapper) {
        this.dataMonthlySalesTbMapper = dataMonthlySalesTbMapper;
    }

    @Override
    public PageInfoDTO<DataSalesRespDTO> listPage(DataSalesPageReqDTO reqDTO) {
        LambdaQueryWrapper<DataMonthlySalesTb> query = Wrappers.lambdaQuery();
        String keyword = reqDTO.keyword();
        if (TextUtils.hasText(keyword)) {
            query.and(wrapper -> wrapper
                    .like(DataMonthlySalesTb::getRegionCode, keyword)
                    .or()
                    .like(DataMonthlySalesTb::getRegionName, keyword)
                    .or()
                    .like(DataMonthlySalesTb::getIndustryCode, keyword)
                    .or()
                    .like(DataMonthlySalesTb::getIndustryName, keyword)
                    .or()
                    .like(DataMonthlySalesTb::getCustomerCode, keyword)
                    .or()
                    .like(DataMonthlySalesTb::getCustomerName, keyword)
                    .or()
                    .like(DataMonthlySalesTb::getFileId, keyword));
        }
        if (TextUtils.hasText(reqDTO.startDate())) {
            query.ge(DataMonthlySalesTb::getStatDate, reqDTO.startDate());
        }
        if (TextUtils.hasText(reqDTO.endDate())) {
            query.le(DataMonthlySalesTb::getStatDate, reqDTO.endDate());
        }
        query.orderByDesc(DataMonthlySalesTb::getStatDate).orderByDesc(DataMonthlySalesTb::getId);
        int page = reqDTO.page() == null ? 1 : reqDTO.page();
        int size = reqDTO.size() == null ? 10 : reqDTO.size();
        IPage<DataMonthlySalesTb> result = dataMonthlySalesTbMapper.selectPage(PageUtils.pageRequest(page, size), query);
        return PageUtils.toPage(result, result.getRecords().stream().map(this::toResp).toList());
    }

    private DataSalesRespDTO toResp(DataMonthlySalesTb item) {
        if (item == null) {
            return null;
        }
        return new DataSalesRespDTO(
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
                item.getCreatedAt()
        );
    }
}
