package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.dto.req.ModelTrainConfigCreateReqDTO;
import com.gas.forecast.business.dto.req.ModelTrainConfigDeleteReqDTO;
import com.gas.forecast.business.dto.req.ModelTrainConfigPageReqDTO;
import com.gas.forecast.business.dto.req.ModelTrainConfigUpdateReqDTO;
import com.gas.forecast.business.dto.resp.ModelTrainConfigRespDTO;
import com.gas.forecast.business.service.ModelTrainConfigService;
import com.gas.forecast.business.util.PageUtils;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.core.PageInfoDTO;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.ModelTrainConfigTb;
import com.gas.forecast.dao.mapper.ModelTrainConfigTbMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ModelTrainConfigServiceImpl implements ModelTrainConfigService {

    private final ModelTrainConfigTbMapper modelTrainConfigTbMapper;

    public ModelTrainConfigServiceImpl(ModelTrainConfigTbMapper modelTrainConfigTbMapper) {
        this.modelTrainConfigTbMapper = modelTrainConfigTbMapper;
    }

    @Override
    public PageInfoDTO<ModelTrainConfigRespDTO> listPage(ModelTrainConfigPageReqDTO reqDTO) {
        LambdaQueryWrapper<ModelTrainConfigTb> query = Wrappers.lambdaQuery();
        String keyword = reqDTO.keyword();
        if (TextUtils.hasText(keyword)) {
            query.and(wrapper -> wrapper
                    .like(ModelTrainConfigTb::getTrainCode, keyword)
                    .or()
                    .like(ModelTrainConfigTb::getTrainName, keyword)
                    .or()
                    .like(ModelTrainConfigTb::getAgentCode, keyword)
                    .or()
                    .like(ModelTrainConfigTb::getModelCode, keyword)
                    .or()
                    .like(ModelTrainConfigTb::getModelName, keyword)
                    .or()
                    .like(ModelTrainConfigTb::getRegionName, keyword)
                    .or()
                    .like(ModelTrainConfigTb::getIndustryName, keyword)
                    .or()
                    .like(ModelTrainConfigTb::getCustomerName, keyword));
        }
        if (TextUtils.hasText(reqDTO.agentCode())) {
            query.eq(ModelTrainConfigTb::getAgentCode, reqDTO.agentCode().trim());
        }
        if (TextUtils.hasText(reqDTO.timeGranularity())) {
            query.eq(ModelTrainConfigTb::getTimeGranularity, reqDTO.timeGranularity().trim());
        }
        query.orderByDesc(ModelTrainConfigTb::getUpdatedAt).orderByDesc(ModelTrainConfigTb::getId);
        int page = reqDTO.page() == null ? 1 : reqDTO.page();
        int size = reqDTO.size() == null ? 10 : reqDTO.size();
        IPage<ModelTrainConfigTb> result = modelTrainConfigTbMapper.selectPage(PageUtils.pageRequest(page, size), query);
        return PageUtils.toPage(result, result.getRecords().stream().map(this::toResp).toList());
    }

    @Override
    @Transactional
    public ModelTrainConfigRespDTO create(ModelTrainConfigCreateReqDTO reqDTO) {
        String trainCode = TextUtils.hasText(reqDTO.trainCode()) ? reqDTO.trainCode().trim() : generateTrainCode();
        ensureTrainCodeUnique(trainCode, null);
        ModelTrainConfigTb entity = toEntity(reqDTO, trainCode);
        Date now = new Date();
        entity.setId(null);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setCreatedBy("system");
        entity.setCreatedByName("系统");
        modelTrainConfigTbMapper.insert(entity);
        return toResp(entity);
    }

    @Override
    @Transactional
    public ModelTrainConfigRespDTO update(ModelTrainConfigUpdateReqDTO reqDTO) {
        ModelTrainConfigTb exists = modelTrainConfigTbMapper.selectById(reqDTO.id());
        if (exists == null) {
            throw new BusinessException("模型训练配置不存在");
        }
        ensureTrainCodeUnique(reqDTO.trainCode(), reqDTO.id());
        ModelTrainConfigTb entity = toEntity(reqDTO);
        entity.setId(reqDTO.id());
        entity.setCreatedAt(exists.getCreatedAt());
        entity.setCreatedBy(exists.getCreatedBy());
        entity.setCreatedByName(exists.getCreatedByName());
        entity.setUpdatedAt(new Date());
        modelTrainConfigTbMapper.updateById(entity);
        return toResp(modelTrainConfigTbMapper.selectById(reqDTO.id()));
    }

    @Override
    @Transactional
    public void delete(ModelTrainConfigDeleteReqDTO reqDTO) {
        modelTrainConfigTbMapper.deleteById(reqDTO.id());
    }

    private void ensureTrainCodeUnique(String trainCode, Long excludeId) {
        LambdaQueryWrapper<ModelTrainConfigTb> query = Wrappers.<ModelTrainConfigTb>lambdaQuery()
                .eq(ModelTrainConfigTb::getTrainCode, trainCode);
        if (excludeId != null) {
            query.ne(ModelTrainConfigTb::getId, excludeId);
        }
        if (modelTrainConfigTbMapper.selectCount(query) > 0) {
            throw new BusinessException("训练配置编码已存在");
        }
    }

    private String generateTrainCode() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        for (int i = 0; i < 5; i++) {
            String suffix = String.valueOf(ThreadLocalRandom.current().nextInt(1000, 10000));
            String trainCode = "TRAIN-" + LocalDateTime.now().format(formatter) + "-" + suffix;
            if (modelTrainConfigTbMapper.selectCount(Wrappers.<ModelTrainConfigTb>lambdaQuery()
                    .eq(ModelTrainConfigTb::getTrainCode, trainCode)) == 0) {
                return trainCode;
            }
        }
        throw new BusinessException("训练配置编码生成失败，请重试");
    }

    private ModelTrainConfigTb toEntity(ModelTrainConfigCreateReqDTO reqDTO, String trainCode) {
        ModelTrainConfigTb entity = new ModelTrainConfigTb();
        fillEntity(entity, trainCode, reqDTO.trainName(), reqDTO.agentCode(), reqDTO.modelCode(), reqDTO.modelName(),
                reqDTO.regionCode(), reqDTO.regionName(), reqDTO.industryCode(), reqDTO.industryName(),
                reqDTO.customerCode(), reqDTO.customerName(), reqDTO.trainStartDate(), reqDTO.trainEndDate(),
                reqDTO.trainMode(), reqDTO.timeGranularity(), reqDTO.recentPeriods(),
                reqDTO.enabled(), reqDTO.remark());
        return entity;
    }

    private ModelTrainConfigTb toEntity(ModelTrainConfigUpdateReqDTO reqDTO) {
        ModelTrainConfigTb entity = new ModelTrainConfigTb();
        fillEntity(entity, reqDTO.trainCode(), reqDTO.trainName(), reqDTO.agentCode(), reqDTO.modelCode(), reqDTO.modelName(),
                reqDTO.regionCode(), reqDTO.regionName(), reqDTO.industryCode(), reqDTO.industryName(),
                reqDTO.customerCode(), reqDTO.customerName(), reqDTO.trainStartDate(), reqDTO.trainEndDate(),
                reqDTO.trainMode(), reqDTO.timeGranularity(), reqDTO.recentPeriods(),
                reqDTO.enabled(), reqDTO.remark());
        return entity;
    }

    private void fillEntity(ModelTrainConfigTb entity,
                            String trainCode,
                            String trainName,
                            String agentCode,
                            String modelCode,
                            String modelName,
                            String regionCode,
                            String regionName,
                            String industryCode,
                            String industryName,
                            String customerCode,
                            String customerName,
                            String trainStartDate,
                            String trainEndDate,
                            String trainMode,
                            String timeGranularity,
                            Integer recentPeriods,
                            Integer enabled,
                            String remark) {
        entity.setTrainCode(trainCode);
        entity.setTrainName(trainName);
        entity.setAgentCode(agentCode);
        entity.setModelCode(modelCode);
        entity.setModelName(modelName);
        entity.setScopeType("ALL");
        entity.setRegionCode(regionCode);
        entity.setRegionName(regionName);
        entity.setIndustryCode(industryCode);
        entity.setIndustryName(industryName);
        entity.setCustomerCode(customerCode);
        entity.setCustomerName(customerName);
        String normalizedTrainMode = TextUtils.hasText(trainMode) ? trainMode : "RECENT";
        entity.setTrainMode(normalizedTrainMode);
        entity.setTrainStartDate("RANGE".equals(normalizedTrainMode) ? trainStartDate : null);
        entity.setTrainEndDate("RANGE".equals(normalizedTrainMode) ? trainEndDate : null);
        entity.setTimeGranularity(TextUtils.hasText(timeGranularity) ? timeGranularity : "MONTH");
        entity.setRecentPeriods("RECENT".equals(normalizedTrainMode) ? (recentPeriods == null ? 36 : recentPeriods) : null);
        entity.setEnabled(enabled);
        entity.setRemark(remark);
    }

    private ModelTrainConfigRespDTO toResp(ModelTrainConfigTb entity) {
        if (entity == null) {
            return null;
        }
        return new ModelTrainConfigRespDTO(
                entity.getId(),
                entity.getTrainCode(),
                entity.getTrainName(),
                entity.getAgentCode(),
                entity.getModelCode(),
                entity.getModelName(),
                entity.getRegionCode(),
                entity.getRegionName(),
                entity.getIndustryCode(),
                entity.getIndustryName(),
                entity.getCustomerCode(),
                entity.getCustomerName(),
                entity.getTrainStartDate(),
                entity.getTrainEndDate(),
                entity.getTrainMode(),
                entity.getTimeGranularity(),
                entity.getRecentPeriods(),
                entity.getEnabled(),
                entity.getRemark(),
                entity.getCreatedBy(),
                entity.getCreatedByName(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
