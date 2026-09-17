package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.dto.req.BaseIndustryCreateReqDTO;
import com.gas.forecast.business.dto.req.BaseIndustryDeleteReqDTO;
import com.gas.forecast.business.dto.req.BaseIndustryPageReqDTO;
import com.gas.forecast.business.dto.req.BaseIndustryUpdateReqDTO;
import com.gas.forecast.business.dto.resp.BaseIndustryRespDTO;
import com.gas.forecast.business.enums.BaseCodeType;
import com.gas.forecast.business.service.BaseCodeGenerateService;
import com.gas.forecast.business.service.BaseIndustryService;
import com.gas.forecast.business.util.PageUtils;
import com.gas.forecast.common.core.PageInfoDTO;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.BaseIndustryTb;
import com.gas.forecast.dao.mapper.BaseIndustryTbMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class BaseIndustryServiceImpl implements BaseIndustryService {

    private final BaseIndustryTbMapper baseIndustryTbMapper;
    private final BaseCodeGenerateService baseCodeGenerateService;

    public BaseIndustryServiceImpl(BaseIndustryTbMapper baseIndustryTbMapper,
                                   BaseCodeGenerateService baseCodeGenerateService) {
        this.baseIndustryTbMapper = baseIndustryTbMapper;
        this.baseCodeGenerateService = baseCodeGenerateService;
    }

    @Override
    public PageInfoDTO<BaseIndustryRespDTO> listPage(BaseIndustryPageReqDTO reqDTO) {
        LambdaQueryWrapper<BaseIndustryTb> query = Wrappers.lambdaQuery();
        String keyword = reqDTO.keyword();
        if (TextUtils.hasText(keyword)) {
            query.and(wrapper -> wrapper
                    .like(BaseIndustryTb::getIndustryCode, keyword)
                    .or()
                    .like(BaseIndustryTb::getIndustryName, keyword));
        }
        query.orderByDesc(BaseIndustryTb::getUpdatedAt).orderByDesc(BaseIndustryTb::getId);
        int page = reqDTO.page() == null ? 1 : reqDTO.page();
        int size = reqDTO.size() == null ? 10 : reqDTO.size();
        IPage<BaseIndustryTb> result = baseIndustryTbMapper.selectPage(PageUtils.pageRequest(page, size), query);
        return PageUtils.toPage(result, result.getRecords().stream().map(this::toResp).toList());
    }

    @Override
    @Transactional
    public BaseIndustryRespDTO createIndustry(BaseIndustryCreateReqDTO reqDTO) {
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
    public BaseIndustryRespDTO update(BaseIndustryUpdateReqDTO reqDTO) {
        BaseIndustryTb industry = toEntity(reqDTO);
        Long id = reqDTO.id();
        industry.setId(id);
        industry.setUpdatedAt(new Date());
        industry.setUpdatedByName("系统");
        baseIndustryTbMapper.updateById(industry);
        return toResp(baseIndustryTbMapper.selectById(id));
    }

    @Override
    public void delete(BaseIndustryDeleteReqDTO reqDTO) {
        baseIndustryTbMapper.deleteById(reqDTO.id());
    }

    private BaseIndustryRespDTO toResp(BaseIndustryTb industry) {
        if (industry == null) {
            return null;
        }
        return new BaseIndustryRespDTO(
                industry.getId(),
                industry.getIndustryCode(),
                industry.getIndustryName(),
                industry.getCreatedAt(),
                industry.getUpdatedAt()
        );
    }

    private BaseIndustryTb toEntity(BaseIndustryCreateReqDTO reqDTO) {
        BaseIndustryTb industry = new BaseIndustryTb();
        industry.setIndustryName(reqDTO.industryName());
        return industry;
    }

    private BaseIndustryTb toEntity(BaseIndustryUpdateReqDTO reqDTO) {
        BaseIndustryTb industry = new BaseIndustryTb();
        industry.setIndustryName(reqDTO.industryName());
        return industry;
    }
}
