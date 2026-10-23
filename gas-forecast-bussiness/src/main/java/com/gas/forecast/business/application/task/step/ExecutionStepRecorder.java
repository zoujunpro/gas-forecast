package com.gas.forecast.business.application.task.step;

import com.gas.forecast.business.enums.ExecutionStepCodeEnum;
import com.gas.forecast.dao.domain.ExecutionTaskDetailTb;

/** 将统一预测执行结果写入任务明细的业务步骤。 */
public interface ExecutionStepRecorder {
    void initialize(ExecutionTaskDetailTb taskDetail);
    void bindForecastNo(String taskDetailNo, String forecastNo);
    void startByForecastNo(String forecastNo, ExecutionStepCodeEnum stepCode, String inputJson);
    void successByForecastNo(String forecastNo, ExecutionStepCodeEnum stepCode, String outputJson, String metricsJson);
    void failByForecastNo(String forecastNo, ExecutionStepCodeEnum stepCode, String errorCode, Throwable error);
}
