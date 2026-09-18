package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.dto.req.ModelTrainFeatureDataCreateReqDTO;
import com.gas.forecast.business.dto.req.ModelTrainFeatureDataDeleteReqDTO;
import com.gas.forecast.business.dto.req.ModelTrainFeatureDataPageReqDTO;
import com.gas.forecast.business.dto.req.ModelTrainFeatureDataUpdateReqDTO;
import com.gas.forecast.business.dto.resp.ModelTrainFeatureDataRespDTO;
import com.gas.forecast.business.service.ModelTrainFeatureDataService;
import com.gas.forecast.business.util.PageUtils;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.core.PageInfoDTO;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.ModelTrainFeatureDataTb;
import com.gas.forecast.dao.mapper.ModelTrainFeatureDataTbMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class ModelTrainFeatureDataServiceImpl implements ModelTrainFeatureDataService {

    private final ModelTrainFeatureDataTbMapper modelTrainFeatureDataTbMapper;

    public ModelTrainFeatureDataServiceImpl(ModelTrainFeatureDataTbMapper modelTrainFeatureDataTbMapper) {
        this.modelTrainFeatureDataTbMapper = modelTrainFeatureDataTbMapper;
    }

    @Override
    public PageInfoDTO<ModelTrainFeatureDataRespDTO> listPage(ModelTrainFeatureDataPageReqDTO reqDTO) {
        LambdaQueryWrapper<ModelTrainFeatureDataTb> query = Wrappers.lambdaQuery();
        String keyword = reqDTO.keyword();
        if (TextUtils.hasText(keyword)) {
            query.and(wrapper -> wrapper
                    .like(ModelTrainFeatureDataTb::getStatDate, keyword)
                    .or()
                    .like(ModelTrainFeatureDataTb::getTimeGranularity, keyword)
                    .or()
                    .like(ModelTrainFeatureDataTb::getRegionCode, keyword)
                    .or()
                    .like(ModelTrainFeatureDataTb::getRegionName, keyword)
                    .or()
                    .like(ModelTrainFeatureDataTb::getCustomerCode, keyword)
                    .or()
                    .like(ModelTrainFeatureDataTb::getCustomerName, keyword)
                    .or()
                    .like(ModelTrainFeatureDataTb::getIndustryCode, keyword)
                    .or()
                    .like(ModelTrainFeatureDataTb::getIndustryName, keyword));
        }
        query.orderByDesc(ModelTrainFeatureDataTb::getUpdateTime).orderByDesc(ModelTrainFeatureDataTb::getId);
        int page = reqDTO.page() == null ? 1 : reqDTO.page();
        int size = reqDTO.size() == null ? 10 : reqDTO.size();
        IPage<ModelTrainFeatureDataTb> result = modelTrainFeatureDataTbMapper.selectPage(PageUtils.pageRequest(page, size), query);
        return PageUtils.toPage(result, result.getRecords().stream().map(this::toResp).toList());
    }

    @Override
    @Transactional
    public ModelTrainFeatureDataRespDTO create(ModelTrainFeatureDataCreateReqDTO reqDTO) {
        ModelTrainFeatureDataTb entity = toEntity(reqDTO);
        Date now = new Date();
        entity.setId(null);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        modelTrainFeatureDataTbMapper.insert(entity);
        return toResp(entity);
    }

    @Override
    @Transactional
    public ModelTrainFeatureDataRespDTO update(ModelTrainFeatureDataUpdateReqDTO reqDTO) {
        ModelTrainFeatureDataTb exists = modelTrainFeatureDataTbMapper.selectById(reqDTO.id());
        if (exists == null) {
            throw new BusinessException("训练特征数据不存在");
        }
        ModelTrainFeatureDataTb entity = toEntity(reqDTO);
        entity.setId(reqDTO.id());
        entity.setCreateTime(exists.getCreateTime());
        entity.setUpdateTime(new Date());
        modelTrainFeatureDataTbMapper.updateById(entity);
        return toResp(modelTrainFeatureDataTbMapper.selectById(reqDTO.id()));
    }

    @Override
    @Transactional
    public void delete(ModelTrainFeatureDataDeleteReqDTO reqDTO) {
        modelTrainFeatureDataTbMapper.deleteById(reqDTO.id());
    }

    private ModelTrainFeatureDataRespDTO toResp(ModelTrainFeatureDataTb entity) {
        if (entity == null) {
            return null;
        }
        return new ModelTrainFeatureDataRespDTO(
                entity.getId(),
                entity.getStatDate(),
                entity.getTimeGranularity(),
                entity.getRegionCode(),
                entity.getRegionName(),
                entity.getCustomerCode(),
                entity.getCustomerName(),
                entity.getIndustryCode(),
                entity.getIndustryName(),
                entity.getGasSales(),
                entity.getFeature001(),
                entity.getFeature002(),
                entity.getFeature003(),
                entity.getFeature004(),
                entity.getFeature005(),
                entity.getFeature006(),
                entity.getFeature007(),
                entity.getFeature008(),
                entity.getFeature009(),
                entity.getFeature010(),
                entity.getCreateTime(),
                entity.getUpdateTime()
        );
    }

    private ModelTrainFeatureDataTb toEntity(ModelTrainFeatureDataCreateReqDTO reqDTO) {
        ModelTrainFeatureDataTb entity = new ModelTrainFeatureDataTb();
        entity.setStatDate(reqDTO.statDate());
        entity.setTimeGranularity(reqDTO.timeGranularity());
        entity.setRegionCode(reqDTO.regionCode());
        entity.setRegionName(reqDTO.regionName());
        entity.setCustomerCode(reqDTO.customerCode());
        entity.setCustomerName(reqDTO.customerName());
        entity.setIndustryCode(reqDTO.industryCode());
        entity.setIndustryName(reqDTO.industryName());
        entity.setGasSales(reqDTO.gasSales());
        entity.setFeature001(reqDTO.feature001());
        entity.setFeature002(reqDTO.feature002());
        entity.setFeature003(reqDTO.feature003());
        entity.setFeature004(reqDTO.feature004());
        entity.setFeature005(reqDTO.feature005());
        entity.setFeature006(reqDTO.feature006());
        entity.setFeature007(reqDTO.feature007());
        entity.setFeature008(reqDTO.feature008());
        entity.setFeature009(reqDTO.feature009());
        entity.setFeature010(reqDTO.feature010());
        return entity;
    }

    private ModelTrainFeatureDataTb toEntity(ModelTrainFeatureDataUpdateReqDTO reqDTO) {
        ModelTrainFeatureDataTb entity = new ModelTrainFeatureDataTb();
        entity.setStatDate(reqDTO.statDate());
        entity.setTimeGranularity(reqDTO.timeGranularity());
        entity.setRegionCode(reqDTO.regionCode());
        entity.setRegionName(reqDTO.regionName());
        entity.setCustomerCode(reqDTO.customerCode());
        entity.setCustomerName(reqDTO.customerName());
        entity.setIndustryCode(reqDTO.industryCode());
        entity.setIndustryName(reqDTO.industryName());
        entity.setGasSales(reqDTO.gasSales());
        entity.setFeature001(reqDTO.feature001());
        entity.setFeature002(reqDTO.feature002());
        entity.setFeature003(reqDTO.feature003());
        entity.setFeature004(reqDTO.feature004());
        entity.setFeature005(reqDTO.feature005());
        entity.setFeature006(reqDTO.feature006());
        entity.setFeature007(reqDTO.feature007());
        entity.setFeature008(reqDTO.feature008());
        entity.setFeature009(reqDTO.feature009());
        entity.setFeature010(reqDTO.feature010());
        return entity;
    }
}
