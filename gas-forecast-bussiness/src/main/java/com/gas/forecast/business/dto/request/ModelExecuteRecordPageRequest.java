package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.Min;
import lombok.Data;

/** 新统一预测执行记录分页查询条件。 */
@Data
public class ModelExecuteRecordPageRequest {
    @Min(1) private Integer page = 1;
    @Min(1) private Integer size = 20;
    private String forecastNo;
    private String agentCode;
    private String status;
    private String regionCode;
    private String industryCode;
    private String customerCode;
}
