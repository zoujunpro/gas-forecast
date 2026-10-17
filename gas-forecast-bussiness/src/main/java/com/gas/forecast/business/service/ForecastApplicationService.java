package com.gas.forecast.business.service;

import java.util.List;

/** 面向REST、MCP和旧AI入口复用的预测应用编排服务。 */
public interface ForecastApplicationService {
    Object start(ForecastCommand command);

    Object status(String forecastBatchNo);

    Object result(String forecastBatchNo, boolean includePoints);

    record ForecastCommand(String conversationNo, String agentCode, List<String> customerCodes, String regionCode, String industryCode, boolean bulk, String forecastStartDate, String forecastEndDate,
            Integer forecastHorizon, String forecastFrequency, String trainMode, Integer trainingPeriods, String trainingStartDate, String trainingEndDate, boolean forceRetrain,
            boolean autoTrain, String trainingPreference, boolean confirmed, String idempotencyKey) {
    }
}
