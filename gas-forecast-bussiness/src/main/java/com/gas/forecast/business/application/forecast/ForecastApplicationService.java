package com.gas.forecast.business.application.forecast;

import com.gas.forecast.business.dto.request.ExecutionTarget;
import com.gas.forecast.business.enums.ExecutionDimensionTypeEnum;
import java.util.List;

/** 面向REST、MCP和旧AI入口复用的预测应用编排服务。 */
public interface ForecastApplicationService {
    Object start(ForecastCommand command);

    /** 预测时间参数。 */
    record ForecastOptions(
            /** 预测起始日期；为空时按预测单位补齐当前周期。 */
            String forecastStartDate,
            /** 预测期数。 */
            Integer forecastHorizon,
            /** 预测单位：day、tenday、month。 */
            String forecastUnit) {
    }

    /** 训练与回测使用的历史数据范围；起止日期均为空时使用全部可用历史数据。 */
    record TrainingDataRange(String historyStartDate, String historyEndDate) {
    }

    /** 发起预测所需的完整业务命令。 */
    record ForecastCommand(
            /** 所属会话编号。 */
            String conversationNo,
            /** 执行预测的智能体编码。 */
            String agentCode,
            /** 本次预测最小执行维度：CUSTOMER、REGION、INDUSTRY。 */
            ExecutionDimensionTypeEnum dimensionType,
            /** 实体解析后的目标组合；不同组合独立执行。 */
            List<ExecutionTarget> targets,
            /** 预测时间参数。 */
            ForecastOptions forecast,
            /** 训练和回测数据范围。 */
            TrainingDataRange trainingDataRange,
            /** AI 会话中用户确认执行的状态。 */
            ExecutionOptions execution
) {

        public String dimensionTypeCode() { return dimensionType == null ? null : dimensionType.getCode(); }
        public boolean confirmed() { return execution != null && execution.confirmed(); }
    }

    /** AI 会话中用户确认执行的状态。 */
    record ExecutionOptions(boolean confirmed) { }
}
