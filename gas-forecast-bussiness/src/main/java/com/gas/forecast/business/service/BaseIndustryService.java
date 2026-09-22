package com.gas.forecast.business.service;

import com.gas.forecast.business.dto.request.BaseIndustryCreateRequest;
import com.gas.forecast.business.dto.request.BaseIndustryDeleteRequest;
import com.gas.forecast.business.dto.request.BaseIndustryPageRequest;
import com.gas.forecast.business.dto.request.BaseIndustryUpdateRequest;
import com.gas.forecast.business.dto.response.BaseIndustryResponse;
import com.gas.forecast.common.core.PageInfoDTO;

public interface BaseIndustryService {

    PageInfoDTO<BaseIndustryResponse> listPage(BaseIndustryPageRequest reqDTO);

    BaseIndustryResponse createIndustry(BaseIndustryCreateRequest reqDTO);

    BaseIndustryResponse update(BaseIndustryUpdateRequest reqDTO);

    void delete(BaseIndustryDeleteRequest reqDTO);
}
