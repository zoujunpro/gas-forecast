package com.gas.forecast.business.service;

import com.gas.forecast.business.dto.req.ModelFeatureDefinitionCreateReqDTO;
import com.gas.forecast.business.dto.req.ModelFeatureDefinitionDeleteReqDTO;
import com.gas.forecast.business.dto.req.ModelFeatureDefinitionPageReqDTO;
import com.gas.forecast.business.dto.req.ModelFeatureDefinitionUpdateReqDTO;
import com.gas.forecast.business.dto.resp.ModelFeatureDefinitionRespDTO;
import com.gas.forecast.common.core.PageInfoDTO;

public interface ModelFeatureDefinitionService {

    PageInfoDTO<ModelFeatureDefinitionRespDTO> listPage(ModelFeatureDefinitionPageReqDTO reqDTO);

    ModelFeatureDefinitionRespDTO create(ModelFeatureDefinitionCreateReqDTO reqDTO);

    ModelFeatureDefinitionRespDTO update(ModelFeatureDefinitionUpdateReqDTO reqDTO);

    void delete(ModelFeatureDefinitionDeleteReqDTO reqDTO);
}
