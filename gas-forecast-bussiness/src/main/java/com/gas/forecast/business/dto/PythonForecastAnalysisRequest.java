package com.gas.forecast.business.dto;

import com.fasterxml.jackson.databind.JsonNode;

/** Java 预测调度器传给 Python 解读模型的可信结果快照。 */
public record PythonForecastAnalysisRequest(String forecastNo, JsonNode context) { }
