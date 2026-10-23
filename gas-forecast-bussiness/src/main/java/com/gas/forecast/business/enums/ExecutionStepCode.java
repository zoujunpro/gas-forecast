package com.gas.forecast.business.enums;

import lombok.Getter;

/** 任务明细面向用户展示的六个固定业务步骤。 */
@Getter
public enum ExecutionStepCode {
    SALES_DATA_PREPARE(1, "销量数据准备"),
    FACTOR_DATA_PREPARE(2, "基础特征准备"),
    DYNAMIC_INDICATOR(3, "动态指标识别与获取"),
    MODEL_SELECT(4, "训练、评估并筛选最佳模型"),
    FORECAST(5, "未来燃气销量预测"),
    FORECAST_ANALYSIS(6, "预测结果智能分析");

    private final int order;
    private final String name;

    ExecutionStepCode(int order, String name) {
        this.order = order;
        this.name = name;
    }
}
