package com.gas.forecast.business.service;

import com.gas.forecast.business.dto.req.ModelConfigCreateReqDTO;
import com.gas.forecast.business.dto.req.ModelConfigDeleteReqDTO;
import com.gas.forecast.business.dto.req.ModelConfigPageReqDTO;
import com.gas.forecast.business.dto.req.ModelConfigUpdateReqDTO;
import com.gas.forecast.business.dto.resp.ModelConfigRespDTO;
import com.gas.forecast.common.core.PageInfoDTO;

public interface ModelConfigService {

    PageInfoDTO<ModelConfigRespDTO> listPage(ModelConfigPageReqDTO reqDTO);

    ModelConfigRespDTO create(ModelConfigCreateReqDTO reqDTO);

    ModelConfigRespDTO update(ModelConfigUpdateReqDTO reqDTO);

    void delete(ModelConfigDeleteReqDTO reqDTO);
}
