package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.dto.request.AgentStrategyConfigPageRequest;
import com.gas.forecast.business.dto.request.AgentStrategyConfigSaveRequest;
import com.gas.forecast.business.service.AgentStrategyConfigService;
import com.gas.forecast.business.util.PageUtils;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.core.PageInfoDTO;
import com.gas.forecast.common.enums.ForecastAgentEnum;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.AgentStrategyConfigTb;
import com.gas.forecast.dao.mapper.AgentStrategyConfigTbMapper;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentStrategyConfigServiceImpl implements AgentStrategyConfigService {
    private final AgentStrategyConfigTbMapper mapper;

    @Override
    public PageInfoDTO<AgentStrategyConfigTb> listPage(AgentStrategyConfigPageRequest request) {
        var query = Wrappers.<AgentStrategyConfigTb>lambdaQuery();
        if (TextUtils.hasText(request.getKeyword())) {
            query.and(w -> w.like(AgentStrategyConfigTb::getAgentCode, request.getKeyword()).or().like(AgentStrategyConfigTb::getAgentName, request.getKeyword()).or()
                    .like(AgentStrategyConfigTb::getDescription, request.getKeyword()));
        }
        query.orderByAsc(AgentStrategyConfigTb::getId);
        IPage<AgentStrategyConfigTb> page = mapper.selectPage(PageUtils.pageRequest(request.getPage(), request.getSize()), query);
        return PageUtils.toPage(page, page.getRecords());
    }

    @Override
    @Transactional
    public AgentStrategyConfigTb create(AgentStrategyConfigSaveRequest request) {
        validate(request, null);
        AgentStrategyConfigTb entity = toEntity(request);
        Date now = new Date();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        mapper.insert(entity);
        return entity;
    }

    @Override
    @Transactional
    public AgentStrategyConfigTb update(AgentStrategyConfigSaveRequest request) {
        if (request.getId() == null || mapper.selectById(request.getId()) == null)
            throw new BusinessException("智能体策略不存在");
        validate(request, request.getId());
        AgentStrategyConfigTb entity = toEntity(request);
        entity.setId(request.getId());
        entity.setUpdatedAt(new Date());
        mapper.updateById(entity);
        return mapper.selectById(entity.getId());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    private void validate(AgentStrategyConfigSaveRequest request, Long excludeId) {
        if (ForecastAgentEnum.ofAgentCode(request.getAgentCode()).isEmpty())
            throw new BusinessException("智能体编码不合法");
        if (!java.util.Set.of("CUSTOMER", "REGION", "INDUSTRY", "REGION_INDUSTRY").contains(request.getTrainScopeType()))
            throw new BusinessException("训练主体类型不合法");
        if (!java.util.Set.of("NEW_DATA", "SCHEDULE", "BEFORE_FORECAST", "HYBRID").contains(request.getTriggerType()))
            throw new BusinessException("触发方式不合法");
        if (!CronExpression.isValidExpression(request.getCheckCron()))
            throw new BusinessException("巡检 Cron 表达式不合法");
        var query = Wrappers.<AgentStrategyConfigTb>lambdaQuery().eq(AgentStrategyConfigTb::getAgentCode, request.getAgentCode());
        if (excludeId != null)
            query.ne(AgentStrategyConfigTb::getId, excludeId);
        if (mapper.selectCount(query) > 0)
            throw new BusinessException("该智能体已存在策略配置");
    }

    private AgentStrategyConfigTb toEntity(AgentStrategyConfigSaveRequest r) {
        AgentStrategyConfigTb e = new AgentStrategyConfigTb();
        e.setAgentCode(r.getAgentCode());
        e.setAgentName(r.getAgentName());
        e.setTrainScopeType(r.getTrainScopeType());
        e.setTriggerType(r.getTriggerType());
        e.setCheckCron(r.getCheckCron());
        e.setDataDelayPeriods(r.getDataDelayPeriods());
        e.setModelValidityPeriods(r.getModelValidityPeriods());
        e.setMaxRetryCount(r.getMaxRetryCount());
        e.setRetryIntervalMinutes(r.getRetryIntervalMinutes());
        e.setAutoTrainEnabled(r.getAutoTrainEnabled());
        e.setAutoForecastEnabled(r.getAutoForecastEnabled());
        e.setForecastLeadPeriods(r.getForecastLeadPeriods());
        e.setEnabled(r.getEnabled());
        e.setDescription(r.getDescription());
        return e;
    }
}
