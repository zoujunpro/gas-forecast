package com.gas.forecast.business.service;

import com.gas.forecast.business.dto.request.AutoTrainRequest;
import com.gas.forecast.business.dto.response.AutoTrainResponse;

public interface AutoTrainService {
    AutoTrainResponse execute(AutoTrainRequest request);
}
