package com.gas.forecast.business.service;

import com.gas.forecast.business.dto.request.ExecutionTarget;
import java.util.List;

/** 面向REST、MCP和旧AI入口复用的训练应用编排服务。 */
public interface TrainingApplicationService {
    Object start(TrainingCommand command);

    Object status(String trainBatchNo);

    Object result(String trainBatchNo);

    /** 训练数据范围。训练与预测前训练共用。 */
    record TrainingDataOptions(
            /** 训练数据范围模式：ALL、RECENT、RANGE。 */
            String trainMode,
            /** RECENT 模式下取最近的训练周期数。 */
            Integer trainingPeriods,
            /** RANGE 模式下的训练开始日期，格式 yyyy-MM-dd。 */
            String trainingStartDate,
            /** RANGE 模式下的训练结束日期，格式 yyyy-MM-dd。 */
            String trainingEndDate) {
    }

    /** 直接训练专用参数。 */
    record TrainingOptions(
            /** 训练数据范围。 */
            TrainingDataOptions dataOptions,
            /** 是否忽略已有成功模型并重新训练。 */
            boolean forceRetrain) {
    }

    /** 调用控制参数。 */
    record ExecutionOptions(
            /** 批量范围、自动训练或强制重训已获得用户确认。 */
            boolean confirmed) {
    }

    /** 发起训练所需的完整业务命令。 */
    record TrainingCommand(
            /** 所属会话编号。 */
            String conversationNo,
            /** 执行训练的智能体编码。 */
            String agentCode,
            /** 本次训练的最小执行维度：CUSTOMER、REGION、INDUSTRY。 */
            String dimensionType,
            /** 实体解析后的目标组合；不同组合独立执行。 */
            List<ExecutionTarget> targets,
            /** 训练数据范围和重训策略。 */
            TrainingOptions training,
            /** 用户确认和幂等控制信息。 */
            ExecutionOptions execution) {
    }
}
