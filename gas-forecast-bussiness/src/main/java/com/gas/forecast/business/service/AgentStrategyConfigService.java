package com.gas.forecast.business.service;

import com.gas.forecast.business.dto.request.AgentStrategyConfigPageRequest;
import com.gas.forecast.business.dto.request.AgentStrategyConfigSaveRequest;
import com.gas.forecast.common.core.PageInfoDTO;
import com.gas.forecast.dao.domain.AgentStrategyConfigTb;

public interface AgentStrategyConfigService {
    PageInfoDTO<AgentStrategyConfigTb> listPage(AgentStrategyConfigPageRequest request);
    AgentStrategyConfigTb create(AgentStrategyConfigSaveRequest request);
    AgentStrategyConfigTb update(AgentStrategyConfigSaveRequest request);
    void delete(Long id);
}
