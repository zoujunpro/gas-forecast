package com.gas.forecast.business.ai.model;

/** 大模型可选择的业务工具。工具名比抽象意图更接近真实接口职责。 */
public enum AiToolName {
    QUERY_CUSTOMERS, QUERY_MODEL_CAPABILITY, START_TRAINING, QUERY_TRAINING, START_FORECAST, QUERY_FORECAST_RESULT, ANALYZE_CURRENT_RESULT, NONE
}
