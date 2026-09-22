package com.gas.forecast.business.service;

import com.gas.forecast.business.dto.request.ModelConfigCreateRequest;
import com.gas.forecast.business.dto.request.ModelConfigDeleteRequest;
import com.gas.forecast.business.dto.request.ModelConfigPageRequest;
import com.gas.forecast.business.dto.request.ModelConfigScopeUpdateRequest;
import com.gas.forecast.business.dto.request.ModelConfigUpdateRequest;
import com.gas.forecast.business.dto.response.ModelConfigResponse;
import com.gas.forecast.common.core.PageInfoDTO;

public interface ModelConfigService {

    PageInfoDTO<ModelConfigResponse> listPage(ModelConfigPageRequest reqDTO);

    ModelConfigResponse create(ModelConfigCreateRequest reqDTO);

    ModelConfigResponse update(ModelConfigUpdateRequest reqDTO);

    ModelConfigResponse updateScope(ModelConfigScopeUpdateRequest reqDTO);

    void delete(ModelConfigDeleteRequest reqDTO);
}
