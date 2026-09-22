package com.gas.forecast.business.service;

import com.gas.forecast.business.dto.req.ModelTrainExecuteReqDTO;
import com.gas.forecast.business.dto.resp.ModelTrainExecuteRespDTO;
import com.fasterxml.jackson.databind.JsonNode;

public interface ModelTrainExecutionService {

    ModelTrainExecuteRespDTO execute(ModelTrainExecuteReqDTO reqDTO);

    JsonNode validateTrainingData(ModelTrainExecuteReqDTO reqDTO);

    ModelTrainExecuteRespDTO updateTrainResult(JsonNode reqDTO);

    JsonNode getTrainResult(JsonNode reqDTO);
}
