package com.gas.forecast.business.service;

import com.gas.forecast.business.dto.request.ModelTrainFeatureDataCreateRequest;
import com.gas.forecast.business.dto.request.ModelTrainFeatureDataDeleteRequest;
import com.gas.forecast.business.dto.request.ModelTrainFeatureDataPageRequest;
import com.gas.forecast.business.dto.request.ModelTrainFeatureDataUpdateRequest;
import com.gas.forecast.business.dto.response.ModelTrainFeatureDataResponse;
import com.gas.forecast.common.core.PageInfoDTO;

public interface ModelTrainFeatureDataService {

    PageInfoDTO<ModelTrainFeatureDataResponse> listPage(ModelTrainFeatureDataPageRequest reqDTO);

    ModelTrainFeatureDataResponse create(ModelTrainFeatureDataCreateRequest reqDTO);

    ModelTrainFeatureDataResponse update(ModelTrainFeatureDataUpdateRequest reqDTO);

    void delete(ModelTrainFeatureDataDeleteRequest reqDTO);
}
