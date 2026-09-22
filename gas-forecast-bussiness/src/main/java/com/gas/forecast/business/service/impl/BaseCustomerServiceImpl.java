package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.dto.request.BaseCustomerCreateRequest;
import com.gas.forecast.business.dto.request.BaseCustomerDeleteRequest;
import com.gas.forecast.business.dto.request.BaseCustomerPageRequest;
import com.gas.forecast.business.dto.request.BaseCustomerUpdateRequest;
import com.gas.forecast.business.dto.response.BaseCustomerResponse;
import com.gas.forecast.business.enums.BaseCodeType;
import com.gas.forecast.business.service.BaseCodeGenerateService;
import com.gas.forecast.business.service.BaseCustomerService;
import com.gas.forecast.business.util.PageUtils;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.core.PageInfoDTO;
import com.gas.forecast.common.security.context.SecurityContextHolder;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.BaseCustomerTb;
import com.gas.forecast.dao.domain.BaseIndustryTb;
import com.gas.forecast.dao.domain.BaseRegionTb;
import com.gas.forecast.dao.mapper.BaseCustomerTbMapper;
import com.gas.forecast.dao.mapper.BaseIndustryTbMapper;
import com.gas.forecast.dao.mapper.BaseRegionTbMapper;
import java.util.Date;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 客户基础信息业务服务实现。
 */
@Service
public class BaseCustomerServiceImpl implements BaseCustomerService {

    private final BaseCustomerTbMapper baseCustomerTbMapper;
    private final BaseRegionTbMapper baseRegionTbMapper;
    private final BaseIndustryTbMapper baseIndustryTbMapper;
    private final BaseCodeGenerateService baseCodeGenerateService;

    public BaseCustomerServiceImpl(
            BaseCustomerTbMapper baseCustomerTbMapper,
            BaseRegionTbMapper baseRegionTbMapper,
            BaseIndustryTbMapper baseIndustryTbMapper,
            BaseCodeGenerateService baseCodeGenerateService) {
        this.baseCustomerTbMapper = baseCustomerTbMapper;
        this.baseRegionTbMapper = baseRegionTbMapper;
        this.baseIndustryTbMapper = baseIndustryTbMapper;
        this.baseCodeGenerateService = baseCodeGenerateService;
    }

    /**
     * 分页查询客户列表。
     */
    @Override
    public PageInfoDTO<BaseCustomerResponse> listPage(BaseCustomerPageRequest reqDTO) {
        LambdaQueryWrapper<BaseCustomerTb> query = Wrappers.lambdaQuery();
        String keyword = reqDTO.getKeyword();
        if (TextUtils.hasText(keyword)) {
            query.and(wrapper -> wrapper.like(BaseCustomerTb::getCustomerCode, keyword)
                    .or()
                    .like(BaseCustomerTb::getCustomerName, keyword)
                    .or()
                    .like(BaseCustomerTb::getRegionName, keyword)
                    .or()
                    .like(BaseCustomerTb::getIndustryName, keyword));
        }
        query.orderByDesc(BaseCustomerTb::getUpdatedAt).orderByDesc(BaseCustomerTb::getId);

        IPage<BaseCustomerTb> result =
                baseCustomerTbMapper.selectPage(PageUtils.pageRequest(reqDTO.getPage(), reqDTO.getSize()), query);
        return PageUtils.toPage(
                result, result.getRecords().stream().map(this::toResp).toList());
    }

    /**
     * 新增客户。
     */
    @Override
    @Transactional
    public BaseCustomerResponse createCustomer(BaseCustomerCreateRequest reqDTO) {
        BaseCustomerTb customer = toEntity(reqDTO);
        Date now = new Date();
        customer.setId(null);
        customer.setCustomerCode(baseCodeGenerateService.nextCode(BaseCodeType.CUSTOMER));
        customer.setCreatedAt(now);
        customer.setUpdatedAt(now);
        customer.setCreatedByName(SecurityContextHolder.getUserName());
        customer.setCreatedBy(SecurityContextHolder.getUserId());
        baseCustomerTbMapper.insert(customer);
        return toResp(customer);
    }

