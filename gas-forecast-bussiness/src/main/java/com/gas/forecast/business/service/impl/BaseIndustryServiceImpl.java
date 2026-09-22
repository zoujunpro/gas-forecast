package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.dto.request.BaseIndustryCreateRequest;
import com.gas.forecast.business.dto.request.BaseIndustryDeleteRequest;
import com.gas.forecast.business.dto.request.BaseIndustryPageRequest;
import com.gas.forecast.business.dto.request.BaseIndustryUpdateRequest;
import com.gas.forecast.business.dto.response.BaseIndustryResponse;
import com.gas.forecast.business.enums.BaseCodeType;
import com.gas.forecast.business.service.BaseCodeGenerateService;
import com.gas.forecast.business.service.BaseIndustryService;
import com.gas.forecast.business.util.PageUtils;
import com.gas.forecast.common.core.PageInfoDTO;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.BaseIndustryTb;
import com.gas.forecast.dao.mapper.BaseIndustryTbMapper;
import java.util.Date;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BaseIndustryServiceImpl implements BaseIndustryService {

    private final BaseIndustryTbMapper baseIndustryTbMapper;
    private final BaseCodeGenerateService baseCodeGenerateService;

    public BaseIndustryServiceImpl(
            BaseIndustryTbMapper baseIndustryTbMapper, BaseCodeGenerateService baseCodeGenerateService) {
        this.baseIndustryTbMapper = baseIndustryTbMapper;
        this.baseCodeGenerateService = baseCodeGenerateService;
    }

    @Override
    public PageInfoDTO<BaseIndustryResponse> listPage(BaseIndustryPageRequest reqDTO) {
        LambdaQueryWrapper<BaseIndustryTb> query = Wrappers.lambdaQuery();
        String keyword = reqDTO.keyword();
        if (TextUtils.hasText(keyword)) {
            query.and(wrapper -> wrapper.like(BaseIndustryTb::getIndustryCode, keyword)
                    .or()
                    .like(BaseIndustryTb::getIndustryName, keyword));
        }
        query.orderByDesc(BaseIndustryTb::getUpdatedAt).orderByDesc(BaseIndustryTb::getId);
        int page = reqDTO.page() == null ? 1 : reqDTO.page();
        int size = reqDTO.size() == null ? 10 : reqDTO.size();
        IPage<BaseIndustryTb> result = baseIndustryTbMapper.selectPage(PageUtils.pageRequest(page, size), query);
        return PageUtils.toPage(
                result, result.getRecords().stream().map(this::toResp).toList());
    }

    @Override
    @Transactional
    public BaseIndustryResponse createIndustry(BaseIndustryCreateRequest reqDTO) {
        BaseIndustryTb industry = toEntity(reqDTO);
        Date now = new Date();
        industry.setId(null);
        industry.setIndustryCode(baseCodeGenerateService.nextCode(BaseCodeType.INDUSTRY));
        industry.setCreatedAt(now);
        industry.setUpdatedAt(now);
        industry.setCreatedByName("系统");
        baseIndustryTbMapper.insert(industry);
        return toResp(industry);
    }

    @Override
    public BaseIndustryResponse update(BaseIndustryUpdateRequest reqDTO) {
        BaseIndustryTb industry = toEntity(reqDTO);
        Long id = reqDTO.id();
        industry.setId(id);
        industry.setUpdatedAt(new Date());
        industry.setUpdatedByName("系统");
        baseIndustryTbMapper.updateById(industry);
        return toResp(baseIndustryTbMapper.selectById(id));
    }

    @Override
    public void delete(BaseIndustryDeleteRequest reqDTO) {
        baseIndustryTbMapper.deleteById(reqDTO.id());
    }

    private BaseIndustryResponse toResp(BaseIndustryTb industry) {
        if (industry == null) {
            return null;
        }
        return new BaseIndustryResponse(
                industry.getId(),
                industry.getIndustryCode(),
                industry.getIndustryName(),
                industry.getCreatedAt(),
                industry.getUpdatedAt());
    }

    private BaseIndustryTb toEntity(BaseIndustryCreateRequest reqDTO) {
        BaseIndustryTb industry = new BaseIndustryTb();
        industry.setIndustryName(reqDTO.industryName());
        return industry;
    }

    private BaseIndustryTb toEntity(BaseIndustryUpdateRequest reqDTO) {
        BaseIndustryTb industry = new BaseIndustryTb();
        industry.setIndustryName(reqDTO.industryName());
        return industry;
    }
}
