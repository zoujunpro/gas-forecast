package com.gas.forecast.business.service;

import com.gas.forecast.business.dto.request.ModelTrainExecuteRequest;
import com.gas.forecast.business.dto.request.ModelTrainResultRequest;
import com.gas.forecast.business.component.dto.ModelTrainApiResponse;
import com.gas.forecast.business.component.dto.ModelTrainingValidationApiResponse;
import com.gas.forecast.business.dto.response.ModelTrainExecuteResponse;
import com.gas.forecast.business.dto.response.ModelTrainResultResponse;
import com.gas.forecast.business.dto.response.ModelTrainExecutionStepResponse;
import java.util.List;

public interface ModelTrainExecutionService {

    ModelTrainExecuteResponse execute(ModelTrainExecuteRequest reqDTO);

    ModelTrainingValidationApiResponse validateTrainingData(ModelTrainExecuteRequest reqDTO);

    ModelTrainExecuteResponse updateTrainResult(ModelTrainApiResponse reqDTO);

    ModelTrainResultResponse getTrainResult(ModelTrainResultRequest reqDTO);

    List<ModelTrainExecutionStepResponse> getExecutionSteps(String batchNo);
}
