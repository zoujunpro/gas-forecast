package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.dto.req.BaseCustomerCreateReqDTO;
import com.gas.forecast.business.dto.req.BaseCustomerDeleteReqDTO;
import com.gas.forecast.business.dto.req.BaseCustomerPageReqDTO;
import com.gas.forecast.business.dto.req.BaseCustomerUpdateReqDTO;
import com.gas.forecast.business.dto.resp.BaseCustomerRespDTO;
import com.gas.forecast.business.enums.BaseCodeType;
import com.gas.forecast.business.service.BaseCodeGenerateService;
import com.gas.forecast.business.service.BaseCustomerService;
import com.gas.forecast.business.util.PageUtils;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.core.PageInfoDTO;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.BaseIndustryTb;
import com.gas.forecast.dao.domain.BaseCustomerTb;
import com.gas.forecast.dao.domain.BaseRegionTb;
import com.gas.forecast.dao.mapper.BaseCustomerTbMapper;
import com.gas.forecast.dao.mapper.BaseIndustryTbMapper;
import com.gas.forecast.dao.mapper.BaseRegionTbMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * 客户基础信息业务服务实现。
 */
@Service
public class BaseCustomerServiceImpl implements BaseCustomerService {

    private final BaseCustomerTbMapper baseCustomerTbMapper;
    private final BaseRegionTbMapper baseRegionTbMapper;
    private final BaseIndustryTbMapper baseIndustryTbMapper;
    private final BaseCodeGenerateService baseCodeGenerateService;

    public BaseCustomerServiceImpl(BaseCustomerTbMapper baseCustomerTbMapper,
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
    public PageInfoDTO<BaseCustomerRespDTO> listPage(BaseCustomerPageReqDTO reqDTO) {
        LambdaQueryWrapper<BaseCustomerTb> query = Wrappers.lambdaQuery();
        String keyword = reqDTO.keyword();
        if (TextUtils.hasText(keyword)) {
            query.and(wrapper -> wrapper
                    .like(BaseCustomerTb::getCustomerCode, keyword)
                    .or()
                    .like(BaseCustomerTb::getCustomerName, keyword)
                    .or()
                    .like(BaseCustomerTb::getRegionName, keyword)
                    .or()
                    .like(BaseCustomerTb::getIndustryName, keyword));
        }
        query.orderByDesc(BaseCustomerTb::getUpdatedAt).orderByDesc(BaseCustomerTb::getId);
        int page = reqDTO.page() == null ? 1 : reqDTO.page();
        int size = reqDTO.size() == null ? 10 : reqDTO.size();
        IPage<BaseCustomerTb> result = baseCustomerTbMapper.selectPage(PageUtils.pageRequest(page, size), query);
        return PageUtils.toPage(result, result.getRecords().stream().map(this::toResp).toList());
    }

    /**
     * 新增客户。
     */
    @Override
    @Transactional
    public BaseCustomerRespDTO createCustomer(BaseCustomerCreateReqDTO reqDTO) {
        BaseCustomerTb customer = toEntity(reqDTO);
        Date now = new Date();
        customer.setId(null);
        customer.setCustomerCode(baseCodeGenerateService.nextCode(BaseCodeType.CUSTOMER));
        customer.setCreatedAt(now);
        customer.setUpdatedAt(now);
        customer.setCreatedByName("系统");
        baseCustomerTbMapper.insert(customer);
        return toResp(customer);
    }

    /**
     * 更新客户。
     */
    @Override
    public BaseCustomerRespDTO update(BaseCustomerUpdateReqDTO reqDTO) {
        BaseCustomerTb customer = toEntity(reqDTO);
        Long id = reqDTO.id();
        customer.setId(id);
        customer.setUpdatedAt(new Date());
        customer.setUpdatedByName("系统");
        baseCustomerTbMapper.updateById(customer);
        return toResp(baseCustomerTbMapper.selectById(id));
    }

    /**
     * 删除客户。
     */
    @Override
    public void delete(BaseCustomerDeleteReqDTO reqDTO) {
        baseCustomerTbMapper.deleteById(reqDTO.id());
    }

    private BaseCustomerRespDTO toResp(BaseCustomerTb customer) {
        if (customer == null) {
            return null;
        }
        return new BaseCustomerRespDTO(
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
                customer.getUpdatedAt()
        );
    }

    private BaseCustomerTb toEntity(BaseCustomerCreateReqDTO reqDTO) {
        BaseCustomerTb customer = new BaseCustomerTb();
        customer.setCustomerName(reqDTO.customerName());
        customer.setIndustryName(reqDTO.industryName());
        customer.setRegionName(reqDTO.regionName());
        customer.setIndustryCode(resolveIndustryCode(reqDTO.industryName()));
        customer.setRegionCode(resolveRegionCode(reqDTO.regionName()));
        customer.setRawRegionName(reqDTO.rawRegionName());
        customer.setRawIndustryName(reqDTO.rawIndustryName());
        return customer;
    }

    private BaseCustomerTb toEntity(BaseCustomerUpdateReqDTO reqDTO) {
        BaseCustomerTb customer = new BaseCustomerTb();
        customer.setCustomerName(reqDTO.customerName());
        customer.setIndustryName(reqDTO.industryName());
        customer.setRegionName(reqDTO.regionName());
        customer.setIndustryCode(resolveIndustryCode(reqDTO.industryName()));
        customer.setRegionCode(resolveRegionCode(reqDTO.regionName()));
        customer.setRawRegionName(reqDTO.rawRegionName());
        customer.setRawIndustryName(reqDTO.rawIndustryName());
        return customer;
    }

    private String resolveRegionCode(String regionName) {
        BaseRegionTb region = baseRegionTbMapper.selectOne(Wrappers.<BaseRegionTb>lambdaQuery()
                .eq(BaseRegionTb::getRegionName, regionName)
                .last("limit 1"));
        if (region == null) {
            throw new BusinessException("区域不存在: " + regionName);
        }
        return region.getRegionCode();
    }

    private String resolveIndustryCode(String industryName) {
        BaseIndustryTb industry = baseIndustryTbMapper.selectOne(Wrappers.<BaseIndustryTb>lambdaQuery()
                .eq(BaseIndustryTb::getIndustryName, industryName)
                .last("limit 1"));
        if (industry == null) {
            throw new BusinessException("行业不存在: " + industryName);
        }
        return industry.getIndustryCode();
    }
}
