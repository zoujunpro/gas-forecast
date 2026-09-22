package com.gas.forecast.business.service;

import com.gas.forecast.business.dto.request.ModelTrainConfigCreateRequest;
import com.gas.forecast.business.dto.request.ModelTrainConfigDeleteRequest;
import com.gas.forecast.business.dto.request.ModelTrainConfigPageRequest;
import com.gas.forecast.business.dto.request.ModelTrainConfigUpdateRequest;
import com.gas.forecast.business.dto.response.ModelTrainConfigResponse;
import com.gas.forecast.common.core.PageInfoDTO;

public interface ModelTrainConfigService {

    PageInfoDTO<ModelTrainConfigResponse> listPage(ModelTrainConfigPageRequest reqDTO);

    ModelTrainConfigResponse create(ModelTrainConfigCreateRequest reqDTO);

    ModelTrainConfigResponse update(ModelTrainConfigUpdateRequest reqDTO);

    void delete(ModelTrainConfigDeleteRequest reqDTO);
}
