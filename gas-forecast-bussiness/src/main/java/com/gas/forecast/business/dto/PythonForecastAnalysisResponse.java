package com.gas.forecast.business.dto;

import java.util.List;

/** Python 解读模型返回的持久化内容。 */
public record PythonForecastAnalysisResponse(String analysis, List<SuggestedQuestion> questions) {

    /** 一个经过业务规则筛选并由AI优化表达的推荐追问。 */
    public record SuggestedQuestion(String code, String category, String text) {
    }
}
