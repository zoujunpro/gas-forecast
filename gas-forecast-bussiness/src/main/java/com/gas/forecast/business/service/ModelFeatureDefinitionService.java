package com.gas.forecast.business.service;

import com.gas.forecast.business.dto.request.ModelFeatureDefinitionCreateRequest;
import com.gas.forecast.business.dto.request.ModelFeatureDefinitionDeleteRequest;
import com.gas.forecast.business.dto.request.ModelFeatureDefinitionPageRequest;
import com.gas.forecast.business.dto.request.ModelFeatureDefinitionUpdateRequest;
import com.gas.forecast.business.dto.response.ModelFeatureDefinitionResponse;
import com.gas.forecast.common.core.PageInfoDTO;

public interface ModelFeatureDefinitionService {

    PageInfoDTO<ModelFeatureDefinitionResponse> listPage(ModelFeatureDefinitionPageRequest reqDTO);

    ModelFeatureDefinitionResponse create(ModelFeatureDefinitionCreateRequest reqDTO);

    ModelFeatureDefinitionResponse update(ModelFeatureDefinitionUpdateRequest reqDTO);

    void delete(ModelFeatureDefinitionDeleteRequest reqDTO);
}
