package com.gas.forecast.business.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 统一训练入口返回的解析结果和异步批次。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AutoTrainResponse {
    private String agentCode;
    private String triggerSource;
    private Long modelId;
    private String modelCode;
    private Long trainConfigId;
    private String regionCode;
    private String industryCode;
    private String customerCode;
    private String trainMode;
    private String trainStartDate;
    private String trainEndDate;
    private Integer recentPeriods;
    private String trainBatchNo;
    private String status;
}
