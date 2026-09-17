package com.gas.forecast.business.service;

import com.gas.forecast.business.dto.req.BaseIndustryCreateReqDTO;
import com.gas.forecast.business.dto.req.BaseIndustryDeleteReqDTO;
import com.gas.forecast.business.dto.req.BaseIndustryPageReqDTO;
import com.gas.forecast.business.dto.req.BaseIndustryUpdateReqDTO;
import com.gas.forecast.business.dto.resp.BaseIndustryRespDTO;
import com.gas.forecast.common.core.PageInfoDTO;

public interface BaseIndustryService {

    PageInfoDTO<BaseIndustryRespDTO> listPage(BaseIndustryPageReqDTO reqDTO);

    BaseIndustryRespDTO createIndustry(BaseIndustryCreateReqDTO reqDTO);

    BaseIndustryRespDTO update(BaseIndustryUpdateReqDTO reqDTO);

    void delete(BaseIndustryDeleteReqDTO reqDTO);
}
