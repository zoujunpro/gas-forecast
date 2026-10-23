package com.gas.forecast.business.service;

import com.gas.forecast.business.enums.ExecutionStepCode;
import com.gas.forecast.dao.domain.ExecutionTaskDetailTb;

/** 将真实训练、预测批次的执行结果写入任务明细的六个业务步骤。 */
public interface ExecutionStepRecorder {
    void initialize(ExecutionTaskDetailTb taskDetail);
    void startByTrainBatch(String trainBatchNo, ExecutionStepCode stepCode, String inputJson);
    void successByTrainBatch(String trainBatchNo, ExecutionStepCode stepCode, String outputJson, String metricsJson);
    /** 写入卡片摘要，并保存展开时使用的结构化详情。 */
    void successWithDetailsByTrainBatch(String trainBatchNo, ExecutionStepCode stepCode, String summary, String detailJson);
    void failByTrainBatch(String trainBatchNo, ExecutionStepCode stepCode, String errorCode, Throwable error);
    void startByForecastBatch(String forecastBatchNo, ExecutionStepCode stepCode, String inputJson);
    void successByForecastBatch(String forecastBatchNo, ExecutionStepCode stepCode, String outputJson, String metricsJson);
    void failByForecastBatch(String forecastBatchNo, ExecutionStepCode stepCode, String errorCode, Throwable error);
    void skipByForecastBatch(String forecastBatchNo, ExecutionStepCode stepCode, String message);
}
