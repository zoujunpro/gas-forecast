package com.gas.forecast.business.service;

import java.util.List;

/** 面向REST、MCP和旧AI入口复用的训练应用编排服务。 */
public interface TrainingApplicationService {
    Object start(TrainingCommand command);

    Object status(String trainBatchNo);

    Object result(String trainBatchNo);

    record TrainingCommand(String agentCode, List<String> customerCodes, String regionCode, String industryCode,
            List<String> regionCodes, List<String> industryCodes, boolean bulk, String trainMode, Integer trainingPeriods,
            String trainingStartDate, String trainingEndDate, boolean forceRetrain, boolean confirmed, String idempotencyKey) {
    }
}
