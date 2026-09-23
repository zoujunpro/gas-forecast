package com.gas.forecast.business.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.gas.forecast.business.dto.request.ModelTrainExecuteRequest;
import com.gas.forecast.business.dto.request.ModelTrainResultRequest;
import com.gas.forecast.business.dto.response.ModelTrainAgentResponse;
import com.gas.forecast.business.dto.response.ModelTrainExecuteResponse;

public interface ModelTrainExecutionService {

    ModelTrainExecuteResponse execute(ModelTrainExecuteRequest reqDTO);

    JsonNode validateTrainingData(ModelTrainExecuteRequest reqDTO);

    ModelTrainExecuteResponse updateTrainResult(ModelTrainAgentResponse reqDTO);

    JsonNode getTrainResult(ModelTrainResultRequest reqDTO);
}
