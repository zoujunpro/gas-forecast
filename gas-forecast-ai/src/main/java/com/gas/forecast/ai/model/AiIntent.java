package com.gas.forecast.ai.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AiIntent {
    private AiToolName tool = AiToolName.NONE;
    private AiIntentType intent = AiIntentType.UNKNOWN;
    private AiSpeechAct speechAct = AiSpeechAct.UNKNOWN;
    private Double confidence = 0D;
    /** 大模型认为语义仍有歧义时可建议确认，最终是否执行由 Java 决策。 */
    private Boolean requiresConfirmation = false;
    /** 是否要求对地区或行业范围内的全部客户批量执行。 */
    private Boolean bulkScope = false;
    private String agentCode = "short-term";
    private String regionCode;
    private String regionName;
    private String industryCode;
    private String industryName;
    private String customerCode;
    private String customerName;
    private String trainBatchNo;
    private String forecastBatchNo;
    private String startDate;
    private String endDate;
    private Integer forecastHorizon;
    private String timeGranularity;
    /** 查询结果排序字段，目前支持 DATA_COUNT、NAME。 */
    private String sortBy;
    /** 查询结果排序方向：ASC、DESC。 */
    private String sortDirection;
    /** 用户明确指定的返回数量。 */
    private Integer limit;
    private String clarificationQuestion;
    private List<EntityCandidate> candidates = new ArrayList<>();

    @Data
    public static class EntityCandidate {
        private String type;
        private String code;
        private String name;
        private String description;
    }
}
