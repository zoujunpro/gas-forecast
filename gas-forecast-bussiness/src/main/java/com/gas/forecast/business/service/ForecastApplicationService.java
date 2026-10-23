package com.gas.forecast.business.service;

import com.gas.forecast.business.dto.request.ExecutionTarget;
import com.gas.forecast.business.dto.request.ExecutionTarget;
import com.gas.forecast.business.service.TrainingApplicationService.ExecutionOptions;
import com.gas.forecast.business.service.TrainingApplicationService.TrainingDataOptions;
import java.util.List;

/** 面向REST、MCP和旧AI入口复用的预测应用编排服务。 */
public interface ForecastApplicationService {
    Object start(ForecastCommand command);

    Object status(String forecastBatchNo);

    Object result(String forecastBatchNo, boolean includePoints);

    /** 预测时间范围和频率参数。 */
    record ForecastOptions(
            String forecastStartDate,
            String forecastEndDate,
            Integer forecastHorizon,
            String forecastFrequency) {
    }

    /** 发起预测所需的完整业务命令。 */
    record ForecastCommand(
            /** 所属会话编号。 */
            String conversationNo,
            /** 执行预测的智能体编码。 */
            String agentCode,
            /** 本次预测最小执行维度：CUSTOMER、REGION、INDUSTRY。 */
            String dimensionType,
            /** 实体解析后的目标组合；不同组合独立执行。 */
            List<ExecutionTarget> targets,
            /** 预测时间范围和频率。 */
            ForecastOptions forecast,
            /** 预测前如需训练时使用的训练数据范围。 */
            TrainingDataOptions trainingDataOptions,
            /** 自动训练授权及幂等控制。 */
            ExecutionOptions execution,
            /** 用户训练偏好：FORCE_RETRAIN、USE_EXISTING_ONLY、UNSPECIFIED、AUTO。 */
            String trainingPreference) {

        /* 旧预测编排的过渡访问方法；后续删除 execution_batch_tb 时整体下沉至实现类。 */
        public ExecutionTarget target() { return targets == null || targets.isEmpty() ? new ExecutionTarget(List.of(), List.of(), List.of()) : targets.get(0); }
        public List<String> customerCodes() { return target().customerCodes(); }
        public List<String> regionCodes() { return target().regionCodes(); }
        public List<String> industryCodes() { return target().industryCodes(); }
        public String regionCode() { return first(regionCodes()); }
        public String industryCode() { return first(industryCodes()); }
        public String forecastStartDate() { return forecast == null ? null : forecast.forecastStartDate(); }
        public String forecastEndDate() { return forecast == null ? null : forecast.forecastEndDate(); }
        public Integer forecastHorizon() { return forecast == null ? null : forecast.forecastHorizon(); }
        public String forecastFrequency() { return forecast == null ? null : forecast.forecastFrequency(); }
        public String trainMode() { return trainingDataOptions == null ? null : trainingDataOptions.trainMode(); }
        public Integer trainingPeriods() { return trainingDataOptions == null ? null : trainingDataOptions.trainingPeriods(); }
        public String trainingStartDate() { return trainingDataOptions == null ? null : trainingDataOptions.trainingStartDate(); }
        public String trainingEndDate() { return trainingDataOptions == null ? null : trainingDataOptions.trainingEndDate(); }
        public boolean forceRetrain() { return "FORCE_RETRAIN".equalsIgnoreCase(trainingPreference); }
        public boolean autoTrain() { return "AUTO".equalsIgnoreCase(trainingPreference) || "FORCE_RETRAIN".equalsIgnoreCase(trainingPreference); }
        public boolean confirmed() { return execution != null && execution.confirmed(); }
        /** 旧批次兼容路径保留；统一任务不再使用请求号。 */
        public String requestId() { return null; }
        private static String first(List<String> values) { return values == null || values.isEmpty() ? null : values.get(0); }

    }
}
