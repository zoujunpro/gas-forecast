package com.gas.forecast.business.service;

import com.gas.forecast.dao.domain.ModelTrainConfigTb;
import com.gas.forecast.dao.domain.ModelTrainRecordTb;

public interface ModelTrainQueueService {
    ModelTrainRecordTb enqueue(ModelTrainConfigTb config, String triggerSource, boolean forceTrain, String retryBatchNo);
}
