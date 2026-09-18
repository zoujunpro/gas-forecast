package com.gas.forecast.business.service;

import com.gas.forecast.business.dto.req.ModelTrainExecuteReqDTO;
import com.gas.forecast.business.dto.resp.ModelTrainExecuteRespDTO;

public interface ModelTrainExecutionService {

    ModelTrainExecuteRespDTO execute(ModelTrainExecuteReqDTO reqDTO);
}
