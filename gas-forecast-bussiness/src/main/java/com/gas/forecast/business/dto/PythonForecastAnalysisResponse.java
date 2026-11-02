package com.gas.forecast.business.dto;

import java.util.List;

/** Python 解读模型返回的持久化内容。 */
public record PythonForecastAnalysisResponse(String analysis, List<String> questions) { }
