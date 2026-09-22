package com.gas.forecast.business.service;

import com.gas.forecast.business.dto.request.BaseRegionCreateRequest;
import com.gas.forecast.business.dto.request.BaseRegionPageRequest;
import com.gas.forecast.business.dto.request.BaseRegionUpdateRequest;
import com.gas.forecast.business.dto.response.BaseRegionResponse;
import com.gas.forecast.common.core.PageInfoDTO;

public interface BaseRegionService {

    PageInfoDTO<BaseRegionResponse> listPage(BaseRegionPageRequest reqDTO);

    BaseRegionResponse createRegion(BaseRegionCreateRequest reqDTO);

    BaseRegionResponse update(BaseRegionUpdateRequest reqDTO);

    void delete(Long id);
}
