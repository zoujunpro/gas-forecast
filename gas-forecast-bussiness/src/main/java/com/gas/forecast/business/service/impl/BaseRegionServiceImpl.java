package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.dto.request.BaseRegionCreateRequest;
import com.gas.forecast.business.dto.request.BaseRegionDeleteRequest;
import com.gas.forecast.business.dto.request.BaseRegionPageRequest;
import com.gas.forecast.business.dto.request.BaseRegionUpdateRequest;
import com.gas.forecast.business.dto.response.BaseRegionResponse;
import com.gas.forecast.business.enums.BaseCodeType;
import com.gas.forecast.business.service.BaseCodeGenerateService;
import com.gas.forecast.business.service.BaseRegionService;
import com.gas.forecast.business.util.PageUtils;
import com.gas.forecast.common.core.PageInfoDTO;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.BaseRegionTb;
import com.gas.forecast.dao.mapper.BaseRegionTbMapper;
import java.util.Date;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BaseRegionServiceImpl implements BaseRegionService {

    private final BaseRegionTbMapper baseRegionTbMapper;
    private final BaseCodeGenerateService baseCodeGenerateService;

    public BaseRegionServiceImpl(
            BaseRegionTbMapper baseRegionTbMapper, BaseCodeGenerateService baseCodeGenerateService) {
        this.baseRegionTbMapper = baseRegionTbMapper;
        this.baseCodeGenerateService = baseCodeGenerateService;
    }

    @Override
    public PageInfoDTO<BaseRegionResponse> listPage(BaseRegionPageRequest reqDTO) {
        LambdaQueryWrapper<BaseRegionTb> query = Wrappers.lambdaQuery();
        String keyword = reqDTO.keyword();
        if (TextUtils.hasText(keyword)) {
            query.and(wrapper -> wrapper.like(BaseRegionTb::getRegionCode, keyword)
                    .or()
                    .like(BaseRegionTb::getRegionName, keyword)
                    .or()
                    .like(BaseRegionTb::getRemark, keyword));
        }
        query.orderByDesc(BaseRegionTb::getUpdatedAt).orderByDesc(BaseRegionTb::getId);
        int page = reqDTO.page() == null ? 1 : reqDTO.page();
        int size = reqDTO.size() == null ? 10 : reqDTO.size();
        IPage<BaseRegionTb> result = baseRegionTbMapper.selectPage(PageUtils.pageRequest(page, size), query);
        return PageUtils.toPage(
                result, result.getRecords().stream().map(this::toResp).toList());
    }

    @Override
    @Transactional
    public BaseRegionResponse createRegion(BaseRegionCreateRequest reqDTO) {
        BaseRegionTb region = toEntity(reqDTO);
        Date now = new Date();
        region.setId(null);
        region.setRegionCode(baseCodeGenerateService.nextCode(BaseCodeType.REGION));
        region.setCreatedAt(now);
        region.setUpdatedAt(now);
        region.setCreatedBy("system");
        region.setCreatedByName("系统");
        baseRegionTbMapper.insert(region);
        return toResp(region);
    }

    @Override
    public BaseRegionResponse update(BaseRegionUpdateRequest reqDTO) {
        BaseRegionTb region = toEntity(reqDTO);
        Long id = reqDTO.id();
        region.setId(id);
        region.setUpdatedAt(new Date());
        region.setUpdatedBy("system");
        region.setUpdatedByName("系统");
        baseRegionTbMapper.updateById(region);
        return toResp(baseRegionTbMapper.selectById(id));
    }

    @Override
    public void delete(BaseRegionDeleteRequest reqDTO) {
        baseRegionTbMapper.deleteById(reqDTO.id());
    }

    private BaseRegionResponse toResp(BaseRegionTb region) {
        if (region == null) {
            return null;
        }
        return new BaseRegionResponse(
                region.getId(),
                region.getRegionCode(),
                region.getRegionName(),
                region.getRemark(),
                region.getCreatedAt(),
                region.getUpdatedAt());
    }

    private BaseRegionTb toEntity(BaseRegionCreateRequest reqDTO) {
        BaseRegionTb region = new BaseRegionTb();
        region.setRegionName(reqDTO.regionName());
        region.setRemark(reqDTO.remark());
        return region;
    }

    private BaseRegionTb toEntity(BaseRegionUpdateRequest reqDTO) {
        BaseRegionTb region = new BaseRegionTb();
        region.setRegionName(reqDTO.regionName());
        region.setRemark(reqDTO.remark());
        return region;
    }
}