    /**
     * 更新客户。
     */
    @Override
    public BaseCustomerResponse update(BaseCustomerUpdateRequest reqDTO) {
        BaseCustomerTb customer = toEntity(reqDTO);
        Long id = reqDTO.id();
        customer.setId(id);
        customer.setUpdatedAt(new Date());
        customer.setUpdatedByName(SecurityContextHolder.getUserName());
        customer.setUpdatedBy(SecurityContextHolder.getUserId());
        baseCustomerTbMapper.updateById(customer);
        return toResp(baseCustomerTbMapper.selectById(id));
    }

    /**
     * 删除客户。
     */
    @Override
    public void delete(BaseCustomerDeleteRequest reqDTO) {
        baseCustomerTbMapper.deleteById(reqDTO.id());
    }

    private BaseCustomerResponse toResp(BaseCustomerTb customer) {
        if (customer == null) {
            return null;
        }
        return new BaseCustomerResponse(
                customer.getId(),
                customer.getCustomerCode(),
                customer.getCustomerName(),
                customer.getIndustryCode(),
                customer.getIndustryName(),
                customer.getRegionCode(),
                customer.getRegionName(),
                customer.getRawRegionName(),
                customer.getRawIndustryName(),
                customer.getCreatedAt(),
                customer.getUpdatedAt());
    }

    private BaseCustomerTb toEntity(BaseCustomerCreateRequest reqDTO) {
        BaseRegionTb region = requireRegion(reqDTO.getRegionCode());
        BaseIndustryTb industry = requireIndustry(reqDTO.getIndustryCode());
        BaseCustomerTb customer = new BaseCustomerTb();
        customer.setCustomerName(reqDTO.getCustomerName());
        customer.setIndustryCode(industry.getIndustryCode());
        customer.setIndustryName(industry.getIndustryName());
        customer.setRegionCode(region.getRegionCode());
        customer.setRegionName(region.getRegionName());
        customer.setRawRegionName(reqDTO.getRawRegionName());
        customer.setRawIndustryName(reqDTO.getRawIndustryName());
        return customer;
    }

    private BaseCustomerTb toEntity(BaseCustomerUpdateRequest reqDTO) {
        BaseRegionTb region = requireRegion(reqDTO.getRegionCode());
        BaseIndustryTb industry = requireIndustry(reqDTO.getIndustryCode());
        BaseCustomerTb customer = new BaseCustomerTb();
        customer.setCustomerName(reqDTO.getCustomerName());
        customer.setIndustryCode(industry.getIndustryCode());
        customer.setIndustryName(industry.getIndustryName());
        customer.setRegionCode(region.getRegionCode());
        customer.setRegionName(region.getRegionName());
        customer.setRawRegionName(reqDTO.getRawRegionName());
        customer.setRawIndustryName(reqDTO.getRawIndustryName());
        return customer;
    }

    private BaseRegionTb requireRegion(String regionCode) {
        BaseRegionTb region = baseRegionTbMapper.selectOne(Wrappers.<BaseRegionTb>lambdaQuery()
                .eq(BaseRegionTb::getRegionCode, regionCode)
                .last("limit 1"));
        if (region == null) {
            throw new BusinessException("区域不存在: " + regionCode);
        }
        return region;
    }

    private BaseIndustryTb requireIndustry(String industryCode) {
        BaseIndustryTb industry = baseIndustryTbMapper.selectOne(Wrappers.<BaseIndustryTb>lambdaQuery()
                .eq(BaseIndustryTb::getIndustryCode, industryCode)
                .last("limit 1"));
        if (industry == null) {
            throw new BusinessException("行业不存在: " + industryCode);
        }
        return industry;
    }
}
