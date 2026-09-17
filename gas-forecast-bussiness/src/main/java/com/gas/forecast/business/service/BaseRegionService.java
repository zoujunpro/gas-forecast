package com.gas.forecast.business.service;

import com.gas.forecast.business.dto.req.BaseRegionCreateReqDTO;
import com.gas.forecast.business.dto.req.BaseRegionDeleteReqDTO;
import com.gas.forecast.business.dto.req.BaseRegionPageReqDTO;
import com.gas.forecast.business.dto.req.BaseRegionUpdateReqDTO;
import com.gas.forecast.business.dto.resp.BaseRegionRespDTO;
import com.gas.forecast.common.core.PageInfoDTO;

public interface BaseRegionService {

    PageInfoDTO<BaseRegionRespDTO> listPage(BaseRegionPageReqDTO reqDTO);

    BaseRegionRespDTO createRegion(BaseRegionCreateReqDTO reqDTO);

    BaseRegionRespDTO update(BaseRegionUpdateReqDTO reqDTO);

    void delete(BaseRegionDeleteReqDTO reqDTO);
}
