package com.gas.forecast.business.service;

import com.gas.forecast.business.dto.req.ModelTrainFeatureDataCreateReqDTO;
import com.gas.forecast.business.dto.req.ModelTrainFeatureDataDeleteReqDTO;
import com.gas.forecast.business.dto.req.ModelTrainFeatureDataPageReqDTO;
import com.gas.forecast.business.dto.req.ModelTrainFeatureDataUpdateReqDTO;
import com.gas.forecast.business.dto.resp.ModelTrainFeatureDataRespDTO;
import com.gas.forecast.common.core.PageInfoDTO;

public interface ModelTrainFeatureDataService {

    PageInfoDTO<ModelTrainFeatureDataRespDTO> listPage(ModelTrainFeatureDataPageReqDTO reqDTO);

    ModelTrainFeatureDataRespDTO create(ModelTrainFeatureDataCreateReqDTO reqDTO);

    ModelTrainFeatureDataRespDTO update(ModelTrainFeatureDataUpdateReqDTO reqDTO);

    void delete(ModelTrainFeatureDataDeleteReqDTO reqDTO);
}
