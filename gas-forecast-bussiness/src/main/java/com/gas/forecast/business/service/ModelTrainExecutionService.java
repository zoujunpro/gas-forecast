package com.gas.forecast.business.service;

import com.gas.forecast.business.dto.request.ModelTrainExecuteRequest;
import com.gas.forecast.business.dto.request.ModelTrainResultRequest;
import com.gas.forecast.business.dto.response.ModelTrainAgentResponse;
import com.gas.forecast.business.dto.response.ModelTrainExecuteResponse;
import com.gas.forecast.business.dto.response.ModelTrainResultResponse;
import com.gas.forecast.business.dto.response.ModelTrainingValidationResponse;

public interface ModelTrainExecutionService {

    ModelTrainExecuteResponse execute(ModelTrainExecuteRequest reqDTO);

    ModelTrainingValidationResponse validateTrainingData(ModelTrainExecuteRequest reqDTO);

    ModelTrainExecuteResponse updateTrainResult(ModelTrainAgentResponse reqDTO);

    ModelTrainResultResponse getTrainResult(ModelTrainResultRequest reqDTO);
}
