package com.gas.forecast.business.service;

import com.gas.forecast.business.dto.req.ModelTrainConfigCreateReqDTO;
import com.gas.forecast.business.dto.req.ModelTrainConfigDeleteReqDTO;
import com.gas.forecast.business.dto.req.ModelTrainConfigPageReqDTO;
import com.gas.forecast.business.dto.req.ModelTrainConfigUpdateReqDTO;
import com.gas.forecast.business.dto.resp.ModelTrainConfigRespDTO;
import com.gas.forecast.common.core.PageInfoDTO;

public interface ModelTrainConfigService {

    PageInfoDTO<ModelTrainConfigRespDTO> listPage(ModelTrainConfigPageReqDTO reqDTO);

    ModelTrainConfigRespDTO create(ModelTrainConfigCreateReqDTO reqDTO);

    ModelTrainConfigRespDTO update(ModelTrainConfigUpdateReqDTO reqDTO);

    void delete(ModelTrainConfigDeleteReqDTO reqDTO);
}
